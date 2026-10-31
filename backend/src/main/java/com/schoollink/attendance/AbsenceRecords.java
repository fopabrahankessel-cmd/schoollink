package com.schoollink.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Attendance records for the DM's justification (UC-D1, attendance module API). Within
 * {@code schoolId} only (NFR-8). Call both methods in one transaction.
 */
public interface AbsenceRecords {

	/**
	 * The records among {@code ids} that belong to the school, locked until the transaction ends so
	 * that a cancellation cannot slip in between the checks and the change.
	 */
	List<Recorded> lock(UUID schoolId, Collection<UUID> ids);

	/** BR-11: sets the justification of these records, which the caller has checked. */
	void justify(UUID schoolId, Collection<UUID> ids, UUID justificationId);

	/**
	 * @param absent false for a PRESENT row, which has nothing to justify
	 * @param justificationId the current one; {@code null} while unjustified (BR-10)
	 */
	record Recorded(UUID id, UUID studentId, LocalDate date, boolean absent, boolean cancelled,
			UUID justificationId) {
	}

}
