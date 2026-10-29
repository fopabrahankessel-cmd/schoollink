package com.schoollink.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Opaque 30-day refresh tokens, rotated on every use (NFR-7). Only the SHA-256 hash is stored.
 * Reusing an already-used token revokes its whole family, except within {@link #REPLACED_GRACE}
 * of its use when its replacement was never used: the client then most likely lost the answer
 * (a page reloaded while the refresh was under way), not its token.
 */
@Component
class RefreshTokens {

	/**
	 * How long a replaced token may still be presented, while its replacement is unused. The
	 * replacement is then retired (marked used), so if it had in fact been stolen, presenting it
	 * later still revokes the family: the theft is detected one refresh later instead of at once.
	 */
	static final Duration REPLACED_GRACE = Duration.ofSeconds(30);

	private static final Logger log = LoggerFactory.getLogger(RefreshTokens.class);

	private static final SecureRandom random = new SecureRandom();

	private final JdbcTemplate jdbc;

	private final JwtProperties properties;

	private final Clock clock;

	RefreshTokens(JdbcTemplate jdbc, JwtProperties properties, Clock clock) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = clock;
	}

	/** Creates a token in {@code familyId} (a new family at login) and returns its raw value. */
	String create(UUID userId, UUID familyId) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant now = clock.instant();
		jdbc.update("""
				INSERT INTO refresh_token (user_id, family_id, token_hash, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?)""", userId, familyId, hash(raw), Timestamp.from(now),
				Timestamp.from(now.plus(properties.refreshTokenTtl())));
		return raw;
	}

	/**
	 * Consumes a token: returns its user and family if it is valid, and marks it used. A token
	 * that was already used revokes its family, unless it was replaced moments ago by a token that
	 * never reached the client (see {@link #REPLACED_GRACE}). Must run in a transaction that commits
	 * even when the result is empty, so the revocation sticks.
	 *
	 * <p>A family with any revoked member is over: a sign-out or password change running at the
	 * same time as a refresh can miss the token that refresh creates, but never the older ones.
	 */
	Optional<Consumed> consume(String raw) {
		Instant now = clock.instant();
		var rows = jdbc.query("""
				SELECT id, seq, user_id, family_id, expires_at, used_at, retired_at, revoked_at,
				       EXISTS (SELECT 1 FROM refresh_token f WHERE f.family_id = t.family_id AND f.revoked_at IS NOT NULL) AS family_revoked
				FROM refresh_token t WHERE token_hash = ? FOR UPDATE""",
				// After waiting for the lock, PostgreSQL rereads the locked row but not the subquery,
				// so the row's own revoked_at must be checked too.
				(rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getLong("seq"), rs.getObject("user_id", UUID.class),
						rs.getObject("family_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
						rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant(),
						rs.getTimestamp("retired_at") != null,
						rs.getTimestamp("revoked_at") != null || rs.getBoolean("family_revoked")),
				hash(raw));
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Row row = rows.getFirst();
		if (row.usedAt() != null) {
			// a retired token never reached its client: presenting it is always theft
			if (!row.retired() && !row.revoked() && row.usedAt().isAfter(now.minus(REPLACED_GRACE))
					&& retireReplacement(row, now)) {
				// never the token itself (NFR-9)
				log.info("Refresh grace used: user {}, family {}, lost replacement retired", row.userId(), row.familyId());
				return Optional.of(new Consumed(row.userId(), row.familyId()));
			}
			revokeFamily(row.familyId(), now);
			return Optional.empty();
		}
		if (row.revoked() || !row.expiresAt().isAfter(now)) {
			return Optional.empty();
		}
		jdbc.update("UPDATE refresh_token SET used_at = ? WHERE id = ?", Timestamp.from(now), row.id());
		return Optional.of(new Consumed(row.userId(), row.familyId()));
	}

	/**
	 * Retires the token that replaced {@code row}, if no one has used it yet. Tokens rotate one at
	 * a time, so the replacement is the family's only live token created after {@code row}; and no
	 * token created since may have been used, or the client did get the replacement. "After" is the
	 * creation order ({@code seq}), not the clock: two tokens may share a timestamp.
	 */
	private boolean retireReplacement(Row row, Instant now) {
		return jdbc.update("""
				UPDATE refresh_token SET used_at = ?, retired_at = ?
				WHERE family_id = ? AND seq > ? AND used_at IS NULL AND revoked_at IS NULL
				  AND NOT EXISTS (SELECT 1 FROM refresh_token u
				                  WHERE u.family_id = ? AND u.seq > ? AND u.used_at IS NOT NULL)""",
				Timestamp.from(now), Timestamp.from(now), row.familyId(), row.seq(), row.familyId(), row.seq()) == 1;
	}

	void revokeFamily(UUID familyId, Instant now) {
		jdbc.update("UPDATE refresh_token SET revoked_at = ? WHERE family_id = ? AND revoked_at IS NULL",
				Timestamp.from(now), familyId);
	}

	/** Sign-out: ends the session (the token family) this token belongs to, whatever its state. */
	void revokeFamilyOf(String raw) {
		jdbc.update("""
				UPDATE refresh_token SET revoked_at = ?
				WHERE revoked_at IS NULL AND family_id = (SELECT family_id FROM refresh_token WHERE token_hash = ?)""",
				Timestamp.from(clock.instant()), hash(raw));
	}

	/** Signs the user out of every device: after a password change or an admin reset (D18). */
	void revokeAll(UUID userId) {
		jdbc.update("UPDATE refresh_token SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
				Timestamp.from(clock.instant()), userId);
	}

	static String hash(String raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	record Consumed(UUID userId, UUID familyId) {
	}

	private record Row(UUID id, long seq, UUID userId, UUID familyId, Instant expiresAt, Instant usedAt, boolean retired,
			boolean revoked) {
	}

}
