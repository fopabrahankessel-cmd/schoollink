package com.schoollink.auth.internal;

import com.schoollink.auth.internal.ParentActivation.Started;
import com.schoollink.notification.ActivationSms;
import com.schoollink.people.UserAccount;
import com.schoollink.people.UserAccounts;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.error.RateLimitedException;
import com.schoollink.shared.phone.PhoneNumbers;
import com.schoollink.shared.ratelimit.RateLimitPolicy;
import com.schoollink.shared.ratelimit.RateLimiter;
import java.security.SecureRandom;
import java.sql.Timestamp;
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
 * D24 (UC-P1 follow-up): a parent who already activated signs in again, after signing out or on a
 * new phone, with their school, their phone and a 6-digit one-time code sent to that phone.
 * Parents have no password; staff who are also parents (D21) sign in with theirs. NFR-7:
 * <ul>
 * <li>step 1 answers the same whether or not the phone has a parent account, so nothing tells
 * which numbers are registered: no SMS is sent then, and step 2 can never succeed;</li>
 * <li>the one-time code is stored as a BCrypt hash, lasts 10 minutes and allows 5 wrong tries, then
 * 429; asking again replaces it;</li>
 * <li>at most 3 one-time codes per account per hour, counted in the database;</li>
 * <li>per IP address, 10 sign-in and 30 verification requests per 15 minutes.</li>
 * </ul>
 * NFR-9: the phone travels in the request body only and is never logged; the codes are never
 * logged.
 */
@Service
class ParentSignIn {

	static final RateLimitPolicy START_PER_IP = new RateLimitPolicy("parent-sign-in-ip", 10, Duration.ofMinutes(15));

	static final RateLimitPolicy VERIFY_PER_IP = new RateLimitPolicy("parent-sign-in-verify-ip", 30,
			Duration.ofMinutes(15));

	private record Pending(UUID userId, String otpHash, Instant expiresAt, int failedAttempts, boolean verified,
			boolean superseded) {
	}

	private final UserAccounts accounts;

	private final ActivationSms sms;

	private final PasswordEncoder hashes;

	private final AccessTokens accessTokens;

	private final RefreshTokens refreshTokens;

	private final RateLimiter limiter;

	private final NamedParameterJdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	ParentSignIn(UserAccounts accounts, ActivationSms sms, PasswordEncoder hashes, AccessTokens accessTokens,
			RefreshTokens refreshTokens, RateLimiter limiter, NamedParameterJdbcTemplate jdbc,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.accounts = accounts;
		this.sms = sms;
		this.hashes = hashes;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.limiter = limiter;
		this.jdbc = jdbc;
		this.transactions = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** Step 1: sends a one-time code to the phone of the school's parent account, if there is one. */
	Started start(UUID schoolId, String phone, String clientIp) {
		checkRate(limiter.tryConsume(START_PER_IP, clientIp));
		String otp = "%06d".formatted(random.nextInt(1_000_000));
		// hashed in both cases, so the answer takes about as long with or without an account
		String otpHash = hashes.encode(otp);
		UUID id = UUID.randomUUID();
		Instant now = clock.instant();
		Instant expiresAt = now.plus(ParentActivation.OTP_VALIDITY);
		Optional<UserAccount> parent = PhoneNumbers.normalize(phone)
			.flatMap(normalized -> accounts.findByLogin(normalized, schoolId).stream().filter(ParentSignIn::isParentOnly)
				.findFirst());
		if (parent.isEmpty()) {
			// NFR-7: the same answer as for a registered number; this id is never stored
			return new Started(id, expiresAt);
		}
		UserAccount account = parent.get();
		transactions.executeWithoutResult(tx -> {
			MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("school", account.schoolId())
				.addValue("user", account.id())
				.addValue("hash", otpHash)
				.addValue("now", Timestamp.from(now))
				.addValue("expires", Timestamp.from(expiresAt))
				.addValue("windowStart", Timestamp.from(now.minus(ParentActivation.CODES_WINDOW)));
			// one account's requests are counted one after the other
			jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:user AS text), 0))", p);
			List<Timestamp> recent = jdbc.queryForList("""
					SELECT created_at FROM parent_sign_in WHERE user_id = :user AND created_at > :windowStart
					ORDER BY created_at""", p, Timestamp.class);
			if (recent.size() >= ParentActivation.MAX_CODES) {
				throw new RateLimitedException(Duration.between(now,
						recent.getFirst().toInstant().plus(ParentActivation.CODES_WINDOW)));
			}
			jdbc.update("""
					INSERT INTO parent_sign_in (id, school_id, user_id, otp_hash, created_at, expires_at)
					VALUES (:id, :school, :user, :hash, :now, :expires)""", p);
		});
		if (!sms.send(account.schoolId(), id, PhoneNumbers.normalize(phone).orElseThrow(), account.language(), otp,
				ParentActivation.OTP_VALIDITY)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "auth.otp_not_sent", null);
		}
		return new Started(id, expiresAt);
	}

	/** Step 2: checks the one-time code and signs the parent in. */
	TokenResponse verify(UUID signInId, String otp, String clientIp) {
		checkRate(limiter.tryConsume(VERIFY_PER_IP, clientIp));
		MapSqlParameterSource p = new MapSqlParameterSource("id", signInId);
		Optional<TokenResponse> tokens = transactions.execute(tx -> {
			// locked: concurrent tries on one request are counted one after the other
			Pending pending = jdbc.query("""
					SELECT s.user_id, s.otp_hash, s.expires_at, s.failed_attempts, s.verified_at IS NOT NULL AS verified,
					       EXISTS (SELECT 1 FROM parent_sign_in n WHERE n.user_id = s.user_id AND n.seq > s.seq) AS superseded
					FROM parent_sign_in s WHERE s.id = :id FOR UPDATE OF s""", p,
					(rs, n) -> new Pending(rs.getObject("user_id", UUID.class), rs.getString("otp_hash"),
							rs.getTimestamp("expires_at").toInstant(), rs.getInt("failed_attempts"),
							rs.getBoolean("verified"), rs.getBoolean("superseded")))
				.stream()
				.findFirst()
				.orElseThrow(ParentSignIn::invalidOtp);
			Instant now = clock.instant();
			if (pending.failedAttempts() >= ParentActivation.MAX_FAILED_ATTEMPTS) {
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
			// D18: an account disabled since the code was sent gets nothing
			UserAccount account = accounts.findById(pending.userId())
				.filter(ParentSignIn::isParentOnly)
				.orElseThrow(ParentSignIn::invalidOtp);
			return Optional.of(new TokenResponse("Bearer", accessTokens.issue(account), accessTokens.ttlSeconds(),
					refreshTokens.create(account.id(), UUID.randomUUID()), UserInfo.of(account)));
		});
		return tokens.orElseThrow(ParentSignIn::invalidOtp);
	}

	/** An active account with the parent role only and no password; staff sign in with theirs (D21). */
	private static boolean isParentOnly(UserAccount account) {
		return account.isActive() && account.passwordHash() == null && account.roles().equals(Set.of("PARENT"));
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
