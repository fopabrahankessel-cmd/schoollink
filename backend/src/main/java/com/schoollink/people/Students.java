package com.schoollink.people;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Students of a school (people module API). Everything is within {@code schoolId} only (NFR-8). */
public interface Students {

	/** The students currently enrolled in the class; those who left the school are not (D43). */
	Set<UUID> idsInClass(UUID schoolId, UUID classId);

	/**
	 * BR-2: the roll of an attendance session of that day. A student who left the school is in the
	 * rolls of the days before they left (BR-6: a session can be synced later), not after (D43).
	 */
	Set<UUID> idsInClassOn(UUID schoolId, UUID classId, LocalDate date);

	/**
	 * The student's class; empty when the student is not in that school. A student who left keeps
	 * their last class, so what was recorded for them can still be read and cancelled.
	 */
	Optional<UUID> classOf(UUID schoolId, UUID studentId);

	/** D43: as {@link #classOf}, but empty for a student who has left: nothing new is made for them. */
	Optional<UUID> enrolledClassOf(UUID schoolId, UUID studentId);

	/** D43: as {@link #classOf}, but empty when the student had left the school by that day. */
	Optional<UUID> enrolledClassOn(UUID schoolId, UUID studentId, LocalDate date);

	/**
	 * Students of the given classes, by last name then first name. {@code query} null: all of them;
	 * otherwise those whose name contains it or whose matricule starts with it, ignoring case, at
	 * most {@code limit}. Students who left the school are left out (D43).
	 */
	List<Card> inClasses(UUID schoolId, Set<UUID> classIds, String query, int limit);

	/** For history: the students of the classes, those who left the school included (D43). */
	List<Card> everInClasses(UUID schoolId, Set<UUID> classIds);

	/** The given students of the school: id -> card; ids of other schools are left out. */
	Map<UUID, Card> cards(UUID schoolId, Collection<UUID> studentIds);

	/** What staff need to recognise a student (NFR-9: no birth year, no guardian). */
	record Card(UUID id, String matricule, String firstName, String lastName, UUID classId) {
	}

}
