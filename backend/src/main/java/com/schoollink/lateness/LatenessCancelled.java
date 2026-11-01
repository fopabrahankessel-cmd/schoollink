package com.schoollink.lateness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published inside the transaction in which the DM cancels a lateness (D11, D20). The notification
 * module withdraws what was not sent yet and writes the corrections (BR-14), in the same
 * transaction; the record no longer counts anywhere (BR-15).
 */
public record LatenessCancelled(UUID recordId, UUID schoolId, UUID studentId, LocalDate date, UUID cancelledBy,
		Instant cancelledAt) {
}
