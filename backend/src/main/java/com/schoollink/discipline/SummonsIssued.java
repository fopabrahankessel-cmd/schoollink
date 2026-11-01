package com.schoollink.discipline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Published inside the transaction that stores a summons (BR-23, D12). The notification module
 * writes, in the same transaction, a push for each guardian with the app and an SMS for each one
 * without, both to go out at once, never held for the digest.
 *
 * @param alertId the risk alert it answers; {@code null} for a summons on its own
 */
public record SummonsIssued(UUID summonsId, UUID schoolId, UUID studentId, UUID alertId, LocalDate meetingDate,
		LocalTime meetingTime, UUID sentBy, Instant sentAt) {
}
