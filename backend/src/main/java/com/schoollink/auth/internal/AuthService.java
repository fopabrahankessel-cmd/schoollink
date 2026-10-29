package com.schoollink.auth.internal;

import com.schoollink.people.UserAccount;
import com.schoollink.people.UserAccounts;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.error.RateLimitedException;
import com.schoollink.shared.phone.PhoneNumbers;
import com.schoollink.shared.ratelimit.RateLimitPolicy;
import com.schoollink.shared.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff login and token refresh (NFR-7, D15, D16). Parents sign in through activation (UC-P1, task 2.9). */
@Service
class AuthService {

	private final UserAccounts accounts;

	private final PasswordEncoder passwords;

	private final AccessTokens accessTokens;

	/**
	 * NFR-7: 5 failed attempts per login from one IP address, one more every 3 minutes; a success
	 * resets it. Keyed on login + IP so that someone who knows a teacher's phone cannot lock the
	 * teacher out everywhere by failing 5 times from their own device.
	 */
	static final RateLimitPolicy LOGIN_FAILURES_PER_SOURCE = new RateLimitPolicy("login-failures-source", 5,
			Duration.ofMinutes(15));

	/**
	 * NFR-7: 20 failed attempts per login per hour across all IP addresses, so spreading guesses
	 * over many addresses does not help. Looser than the per-source limit, so the owner is locked
	 * out only by a sustained, distributed attack.
	 */
	static final RateLimitPolicy LOGIN_FAILURES_PER_LOGIN = new RateLimitPolicy("login-failures-login", 20,
			Duration.ofHours(1));

	/**
	 * NFR-7: 30 login requests per minute per IP address, generous because a school's staff may
	 * share one public address.
	 */
	static final RateLimitPolicy LOGIN_PER_IP = new RateLimitPolicy("login-ip", 30, Duration.ofMinutes(1));

	/** NFR-7: 5 wrong current passwords per user, one more every 3 minutes. */
	static final RateLimitPolicy PASSWORD_CHANGE_FAILURES = new RateLimitPolicy("password-change-failures", 5,
			Duration.ofMinutes(15));

	private final RefreshTokens refreshTokens;

	private final RateLimiter limiter;

	/** Checked when no account matches, so a wrong login takes as long as a wrong password. */
	private final String dummyHash;

	AuthService(UserAccounts accounts, PasswordEncoder passwords, AccessTokens accessTokens,
			RefreshTokens refreshTokens, RateLimiter limiter) {
		this.accounts = accounts;
		this.passwords = passwords;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.limiter = limiter;
		this.dummyHash = passwords.encode(UUID.randomUUID().toString());
	}

	/**
	 * D16: {@code schoolId} comes from the school picker. Without it, the password is checked
	 * against every account with this phone or email; the school is required only if it matches
	 * accounts in several schools.
	 */
	@Transactional
	TokenResponse login(UUID schoolId, String login, String password, String clientIp) {
		Optional<String> normalized = normalizeLogin(login);
		// NFR-7: keyed on the normalized login, so "699 20 00 01" and "+237699200001" share a bucket
		String loginKey = normalized.orElse(login.strip().toLowerCase(Locale.ROOT));
		String sourceKey = loginKey + "|" + clientIp;
		checkRate(limiter.tryConsume(LOGIN_PER_IP, clientIp));
		// Reserve a failure before checking the password (atomic check-and-spend), so parallel
		// requests cannot all slip past the limit; the reservation is given back on success.
		checkRate(limiter.tryConsume(LOGIN_FAILURES_PER_SOURCE, sourceKey));
		Duration loginWait = limiter.tryConsume(LOGIN_FAILURES_PER_LOGIN, loginKey);
		if (!loginWait.isZero()) {
			limiter.refund(LOGIN_FAILURES_PER_SOURCE, sourceKey);
			throw new RateLimitedException(loginWait);
		}

		List<UserAccount> candidates = normalized.map(l -> accounts.findByLogin(l, schoolId))
			.orElse(List.of())
			.stream()
			.filter(a -> a.isActive() && a.passwordHash() != null)
			.toList();
		if (candidates.isEmpty()) {
			passwords.matches(password, dummyHash);
			throw invalidCredentials();
		}
		List<UserAccount> matches = candidates.stream().filter(a -> passwords.matches(password, a.passwordHash())).toList();
		if (matches.isEmpty()) {
			throw invalidCredentials();
		}
		// right password: not a failure
		limiter.reset(LOGIN_FAILURES_PER_SOURCE, sourceKey);
		limiter.refund(LOGIN_FAILURES_PER_LOGIN, loginKey);
		if (matches.size() > 1) {
			throw ApiException.invalid("auth.school_required", "schoolId");
		}
		UserAccount account = matches.getFirst();
		return tokens(account, refreshTokens.create(account.id(), UUID.randomUUID()));
	}

	private static void checkRate(Duration wait) {
		if (!wait.isZero()) {
			throw new RateLimitedException(wait);
		}
	}

	/** Rotation: the presented token is used up and a new one is issued in the same family. */
	/**
	 * Sign-out: the refresh token and the rest of its family stop working. Answers the same way
	 * for any token, so it reveals nothing; the access token expires on its own (15 min, NFR-7).
	 */
	@Transactional
	void logout(String rawRefreshToken) {
		refreshTokens.revokeFamilyOf(rawRefreshToken);
	}

	@Transactional(noRollbackFor = ApiException.class) // a reuse-triggered revocation must commit
	TokenResponse refresh(String rawRefreshToken) {
		RefreshTokens.Consumed consumed = refreshTokens.consume(rawRefreshToken).orElseThrow(AuthService::invalidRefresh);
		UserAccount account = accounts.findById(consumed.userId())
			.filter(UserAccount::isActive)
			.orElseThrow(AuthService::invalidRefresh);
		return tokens(account, refreshTokens.create(account.id(), consumed.familyId()));
	}

	/**
	 * D18: the user replaces their (temporary) password. Every other session ends, and this device
	 * gets a fresh token pair. Wrong current passwords are limited like logins (NFR-7), so a stolen
	 * access token cannot be used to guess the password.
	 */
	@Transactional
	TokenResponse changePassword(UUID userId, String currentPassword, String newPassword) {
		String key = userId.toString();
		checkRate(limiter.tryConsume(PASSWORD_CHANGE_FAILURES, key));
		UserAccount account = accounts.findById(userId).orElseThrow(AuthService::invalidRefresh);
		if (account.passwordHash() == null || !passwords.matches(currentPassword, account.passwordHash())) {
			throw ApiException.invalid("auth.wrong_password", "currentPassword");
		}
		limiter.reset(PASSWORD_CHANGE_FAILURES, key);
		if (newPassword.equals(currentPassword)) {
			throw ApiException.invalid("auth.password_unchanged", "newPassword");
		}
		accounts.changePassword(userId, passwords.encode(newPassword));
		refreshTokens.revokeAll(userId);
		UserAccount changed = accounts.findById(userId).orElseThrow();
		return tokens(changed, refreshTokens.create(userId, UUID.randomUUID()));
	}

	private TokenResponse tokens(UserAccount account, String refreshToken) {
		return new TokenResponse("Bearer", accessTokens.issue(account), accessTokens.ttlSeconds(), refreshToken,
				UserInfo.of(account));
	}

	/** Email in lower case, or phone in E.164; anything else cannot match an account. */
	private static Optional<String> normalizeLogin(String login) {
		String trimmed = login.strip();
		return trimmed.contains("@") ? Optional.of(trimmed.toLowerCase(Locale.ROOT)) : PhoneNumbers.normalize(trimmed);
	}

	private static ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid_credentials", null);
	}

	private static ApiException invalidRefresh() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid_refresh_token", null);
	}

}
