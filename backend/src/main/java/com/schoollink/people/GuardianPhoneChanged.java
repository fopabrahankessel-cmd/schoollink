package com.schoollink.people;

import java.util.UUID;

/**
 * Published, inside the transaction, when the admin changes a guardian's phone (D43). The old
 * number may now be somebody else's: the sessions of the guardian's app account must end, their
 * phones must stop receiving pushes, and a sign-in code already sent to the old number must stop
 * working (D24). NFR-9: the event carries no phone.
 *
 * @param userId the guardian's app account, or null when they have none
 */
public record GuardianPhoneChanged(UUID schoolId, UUID guardianId, UUID userId) {
}
