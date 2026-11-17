package com.schoollink.auth.internal;

import com.schoollink.notification.ActivationSms;
import com.schoollink.people.Guardians;
import com.schoollink.people.ParentAccounts;
import com.schoollink.people.UserAccount;
import com.schoollink.people.UserAccounts;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.error.RateLimitedException;
import com.schoollink.shared.phone.PhoneNumbers;
import com.schoollink.shared.ratelimit.RateLimitPolicy;
import com.schoollink.shared.ratelimit.RateLimiter;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-P1, D24, D45: a parent signs in with their school, phone and password ({@code /auth/login}). A
 * 6-digit one-time code is sent to that phone only the first time and when the password is
 * forgotten (D45: SMS cost), and with it the parent chooses their password. The first time, the
 * phone is a guardian's in the school (D15) and the account is made when the code is right: the code
 * proves they hold the number the school gave. A code never opens an account with staff rights, so
 * staff who are also parents sign in with their own password and are linked then (D21). Choosing a
 * password ends the account's other sessions. NFR-7:
 * <ul>
 * <li>step 1 answers the same whether or not the phone is known, so nothing tells which numbers
 * are registered: no SMS is sent then, and step 2 can never succeed;</li>
 * <li>the one-time code is stored as a BCrypt hash, lasts 10 minutes and allows 5 wrong tries, then
 * 429; asking again replaces it;</li>
 * <li>at most 3 one-time codes per account or guardian per hour and 5 per day, counted in the
 * database, so nobody can flood a phone with SMS;</li>
 * <li>per IP address, 10 sign-in and 30 verification requests per 15 minutes.</li>
 * </ul>
 * NFR-9: the phone travels in the request body only and is never logged; the codes and passwords
 * are never logged.
 */
@Service
class ParentSignIn {

	static final Duration OTP_VALIDITY = Duration.ofMinutes(10);

	static final int MAX_FAILED_ATTEMPTS = 5;

	/** One-time codes per account or guardian per {@link #CODES_WINDOW}. */
	static final int MAX_CODES = 3;

	static final Duration CODES_WINDOW = Duration.ofHours(1);

	/** One-time codes per account or guardian per {@link #DAILY_WINDOW}. */
	static final int MAX_DAILY_CODES = 5;

	static final Duration DAILY_WINDOW = Duration.ofDays(1);

	static final RateLimitPolicy START_PER_IP = new RateLimitPolicy("parent-sign-in-ip", 10, Duration.ofMinutes(15));

	static final RateLimitPolicy VERIFY_PER_IP = new RateLimitPolicy("parent-sign-in-verify-ip", 30,
			Duration.ofMinutes(15));

	/** {@code expiresAt}: when the one-time code stops working. */
	record Started(UUID activationId, Instant expiresAt) {
	}

	/** Who the code is for: an account, or a guardian who has none yet. */
	private record Target(UUID schoolId, UUID userId, UUID guardianId, String language) {

		UUID key() {
			return userId != null ? userId : guardianId;
		}

	}

	private record Pending(UUID schoolId, UUID userId, UUID guardianId, String otpHash, Instant expiresAt,
			int failedAttempts, boolean verified, boolean superseded) {
	}

	private final UserAccounts accounts;

	private final Guardians guardians;

	private final ParentAccounts parentAccounts;

	private final ActivationSms sms;

	private final PasswordEncoder hashes;

	private final AccessTokens accessTokens;

	private final RefreshTokens refreshTokens;

	private final RateLimiter limiter;

	private final NamedParameterJdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	ParentSignIn(UserAccounts accounts, Guardians guardians, ParentAccounts parentAccounts, ActivationSms sms,
			PasswordEncoder hashes, AccessTokens accessTokens, RefreshTokens refreshTokens, RateLimiter limiter,
			NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
		this.accounts = accounts;
		this.guardians = guardians;
		this.parentAccounts = parentAccounts;
		this.sms = sms;
		this.hashes = hashes;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.limiter = limiter;
		this.jdbc = jdbc;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** Step 1: sends a one-time code to the phone of the school's parent or guardian, if there is one. */
	Started start(UUID schoolId, String phone, String clientIp) {
		checkRate(limiter.tryConsume(START_PER_IP, clientIp));
		String otp = "%06d".formatted(random.nextInt(1_000_000));
		// hashed in both cases, so the answer takes about as long with or without an account
		String otpHash = hashes.encode(otp);
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		Instant expiresAt = now.plus(OTP_VALIDITY);
		Optional<String> normalized = PhoneNumbers.normalize(phone);
		Optional<Target> found = normalized.flatMap(number -> target(schoolId, number));
		if (found.isEmpty()) {
			// NFR-7: the same answer as for a registered number; this id is never stored
			return new Started(id, expiresAt);
		}
		Target target = found.get();
		transactions.executeWithoutResult(tx -> {
			MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("school", target.schoolId())
				.addValue("user", target.userId(), Types.OTHER)
				.addValue("guardian", target.guardianId(), Types.OTHER)
				.addValue("key", target.key().toString())
				.addValue("hash", otpHash)
				.addValue("now", Timestamp.from(now))
				.addValue("expires", Timestamp.from(expiresAt))
				.addValue("windowStart", Timestamp.from(now.minus(DAILY_WINDOW)));
			// one person's requests are counted one after the other
			jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))", p);
			List<Timestamp> recent = jdbc.queryForList("""
					SELECT created_at FROM parent_sign_in
					WHERE (user_id = :user OR guardian_id = :guardian) AND created_at > :windowStart
					ORDER BY created_at""", p, Timestamp.class);
			if (recent.size() >= MAX_DAILY_CODES) {
				throw new RateLimitedException(Duration.between(now, recent.getFirst().toInstant().plus(DAILY_WINDOW)));
			}
			List<Timestamp> lastHour = recent.stream()
				.filter(t -> t.toInstant().isAfter(now.minus(CODES_WINDOW)))
				.toList();
			if (lastHour.size() >= MAX_CODES) {
				throw new RateLimitedException(Duration.between(now, lastHour.getFirst().toInstant().plus(CODES_WINDOW)));
			}
			jdbc.update("""
					INSERT INTO parent_sign_in (id, school_id, user_id, guardian_id, otp_hash, created_at, expires_at)
					VALUES (:id, :school, :user, :guardian, :hash, :now, :expires)""", p);
		});
		if (!sms.send(target.schoolId(), id, normalized.orElseThrow(), target.language(), otp, OTP_VALIDITY)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "auth.otp_not_sent", null);
		}
		return new Started(id, expiresAt);
	}

	/**
	 * The parent account of this phone, or its guardian when the phone has no account at all. A
	 * phone with another account (staff, D21, or disabled, D18) gets no code.
	 */
	private Optional<Target> target(UUID schoolId, String phone) {
		List<UserAccount> existing = accounts.findByLogin(phone, schoolId);
		if (!existing.isEmpty()) {
			return existing.stream()
				.filter(ParentSignIn::isParentOnly)
				.findFirst()
				.map(account -> new Target(account.schoolId(), account.id(), null, account.language()));
		}
		return guardians.withoutAccount(schoolId, phone)
			.map(guardian -> new Target(schoolId, null, guardian.guardianId(), guardian.language()));
	}

	/**
	 * Step 2: checks the one-time code, makes the account the first time, sets the password the
	 * parent chose and signs them in.
	 */
	TokenResponse verify(UUID signInId, String otp, String password, String clientIp) {
		checkRate(limiter.tryConsume(VERIFY_PER_IP, clientIp));
		// hashed before the row is locked: BCrypt is slow on purpose
		String passwordHash = hashes.encode(password);
		MapSqlParameterSource p = new MapSqlParameterSource("id", signInId);
		Optional<TokenResponse> tokens = transactions.execute(tx -> {
			// locked: concurrent tries on one request are counted one after the other
			Pending pending = jdbc.query("""
					SELECT s.school_id, s.user_id, s.guardian_id, s.otp_hash, s.expires_at, s.failed_attempts,
					       s.verified_at IS NOT NULL AS verified,
					       EXISTS (SELECT 1 FROM parent_sign_in n
					               WHERE (n.user_id = s.user_id OR n.guardian_id = s.guardian_id) AND n.seq > s.seq) AS superseded
					FROM parent_sign_in s WHERE s.id = :id FOR UPDATE OF s""", p,
					(rs, n) -> new Pending(rs.getObject("school_id", UUID.class), rs.getObject("user_id", UUID.class),
							rs.getObject("guardian_id", UUID.class), rs.getString("otp_hash"),
							rs.getTimestamp("expires_at").toInstant(), rs.getInt("failed_attempts"),
							rs.getBoolean("verified"), rs.getBoolean("superseded")))
				.stream()
				.findFirst()
				.orElseThrow(ParentSignIn::invalidOtp);
			Instant now = clock.instant();
			if (pending.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
				Duration left = Duration.between(now, pending.expiresAt());
				throw new RateLimitedException(left.isNegative() ? Duration.ofMinutes(1) : left);
			}
			if (pending.verified() || pending.superseded() || !now.isBefore(pending.expiresAt())) {
				throw invalidOtp();
			}
			if (otp == null || !otp.strip().matches("[0-9]{6}") || !hashes.matches(otp.strip(), pending.otpHash())) {
				// committed with this transaction, which ends normally
				jdbc.update("UPDATE parent_sign_in SET failed_attempts = failed_attempts + 1 WHERE id = :id", p);
				return Optional.<TokenResponse>empty();
			}
			jdbc.update("UPDATE parent_sign_in SET verified_at = :now WHERE id = :id",
					p.addValue("now", Timestamp.from(now)));
			UUID userId = pending.userId() != null ? pending.userId()
					: parentAccounts.activate(pending.schoolId(), pending.guardianId()).orElseThrow(ParentSignIn::invalidOtp);
			// D18, D21: an account disabled or given staff rights since the code was sent gets nothing
			UserAccount account = accounts.findById(userId)
				.filter(ParentSignIn::isParentOnly)
				.orElseThrow(ParentSignIn::invalidOtp);
			// D45: the parent's password from now on (audited, NFR-11); other sessions end
			accounts.changePassword(account.id(), passwordHash);
			refreshTokens.revokeAll(account.id());
			account = accounts.findById(account.id()).orElseThrow();
			return Optional.of(new TokenResponse("Bearer", accessTokens.issue(account), accessTokens.ttlSeconds(),
					refreshTokens.create(account.id(), UUID.randomUUID()), UserInfo.of(account)));
		});
		return tokens.orElseThrow(ParentSignIn::invalidOtp);
	}

	/** An active account with the parent role only; staff sign in with their password (D21). */
	private static boolean isParentOnly(UserAccount account) {
		return account.isActive() && account.roles().equals(Set.of("PARENT"));
	}

	private static void checkRate(Duration wait) {
		if (!wait.isZero()) {
			throw new RateLimitedException(wait);
		}
	}

	private static ApiException invalidOtp() {
		return ApiException.invalid("auth.invalid_otp", "otp");
	}

}
