package com.schoollink.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Published inside the transaction that issues an invitation code (UC-A4). D13: the notification
 * module turns it into an {@code INVITATION} SMS in the outbox, in the same transaction (task 2.4).
 * {@code code} is the only copy of the code in clear: never log or store it elsewhere.
 */
public record InvitationIssued(UUID schoolId, UUID guardianId, String code, Instant expiresAt) {

	@Override
	public String toString() {
		return "InvitationIssued[schoolId=" + schoolId + ", guardianId=" + guardianId + ", expiresAt=" + expiresAt + "]";
	}

}
