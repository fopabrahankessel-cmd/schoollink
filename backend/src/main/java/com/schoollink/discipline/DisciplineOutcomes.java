package com.schoollink.discipline;

import java.util.UUID;

/**
 * What a DM's decision on a risk alert leads to (discipline module API; BR-18, D4). Called inside
 * the deciding transaction; each creation is audited (NFR-11). Within {@code schoolId} only (NFR-8).
 */
public interface DisciplineOutcomes {

	/** D4: a minimal disciplinary record, OPEN, until its screens come (UC-D5, wave 2); its id. */
	UUID recordDisciplinary(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, String description);

	/**
	 * D4, D30: a referral to the counsellor, a counselling case OPEN until a counsellor takes it; the
	 * case already in progress when there is one. Its id.
	 */
	UUID refer(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, String note);

}
