package com.schoollink.lateness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A student's recorded lateness (lateness module API). Within {@code schoolId} only (NFR-8). */
public interface LatenessHistory {

	/**
	 * The student's live lateness between two dates, inclusive, newest first; cancelled ones are
	 * left out (BR-15).
	 */
	List<Lateness> lateness(UUID schoolId, UUID studentId, LocalDate from, LocalDate to);

	/** BR-17: the students with a live lateness between two dates, inclusive (the nightly detection run). */
	Set<UUID> studentsLate(UUID schoolId, LocalDate from, LocalDate to);

	/** {@code justified}: the DM recorded a justification (BR-11). */
	record Lateness(UUID recordId, LocalDate date, LocalTime arrivalTime, boolean justified) {
	}

}
