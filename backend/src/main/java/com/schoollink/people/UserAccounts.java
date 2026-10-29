package com.schoollink.people;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read access to app user accounts for the auth module (people module API). */
public interface UserAccounts {

	/**
	 * Accounts whose phone ({@code +237...}, already normalized) or email (lower case) equals
	 * {@code login}. A phone or email is unique within one school only (D15), so without a
	 * {@code schoolId} several accounts may match.
	 */
	List<UserAccount> findByLogin(String login, UUID schoolId);

	Optional<UserAccount> findById(UUID id);

	/** The assignments behind a user's scope (NFR-8), all within {@code schoolId}. */
	ScopeAssignments assignmentsOf(UUID userId, UUID schoolId);

	/**
	 * Replaces the user's password with one they chose, ending the temporary password (D18), and
	 * audits it (NFR-11). Must be called inside a transaction.
	 */
	void changePassword(UUID userId, String passwordHash);

}
