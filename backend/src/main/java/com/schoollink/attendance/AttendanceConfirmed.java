package com.schoollink.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Published inside the transaction that stores a confirmed, locked session (BR-4), once per session:
 * never again when the device replays it. D2: the notification module turns each absence into
 * outbox rows for the student's guardians, in the same transaction; detection runs its rules after
 * it (BR-17). {@code confirmedAt} is the device's confirmation time (BR-6).
 */
public record AttendanceConfirmed(UUID sessionId, UUID schoolId, UUID classId, LocalDate date, UUID periodId,
		UUID teacherId, Instant confirmedAt, List<Absence> absences) {

	/** One absent student and the attendance record that holds the absence. */
	public record Absence(UUID recordId, UUID studentId) {
	}

	public AttendanceConfirmed {
		absences = List.copyOf(absences);
	}

	public Set<UUID> absentStudentIds() {
		return absences.stream().map(Absence::studentId).collect(Collectors.toUnmodifiableSet());
	}

}
