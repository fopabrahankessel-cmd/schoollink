package com.schoollink.auth.internal;

import com.schoollink.people.StaffCredentialsReset;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * D18: a password reset or a disabled account revokes every refresh token of the user, in the
 * same transaction. Access tokens stop working at once too, because the account is re-read on
 * every request (task 1.8).
 */
@Component
class SessionRevoker {

	private final RefreshTokens refreshTokens;

	SessionRevoker(RefreshTokens refreshTokens) {
		this.refreshTokens = refreshTokens;
	}

	@EventListener
	void on(StaffCredentialsReset event) {
		refreshTokens.revokeAll(event.userId());
	}

}
