package com.schoollink.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Published inside the transaction that stores a confirmed, locked session (BR-4), once per session:
 * never again when the device replays it. D2: the notification module turns each absentee into
 * outbox rows for their guardians, in the same transaction (task 2.4); detection runs its rules
 * after it (BR-17). {@code confirmedAt} is the device's confirmation time (BR-6).
 */
public record AttendanceConfirmed(UUID sessionId, UUID schoolId, UUID classId, LocalDate date, UUID periodId,
		UUID teacherId, Instant confirmedAt, Set<UUID> absentStudentIds) {

	public AttendanceConfirmed {
		absentStudentIds = Set.copyOf(absentStudentIds);
	}

}
