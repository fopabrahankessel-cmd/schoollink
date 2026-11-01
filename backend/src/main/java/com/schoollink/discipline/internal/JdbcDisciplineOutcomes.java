package com.schoollink.discipline.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.discipline.DisciplineOutcomes;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcDisciplineOutcomes implements DisciplineOutcomes {

	private final NamedParameterJdbcTemplate jdbc;

	private final AuditLog audit;

	private final Clock clock;

	JdbcDisciplineOutcomes(NamedParameterJdbcTemplate jdbc, AuditLog audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	public UUID recordDisciplinary(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, String description) {
		UUID id = jdbc.queryForObject("""
				INSERT INTO disciplinary_record (school_id, student_id, alert_id, description, recorded_by, recorded_at)
				VALUES (:school, :student, :alert, :description, :by, :now) RETURNING id""",
				params(schoolId, studentId, alertId, actorId).addValue("description", description), UUID.class);
		Map<String, Object> after = new HashMap<>();
		after.put("studentId", studentId.toString());
		after.put("alertId", alertId == null ? null : alertId.toString());
		after.put("description", description);
		after.put("status", "OPEN");
		audit.record(new AuditEntry(schoolId, actorId, AuditAction.CREATE, "disciplinary_record", id.toString(), null,
				after));
		return id;
	}

	@Override
	public UUID refer(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, String note) {
		// D30: one case in progress per student, even when two DMs refer at once
		List<UUID> created = jdbc.queryForList("""
				INSERT INTO counselling_case (school_id, student_id, referred_by, alert_id, referral_note, opened_at)
				VALUES (:school, :student, :by, :alert, :note, :now)
				ON CONFLICT (student_id) WHERE status <> 'CLOSED' DO NOTHING
				RETURNING id""", params(schoolId, studentId, alertId, actorId).addValue("note", note), UUID.class);
		if (created.isEmpty()) {
			return jdbc.queryForObject("""
					SELECT id FROM counselling_case
					WHERE school_id = :school AND student_id = :student AND status <> 'CLOSED'""",
					params(schoolId, studentId, alertId, actorId), UUID.class);
		}
		UUID id = created.get(0);
		Map<String, Object> after = new HashMap<>();
		after.put("studentId", studentId.toString());
		after.put("alertId", alertId == null ? null : alertId.toString());
		after.put("referralNote", note);
		after.put("status", "OPEN");
		audit.record(new AuditEntry(schoolId, actorId, AuditAction.CREATE, "counselling_case", id.toString(), null,
				after));
		return id;
	}

	private MapSqlParameterSource params(UUID schoolId, UUID studentId, UUID alertId, UUID actorId) {
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		return new MapSqlParameterSource("school", schoolId).addValue("student", studentId)
			.addValue("alert", alertId)
			.addValue("by", actorId)
			.addValue("now", Timestamp.from(now));
	}

}
