package com.schoollink.discipline;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Summoning a parent (discipline module API; BR-23, UC-D3). Called inside the caller's transaction,
 * once the caller has checked the DM's scope. Within {@code schoolId} only (NFR-8).
 */
public interface Summonses {

	/**
	 * Stores the summons, audits it (NFR-11) and publishes {@link SummonsIssued}; its id.
	 *
	 * @param alertId the risk alert it answers, or {@code null}
	 * @param meetingDate {@code null}: the next school day (D31)
	 * @param meetingTime {@code null}: the school start time (D31)
	 * @param reason required without an alert; for the DM's records, not sent to the parents
	 * @throws com.schoollink.shared.error.ApiException 400 when the meeting is not on a coming school day
	 */
	UUID issue(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, LocalDate meetingDate, LocalTime meetingTime,
			String reason);

}
