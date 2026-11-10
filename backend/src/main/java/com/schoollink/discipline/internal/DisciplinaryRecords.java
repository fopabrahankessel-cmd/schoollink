package com.schoollink.discipline.internal;

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
import java.util.List;
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

	DisciplinaryRecords(DisciplineOutcomes outcomes, Students students, SchoolCalendar calendar,
			NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.outcomes = outcomes;
		this.students = students;
		this.calendar = calendar;
		this.jdbc = jdbc;
		this.clock = clock;
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
