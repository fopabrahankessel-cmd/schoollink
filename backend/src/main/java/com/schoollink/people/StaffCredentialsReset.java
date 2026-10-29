package com.schoollink.people;

import java.util.UUID;

/**
 * Published, inside the transaction, when the admin gives a staff member a new temporary password
 * or disables the account (D18): the user's existing sessions must end.
 */
public record StaffCredentialsReset(UUID userId) {
}
