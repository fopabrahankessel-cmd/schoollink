package com.schoollink.people;

import java.util.Optional;
import java.util.UUID;

/**
 * Invitation codes for parent activation (UC-A4, UC-P1; people module API). Codes are compared
 * case-insensitively, ignoring spaces and dashes, so "k7qm 3xpa" is "K7QM-3XPA".
 */
public interface Invitations {

	/** A live code: not used, not revoked, not expired. */
	record PendingInvitation(UUID invitationId, UUID schoolId, UUID guardianId) {
	}

	/** The live invitation for this code, without using it (UC-P1 step 1: send the SMS one-time code). */
	Optional<PendingInvitation> find(String code);

	/**
	 * Uses the code, atomically: of two concurrent calls, only one gets it (UC-P1 step 2). Must be
	 * called inside the transaction that creates the parent's account.
	 */
	Optional<PendingInvitation> use(String code);

}
