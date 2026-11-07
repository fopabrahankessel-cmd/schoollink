package com.schoollink.people;

import java.util.Optional;
import java.util.UUID;

/** Parent accounts, made at the first sign-in (UC-P1; people module API). */
public interface ParentAccounts {

	/**
	 * Gives the guardian an app account in their school (D15), with the PARENT role, in the caller's
	 * transaction, and audits it (NFR-11). A guardian who has an account keeps it. Empty when an
	 * account they do not own has their phone (staff sign in with their password, D21) or their
	 * account is disabled (D18).
	 *
	 * @return the account's user id
	 */
	Optional<UUID> activate(UUID schoolId, UUID guardianId);

	/**
	 * D21: a staff member who has just proved themselves with their password, and whose phone is a
	 * guardian's without an account, becomes that guardian: one person, one account per school. The
	 * account gets the PARENT role too, in the caller's transaction, and it is audited (NFR-11).
	 *
	 * @return whether a guardian was linked now
	 */
	boolean linkStaff(UUID schoolId, UUID userId);

}
