package com.schoollink.people;

import java.util.Optional;
import java.util.UUID;

/** Parent accounts made by activation (UC-P1; people module API). */
public interface ParentAccounts {

	/**
	 * Uses the invitation and gives its guardian an app account in the guardian's school (D15),
	 * with the PARENT role, in the caller's transaction, and audits it (NFR-11). When a staff
	 * account of that school already has the guardian's phone (a teacher who is also a parent),
	 * the guardian is linked to it and it gets the PARENT role too (D21). Empty when the invitation
	 * is no longer live (used, revoked or expired meanwhile) or that account is disabled.
	 *
	 * @return the account's user id
	 */
	Optional<UUID> activate(UUID schoolId, UUID invitationId);

}
