package com.schoollink.people;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * D46: a parent resets a forgotten password with an SMS code at most {@code 3} times per school
 * year; then the school administration lets them do it again. Implemented by the auth module, which
 * owns the sign-in codes; everything is within {@code schoolId} only (NFR-8).
 */
public interface ParentPasswordResets {

	/** Of these accounts, those that have used up their resets for this school year. */
	Set<UUID> locked(UUID schoolId, Collection<UUID> userIds);

	/** The admin lets the account reset its password again, in the caller's transaction, audited (NFR-11). */
	void allowAgain(UUID schoolId, UUID userId, UUID adminId);

}
