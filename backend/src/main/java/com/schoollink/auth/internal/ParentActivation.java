package com.schoollink.auth.internal;

import com.schoollink.notification.ActivationSms;
import com.schoollink.people.Guardians;
import com.schoollink.people.Guardians.Contact;
import com.schoollink.people.Invitations;
import com.schoollink.people.Invitations.PendingInvitation;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * UC-P1: a parent activates their account with the invitation code from the SMS (D13) and their
 * phone number, then with a 6-digit one-time code sent to that phone. The account is made in the
 * guardian's school (D15). NFR-7:
 * <ul>
 * <li>a wrong code, a used, revoked or expired one, and a phone that is not the guardian's all get
 * the same answer, so nothing tells which codes exist;</li>
 * <li>the one-time code is stored as a BCrypt hash, lasts 10 minutes and allows 5 wrong tries, then
 * 429 (counted in the database, so a restart does not reset it); asking again replaces it;</li>
 * <li>at most 3 one-time codes per guardian per hour (SMS cost, and no flooding a phone), and per
 * IP address 10 activation and 30 verification requests per 15 minutes.</li>
 * </ul>
 * NFR-9: the phone travels in the request body only and is never logged; the codes are never
 * logged or audited.
 */
@Service
class ParentActivation {

	static final Duration OTP_VALIDITY = Duration.ofMinutes(10);

	static final int MAX_FAILED_ATTEMPTS = 5;

	static final RateLimitPolicy ACTIVATE_PER_IP = new RateLimitPolicy("activate-ip", 10, Duration.ofMinutes(15));

	static final RateLimitPolicy OTP_PER_GUARDIAN = new RateLimitPolicy("activate-otp-guardian", 3, Duration.ofHours(1));

	static final RateLimitPolicy VERIFY_PER_IP = new RateLimitPolicy("verify-ip", 30, Duration.ofMinutes(15));

	/** {@code expiresAt}: when the one-time code stops working. */
	record Started(UUID activationId, Instant expiresAt) {
	}

	private record Pending(UUID schoolId, UUID invitationId, String otpHash, Instant expiresAt, int failedAttempts,
			boolean verified) {
	}

	private final Invitations invitations;

	private final Guardians guardians;

	private final ParentAccounts parentAccounts;

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

	ParentActivation(Invitations invitations, Guardians guardians, ParentAccounts parentAccounts,
			UserAccounts accounts, ActivationSms sms, PasswordEncoder hashes, AccessTokens accessTokens,
			RefreshTokens refreshTokens, RateLimiter limiter, NamedParameterJdbcTemplate jdbc,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.invitations = invitations;
		this.guardians = guardians;
		this.parentAccounts = parentAccounts;
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

	/** Step 1: checks the invitation code and the phone, and sends the one-time code by SMS. */
	Started start(String code, String phone, String clientIp) {
		checkRate(limiter.tryConsume(ACTIVATE_PER_IP, clientIp));
		Optional<String> normalized = PhoneNumbers.normalize(phone);
		Optional<PendingInvitation> invitation = invitations.find(code);
		if (normalized.isEmpty() || invitation.isEmpty()) {
			throw invalidActivation();
		}
		PendingInvitation pending = invitation.get();
		Contact contact = guardians.contact(pending.schoolId(), pending.guardianId());
		if (!normalized.get().equals(contact.phone())) {
			throw invalidActivation();
		}
		checkRate(limiter.tryConsume(OTP_PER_GUARDIAN, pending.guardianId().toString()));

		UUID id = UUID.randomUUID();
		String otp = "%06d".formatted(random.nextInt(1_000_000));
		Instant now = clock.instant();
		Instant expiresAt = now.plus(OTP_VALIDITY);
		transactions.executeWithoutResult(tx -> {
			MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("school", pending.schoolId())
				.addValue("invitation", pending.invitationId())
				.addValue("guardian", pending.guardianId())
				.addValue("hash", hashes.encode(otp))
				.addValue("now", Timestamp.from(now))
				.addValue("expires", Timestamp.from(expiresAt));
			// a new code replaces the previous ones
			jdbc.update("DELETE FROM parent_activation WHERE invitation_id = :invitation AND verified_at IS NULL", p);
			jdbc.update("""
					INSERT INTO parent_activation (id, school_id, invitation_id, guardian_id, otp_hash, created_at, expires_at)
					VALUES (:id, :school, :invitation, :guardian, :hash, :now, :expires)""", p);
		});
		String language = guardians.language(pending.schoolId(), pending.guardianId());
		if (!sms.send(pending.schoolId(), id, contact.phone(), language, otp, OTP_VALIDITY)) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "auth.otp_not_sent", null);
		}
		return new Started(id, expiresAt);
	}

	/** Step 2: checks the one-time code, makes the account and signs the parent in. */
	TokenResponse verify(UUID activationId, String otp, String clientIp) {
		checkRate(limiter.tryConsume(VERIFY_PER_IP, clientIp));
		MapSqlParameterSource p = new MapSqlParameterSource("id", activationId);
		Optional<TokenResponse> tokens = transactions.execute(tx -> {
			// locked: concurrent tries on one activation are counted one after the other
			Pending pending = jdbc.query("""
					SELECT school_id, invitation_id, otp_hash, expires_at, failed_attempts, verified_at IS NOT NULL AS verified
					FROM parent_activation WHERE id = :id FOR UPDATE""", p,
					(rs, n) -> new Pending(rs.getObject("school_id", UUID.class), rs.getObject("invitation_id", UUID.class),
							rs.getString("otp_hash"), rs.getTimestamp("expires_at").toInstant(),
							rs.getInt("failed_attempts"), rs.getBoolean("verified")))
				.stream()
				.findFirst()
				.orElseThrow(ParentActivation::invalidOtp);
			Instant now = clock.instant();
			if (pending.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
				// NFR-7: the parent asks for a new code (step 1), itself limited per guardian
				Duration left = Duration.between(now, pending.expiresAt());
				throw new RateLimitedException(left.isNegative() ? Duration.ofMinutes(1) : left);
			}
			if (pending.verified() || !now.isBefore(pending.expiresAt())) {
				throw invalidOtp();
			}
			if (otp == null || !otp.strip().matches("[0-9]{6}") || !hashes.matches(otp.strip(), pending.otpHash())) {
				// committed with this transaction, which ends normally
				jdbc.update("UPDATE parent_activation SET failed_attempts = failed_attempts + 1 WHERE id = :id", p);
				return Optional.<TokenResponse>empty();
			}
			// the invitation is used in this transaction: of two verifications, only one makes the account
			UUID userId = parentAccounts.activate(pending.schoolId(), pending.invitationId())
				.orElseThrow(ParentActivation::invalidActivation);
			jdbc.update("UPDATE parent_activation SET verified_at = :now WHERE id = :id",
					p.addValue("now", Timestamp.from(now)));
			// defence in depth: tokens only ever go to an active account (D18)
			UserAccount account = accounts.findById(userId)
				.filter(UserAccount::isActive)
				.orElseThrow(ParentActivation::invalidActivation);
			return Optional.of(new TokenResponse("Bearer", accessTokens.issue(account), accessTokens.ttlSeconds(),
					refreshTokens.create(userId, UUID.randomUUID()), UserInfo.of(account)));
		});
		return tokens.orElseThrow(ParentActivation::invalidOtp);
	}

	private static void checkRate(Duration wait) {
		if (!wait.isZero()) {
			throw new RateLimitedException(wait);
		}
	}

	private static ApiException invalidActivation() {
		return ApiException.invalid("auth.invalid_activation", null);
	}

	private static ApiException invalidOtp() {
		return ApiException.invalid("auth.invalid_otp", "otp");
	}

}
