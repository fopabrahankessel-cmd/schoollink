package com.schoollink.discipline;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What a DM's decision on a risk alert leads to (discipline module API; BR-18, D4). Called inside
 * the deciding transaction; each creation is audited (NFR-11). Within {@code schoolId} only (NFR-8).
 */
public interface DisciplineOutcomes {

	/**
	 * D4, D37: a disciplinary record, OPEN, from an alert decision ({@code alertId}) or from the
	 * student page ({@code alertId} null); its id. {@code occurredOn} null means the school's today.
	 * The caller checks the scope and the values (UC-D5).
	 */
	UUID recordDisciplinary(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, DisciplinaryCategory category,
			DisciplinaryMeasure measure, LocalDate occurredOn, String description);

	/**
	 * D4, D30: a referral to the counsellor, a counselling case OPEN until a counsellor takes it; the
	 * case already in progress when there is one. Its id.
	 */
	UUID refer(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, String note);

}
