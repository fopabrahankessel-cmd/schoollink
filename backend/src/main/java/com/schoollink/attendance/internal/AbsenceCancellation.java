package com.schoollink.attendance.internal;

import com.schoollink.attendance.AbsenceCancelled;
import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.Students;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D6: the DM in charge of the student's class cancels an absence recorded by mistake, with a
 * reason (BR-13). The session stays locked and the row stays, marked cancelled once and for all
 * (D20); it no longer counts anywhere (BR-15). The parent who was told gets a correction (BR-14),
 * written by the notification module in this same transaction.
 */
@Service
class AbsenceCancellation {

	static final int MAX_REASON = 300;

	record CancelRequest(@NotBlank @Size(max = MAX_REASON) String reason) {
	}

	record CancelledView(UUID id, Instant cancelledAt, UUID cancelledBy, String reason) {
	}

	private record Row(UUID studentId, LocalDate date, String status, UUID justificationId, boolean cancelled) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Students students;

	private final AuditLog audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	AbsenceCancellation(NamedParameterJdbcTemplate jdbc, Students students, AuditLog audit,
			ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.students = students;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	CancelledView cancel(CurrentUser user, UUID id, CancelRequest request) {
		// BR-13: the DM only; the teacher who recorded it cannot (BR-4)
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("school", school);
		// locked: a justification or a second cancellation waits for this one
		Row row = jdbc.query("""
				SELECT r.student_id, s.date, r.status, r.justification_id, r.cancelled_at IS NOT NULL AS cancelled
				FROM attendance_record r JOIN attendance_session s ON s.id = r.session_id
				WHERE r.id = :id AND r.school_id = :school FOR UPDATE OF r""", p,
				(rs, n) -> new Row(rs.getObject("student_id", UUID.class), rs.getObject("date", LocalDate.class),
						rs.getString("status"), rs.getObject("justification_id", UUID.class), rs.getBoolean("cancelled")))
			.stream()
			.findFirst()
			// NFR-8: a record of another school is not found
			.orElseThrow(() -> ApiException.notFound("not_found"));
		// BR-13, D17: the DM in charge of the student's class; outside the scope the record is not found
		boolean inScope = students.classOf(school, row.studentId()).filter(user.classesAs(Role.DM)::contains).isPresent();
		if (!inScope) {
			throw ApiException.notFound("not_found");
		}
		if (!row.status().equals("ABSENT")) {
			throw ApiException.invalid("cancel.not_an_absence", "id");
		}
		if (row.cancelled()) {
			throw ApiException.conflict("cancel.already_cancelled");
		}

		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		String reason = request.reason().strip();
		jdbc.update("""
				UPDATE attendance_record SET cancelled_at = :now, cancelled_by = :by, cancel_reason = :reason
				WHERE id = :id AND school_id = :school""",
				p.addValue("now", Timestamp.from(now)).addValue("by", user.userId()).addValue("reason", reason));
		events.publishEvent(new AbsenceCancelled(id, school, row.studentId(), row.date(), user.userId(), now));
		// NFR-11: both versions of the record
		Map<String, Object> before = new HashMap<>();
		before.put("status", row.status());
		before.put("justificationId", row.justificationId() == null ? null : row.justificationId().toString());
		before.put("cancelledAt", null);
		Map<String, Object> after = new HashMap<>(before);
		after.put("cancelledAt", now.toString());
		after.put("cancelledBy", user.userId().toString());
		after.put("cancelReason", reason);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CANCEL, "attendance_record", id.toString(),
				before, after));
		return new CancelledView(id, now, user.userId(), reason);
	}

}
