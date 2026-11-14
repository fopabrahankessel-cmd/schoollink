package com.schoollink.auth.internal;

import com.schoollink.people.GuardianPhoneChanged;
import com.schoollink.people.StaffCredentialsReset;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D18: a password reset or a disabled account revokes every refresh token of the user, in the
 * same transaction. Access tokens stop working at once too, because the account is re-read on
 * every request (task 1.8).
 * <p>
 * D43, D24: when a guardian's phone changes, their sessions end the same way, and a sign-in code
 * already sent to the old number, not used yet, is thrown away: the old number may be somebody
 * else's now.
 */
@Component
class SessionRevoker {

	private final RefreshTokens refreshTokens;

	private final JdbcTemplate jdbc;

	SessionRevoker(RefreshTokens refreshTokens, JdbcTemplate jdbc) {
		this.refreshTokens = refreshTokens;
		this.jdbc = jdbc;
	}

	@EventListener
	void on(StaffCredentialsReset event) {
		refreshTokens.revokeAll(event.userId());
	}

	@EventListener
	void on(GuardianPhoneChanged event) {
		UUID user = event.userId();
		if (user != null) {
			refreshTokens.revokeAll(user);
		}
		jdbc.update("""
				DELETE FROM parent_sign_in
				WHERE school_id = ? AND verified_at IS NULL AND (guardian_id = ? OR user_id = ?)""", event.schoolId(),
				event.guardianId(), user);
	}

}
