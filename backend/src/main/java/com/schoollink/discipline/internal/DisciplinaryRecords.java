package com.schoollink.discipline.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.discipline.DisciplinaryCategory;
import com.schoollink.discipline.DisciplinaryMeasure;
import com.schoollink.discipline.DisciplineOutcomes;
import com.schoollink.people.Students;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D5, D37: the disciplinary records of a student. The DM whose blocks hold the student's current
 * class records them; that DM and the admin read them (SPEC §3, NFR-8). Cancelled records are left
 * out. Nothing here notifies the parents (design §11, call 5).
 */
@Service
class DisciplinaryRecords {

	/** D37: the date of an incident is at most this many days before the school's today. */
	static final int DAYS_BACK = 30;

	static final int MAX_TEXT = 500;

	/** {@code measure} and {@code occurredOn} may be left out: NONE, and the school's today. */
	record IncidentRequest(DisciplinaryCategory category, DisciplinaryMeasure measure, LocalDate occurredOn,
			String description) {
	}

	record RecordView(UUID id, UUID studentId, UUID alertId, String category, String measure, LocalDate occurredOn,
			String description, String status, String closeNote, String recordedByName, Instant recordedAt,
			Instant closedAt) {
	}

	static final String COLUMNS = """
			d.id, d.student_id, d.alert_id, d.category, d.measure, d.occurred_on, d.description, d.status, d.close_note,
			u.first_name || ' ' || u.last_name AS recorded_by_name, d.recorded_at, d.closed_at""";

	static final String FROM = """
			FROM disciplinary_record d JOIN app_user u ON u.school_id = d.school_id AND u.id = d.recorded_by""";

	private final DisciplineOutcomes outcomes;

	private final Students students;

	private final SchoolCalendar calendar;

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	private final AuditLog audit;

	DisciplinaryRecords(DisciplineOutcomes outcomes, Students students, SchoolCalendar calendar,
			NamedParameterJdbcTemplate jdbc, Clock clock, AuditLog audit) {
		this.outcomes = outcomes;
		this.students = students;
		this.calendar = calendar;
		this.jdbc = jdbc;
		this.clock = clock;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	List<RecordView> list(CurrentUser user, UUID studentId) {
		if (user.schoolId() == null || !user.hasAny(Role.ADMIN, Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		UUID classId = students.classOf(school, studentId).orElseThrow(() -> ApiException.notFound("not_found"));
		if (!user.has(Role.ADMIN) && !user.classesAs(Role.DM).contains(classId)) {
			throw ApiException.notFound("not_found");
		}
		return jdbc.query("SELECT " + COLUMNS + " " + FROM + """
				 WHERE d.school_id = :school AND d.student_id = :student AND d.cancelled_at IS NULL
				ORDER BY d.occurred_on DESC, d.recorded_at DESC, d.id""",
				new MapSqlParameterSource("school", school).addValue("student", studentId), (rs, n) -> view(rs));
	}

	@Transactional
	RecordView create(CurrentUser user, UUID studentId, IncidentRequest request) {
		UUID school = dmSchool(user);
		requireInScope(user, school, studentId);
		if (request.category() == null) {
			throw ApiException.invalid("discipline.category_required", "category");
		}
		String description = request.description() == null ? "" : request.description().strip();
		if (description.isEmpty() || description.length() > MAX_TEXT) {
			throw ApiException.invalid("discipline.description_required", "description");
		}
		LocalDate today = clock.instant().atZone(calendar.zone(school)).toLocalDate();
		LocalDate day = request.occurredOn() == null ? today : request.occurredOn();
		if (day.isAfter(today) || day.isBefore(today.minusDays(DAYS_BACK))) {
			throw ApiException.invalid("discipline.occurred_on_range", "occurredOn");
		}
		DisciplinaryMeasure measure = request.measure() == null ? DisciplinaryMeasure.NONE : request.measure();
		UUID id = outcomes.recordDisciplinary(school, studentId, null, user.userId(), request.category(), measure, day,
				description);
		return find(school, id);
	}

	/** D37: closes an OPEN record once, with an optional note. */
	@Transactional
	RecordView close(CurrentUser user, UUID id, String note) {
		UUID school = dmSchool(user);
		String closeNote = note == null || note.isBlank() ? null : note.strip();
		if (closeNote != null && closeNote.length() > MAX_TEXT) {
			throw ApiException.invalid("discipline.note_too_long", "note");
		}
		Locked record = lock(user, school, id);
		if (!record.open()) {
			throw ApiException.conflict("discipline.already_closed");
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("""
				UPDATE disciplinary_record SET status = 'CLOSED', close_note = :note, closed_by = :by, closed_at = :now
				WHERE school_id = :school AND id = :id""",
				new MapSqlParameterSource("school", school).addValue("id", id)
					.addValue("note", closeNote)
					.addValue("by", user.userId())
					.addValue("now", Timestamp.from(now)));
		// NFR-11
		Map<String, Object> after = new HashMap<>();
		after.put("status", "CLOSED");
		after.put("closeNote", closeNote);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "disciplinary_record", id.toString(),
				Map.of("status", "OPEN"), after));
		return find(school, id);
	}

	/**
	 * D37: a record made by mistake is cancelled with a reason and hidden from then on; open or
	 * closed (the spirit of BR-13 to BR-15).
	 */
	@Transactional
	void cancel(CurrentUser user, UUID id, String reason) {
		UUID school = dmSchool(user);
		String why = reason == null ? "" : reason.strip();
		if (why.isEmpty() || why.length() > MAX_TEXT) {
			throw ApiException.invalid("discipline.reason_required", "reason");
		}
		Locked record = lock(user, school, id);
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("""
				UPDATE disciplinary_record SET cancelled_at = :now, cancelled_by = :by, cancel_reason = :reason
				WHERE school_id = :school AND id = :id""",
				new MapSqlParameterSource("school", school).addValue("id", id)
					.addValue("reason", why)
					.addValue("by", user.userId())
					.addValue("now", Timestamp.from(now)));
		// NFR-11
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CANCEL, "disciplinary_record", id.toString(),
				Map.of("status", record.open() ? "OPEN" : "CLOSED"), Map.of("cancelReason", why)));
	}

	private record Locked(UUID studentId, boolean open) {
	}

	/**
	 * The record, locked so two closes or cancels run one after the other; another school's, a
	 * cancelled one, or one whose student is outside the DM's blocks is not found (NFR-8).
	 */
	private Locked lock(CurrentUser user, UUID school, UUID id) {
		Locked record = jdbc.query("""
				SELECT student_id, status FROM disciplinary_record
				WHERE school_id = :school AND id = :id AND cancelled_at IS NULL FOR UPDATE""",
				new MapSqlParameterSource("school", school).addValue("id", id),
				(rs, n) -> new Locked(rs.getObject("student_id", UUID.class), "OPEN".equals(rs.getString("status"))))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		requireInScope(user, school, record.studentId());
		return record;
	}

	/** The DM's school; 403 for anyone who is not a DM (SPEC §3: only the DM writes). */
	static UUID dmSchool(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	/** NFR-8, D17: the student's current class is in the DM's blocks; otherwise not found. */
	void requireInScope(CurrentUser user, UUID school, UUID studentId) {
		boolean inScope = students.classOf(school, studentId).filter(user.classesAs(Role.DM)::contains).isPresent();
		if (!inScope) {
			throw ApiException.notFound("not_found");
		}
	}

	RecordView find(UUID school, UUID id) {
		return jdbc.queryForObject("SELECT " + COLUMNS + " " + FROM + " WHERE d.school_id = :school AND d.id = :id",
				new MapSqlParameterSource("school", school).addValue("id", id), (rs, n) -> view(rs));
	}

	static RecordView view(ResultSet rs) throws SQLException {
		Timestamp closedAt = rs.getTimestamp("closed_at");
		return new RecordView(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getObject("alert_id", UUID.class), rs.getString("category"), rs.getString("measure"),
				rs.getObject("occurred_on", LocalDate.class), rs.getString("description"), rs.getString("status"),
				rs.getString("close_note"), rs.getString("recorded_by_name"), rs.getTimestamp("recorded_at").toInstant(),
				closedAt == null ? null : closedAt.toInstant());
	}

}
