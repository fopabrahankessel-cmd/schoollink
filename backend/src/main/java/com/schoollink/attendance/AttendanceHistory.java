package com.schoollink.attendance;

import java.time.LocalDate;
import java.util.List;
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

	/** {@code justified}: the DM recorded a justification (BR-11). */
	record Absence(UUID recordId, LocalDate date, UUID periodId, boolean justified) {
	}

}
