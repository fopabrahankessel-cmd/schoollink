package com.schoollink.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A student's recorded absences (attendance module API). Within {@code schoolId} only (NFR-8). */
public interface AttendanceHistory {

	/**
	 * The student's live absences between two dates, inclusive, newest first; cancelled ones are
	 * left out (BR-15).
	 */
	List<Absence> absences(UUID schoolId, UUID studentId, LocalDate from, LocalDate to);

	/** BR-17: the students with a live absence between two dates, inclusive (the nightly detection run). */
	Set<UUID> studentsAbsent(UUID schoolId, LocalDate from, LocalDate to);

	/**
	 * UC-D9, D36: one day of the given classes: the periods with a session, per class, and the live
	 * absences (cancelled ones left out, BR-15), each in the class of its session.
	 */
	Day day(UUID schoolId, Set<UUID> classIds, LocalDate date);

	record Day(Map<UUID, Set<UUID>> recordedPeriods, List<DayAbsence> absences) {

		public Day {
			recordedPeriods = Map.copyOf(recordedPeriods);
			absences = List.copyOf(absences);
		}

	}

	record DayAbsence(UUID studentId, UUID classId, UUID periodId, boolean justified) {
	}

	/** {@code justified}: the DM recorded a justification (BR-11). */
	record Absence(UUID recordId, LocalDate date, UUID periodId, boolean justified) {
	}

}
