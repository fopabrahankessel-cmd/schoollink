package com.schoollink.lateness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Published inside the transaction that stores a lateness record (BR-7), once: never again when
 * the device replays it. The notification module turns it into outbox rows for the student's
 * guardians, in the same transaction (BR-20); detection runs its rules after it (BR-17).
 * {@code recordedAt} is the device's time (NFR-3).
 */
public record LatenessRecorded(UUID recordId, UUID schoolId, UUID studentId, UUID classId, LocalDate date,
		LocalTime arrivalTime, UUID recordedBy, Instant recordedAt) {
}
