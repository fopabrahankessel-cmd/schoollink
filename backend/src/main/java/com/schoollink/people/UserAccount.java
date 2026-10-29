package com.schoollink.people;

import java.util.Set;
import java.util.UUID;

/**
 * What authentication needs to know about an app user. {@code schoolId} is {@code null} only for
 * the researcher; {@code passwordHash} is {@code null} for parents (they sign in with an invitation
 * code and an SMS one-time code, UC-P1).
 */
public record UserAccount(UUID id, UUID schoolId, String passwordHash, String language, String status,
		Set<String> roles) {

	public boolean isActive() {
		return "ACTIVE".equals(status);
	}

}
