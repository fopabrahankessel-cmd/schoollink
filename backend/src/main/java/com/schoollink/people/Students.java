package com.schoollink.people;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Students of a school (people module API). Everything is within {@code schoolId} only (NFR-8). */
public interface Students {

	/** The students currently enrolled in the class (BR-2: the roll of an attendance session). */
	Set<UUID> idsInClass(UUID schoolId, UUID classId);

	/** The student's class; empty when the student is not in that school. */
	Optional<UUID> classOf(UUID schoolId, UUID studentId);

	/**
	 * Students of the given classes, by last name then first name. {@code query} null: all of them;
	 * otherwise those whose name contains it or whose matricule starts with it, ignoring case, at
	 * most {@code limit}.
	 */
	List<Card> inClasses(UUID schoolId, Set<UUID> classIds, String query, int limit);

	/** The given students of the school: id -> card; ids of other schools are left out. */
	Map<UUID, Card> cards(UUID schoolId, Collection<UUID> studentIds);

	/** What staff need to recognise a student (NFR-9: no birth year, no guardian). */
	record Card(UUID id, String matricule, String firstName, String lastName, UUID classId) {
	}

}
