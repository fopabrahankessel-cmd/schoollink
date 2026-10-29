package com.schoollink.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Opaque 30-day refresh tokens, rotated on every use (NFR-7). Only the SHA-256 hash is stored.
 * Reusing an already-used token revokes its whole family.
 */
@Component
class RefreshTokens {

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
	 * that was already used revokes its family. Must run in a transaction that commits even when
	 * the result is empty, so the revocation sticks.
	 */
	Optional<Consumed> consume(String raw) {
		Instant now = clock.instant();
		var rows = jdbc.query("""
				SELECT id, user_id, family_id, expires_at, used_at, revoked_at
				FROM refresh_token WHERE token_hash = ? FOR UPDATE""",
				(rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
						rs.getObject("family_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
						rs.getTimestamp("used_at") != null, rs.getTimestamp("revoked_at") != null),
				hash(raw));
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		Row row = rows.getFirst();
		if (row.used()) {
			revokeFamily(row.familyId(), now);
			return Optional.empty();
		}
		if (row.revoked() || !row.expiresAt().isAfter(now)) {
			return Optional.empty();
		}
		jdbc.update("UPDATE refresh_token SET used_at = ? WHERE id = ?", Timestamp.from(now), row.id());
		return Optional.of(new Consumed(row.userId(), row.familyId()));
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

	private record Row(UUID id, UUID userId, UUID familyId, Instant expiresAt, boolean used, boolean revoked) {
	}

}
