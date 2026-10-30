package com.schoollink.people;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Who receives a student's notifications (people module API, BR-20). Everything is within
 * {@code schoolId} only (NFR-8). Phone numbers are not returned: the dispatcher reads them when it
 * sends (NFR-9).
 */
public interface Guardians {

	/** Every guardian of each of these students, with the student's first name for the message. */
	List<StudentGuardian> ofStudents(UUID schoolId, Collection<UUID> studentIds);

	/** The guardian's message language, FR or EN. */
	String language(UUID schoolId, UUID guardianId);

	/**
	 * Where to reach the guardian when a notification is sent: the app account (push) and the phone
	 * (SMS). Never log it (NFR-9).
	 */
	Contact contact(UUID schoolId, UUID guardianId);

	/** {@code userId} null when the guardian has no app account (D7). */
	record Contact(UUID userId, String phone) {

		@Override
		public String toString() {
			return "Contact[userId=" + userId + ", phone=***]";
		}

	}

	/** D7: {@code hasAccount} decides the channel: push for an app account, SMS otherwise. */
	record StudentGuardian(UUID studentId, String studentFirstName, UUID guardianId, String language,
			boolean hasAccount) {
	}

}
