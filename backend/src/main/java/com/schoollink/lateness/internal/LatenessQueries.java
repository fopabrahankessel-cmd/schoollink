package com.schoollink.lateness.internal;

import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the gate screen reads (UC-AD1, UC-AD2), always within the caller's scope (NFR-8).
 */
@Service
class LatenessQueries {

	/** Results of a typed search; the full list (no query) is for the device's offline copy. */
	static final int SEARCH_LIMIT = 50;

	record GateStudent(UUID id, String matricule, String firstName, String lastName, UUID classId,
			String className) {
	}

	/** {@code justified}: the DM recorded a justification (BR-11). */
	record LatenessItem(UUID id, UUID studentId, String firstName, String lastName, UUID classId, String className,
			LocalTime arrivalTime, Instant recordedAt, UUID recordedBy, boolean justified) {
	}

	record LatenessDay(LocalDate date, LocalTime startTime, List<LatenessItem> lateness) {
	}

	private record Row(UUID id, UUID studentId, LocalTime arrivalTime, Instant recordedAt, UUID recordedBy,
			boolean justified) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Students students;

	private final SchoolClasses classes;

	private final SchoolCalendar calendar;

	private final Clock clock;

	LatenessQueries(NamedParameterJdbcTemplate jdbc, Students students, SchoolClasses classes,
			SchoolCalendar calendar, Clock clock) {
		this.jdbc = jdbc;
		this.students = students;
		this.classes = classes;
		this.calendar = calendar;
		this.clock = clock;
	}

	/**
	 * UC-AD1: the students the assistant DM may record, by name or matricule. Without a query, all
	 * of them, so the app can search offline (NFR-3).
	 */
	@Transactional(readOnly = true)
	List<GateStudent> gateStudents(CurrentUser user, String query) {
		if (user.schoolId() == null || !user.has(Role.ASSISTANT_DM)) {
			throw ApiException.forbidden();
		}
		String q = query == null || query.isBlank() ? null : query.strip();
		if (q != null && q.length() > 100) {
			// same arguments as @Size: {1} max, {2} min
			throw ApiException.invalid("validation.size", "q", "q", 100, 1);
		}
		Set<UUID> scope = user.classesAs(Role.ASSISTANT_DM);
		List<Card> found = students.inClasses(user.schoolId(), scope, q, SEARCH_LIMIT);
		Map<UUID, String> names = classes.names(user.schoolId(), scope);
		return found.stream()
			.map(c -> new GateStudent(c.id(), c.matricule(), c.firstName(), c.lastName(), c.classId(),
					names.get(c.classId())))
			.toList();
	}

	/**
	 * UC-AD2: the live lateness of a day (cancelled ones do not count, BR-15), latest arrival first.
	 * Assistant DMs and DMs see their scope; the admin the whole school. {@code date} null: today.
	 */
	@Transactional(readOnly = true)
	LatenessDay day(CurrentUser user, LocalDate date) {
		if (user.schoolId() == null || !user.hasAny(Role.ADMIN, Role.ASSISTANT_DM, Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		LocalDate day = date != null ? date : clock.instant().atZone(calendar.zone(school)).toLocalDate();
		MapSqlParameterSource p = new MapSqlParameterSource("school", school).addValue("date", day);
		List<Row> rows = jdbc.query("""
				SELECT id, student_id, arrival_time, recorded_at, recorded_by, justification_id IS NOT NULL AS justified
				FROM lateness_record WHERE school_id = :school AND date = :date AND cancelled_at IS NULL
				ORDER BY arrival_time DESC, recorded_at DESC""", p,
				(rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
						rs.getObject("arrival_time", LocalTime.class), rs.getTimestamp("recorded_at").toInstant(),
						rs.getObject("recorded_by", UUID.class), rs.getBoolean("justified")));
		Map<UUID, Card> cards = students.cards(school, rows.stream().map(Row::studentId).toList());
		boolean wholeSchool = user.has(Role.ADMIN);
		Set<UUID> scope = user.classesAs(Role.ASSISTANT_DM, Role.DM);
		List<Row> visible = rows.stream().filter(r -> {
			Card card = cards.get(r.studentId());
			return card != null && (wholeSchool || scope.contains(card.classId()));
		}).toList();
		Map<UUID, String> names = classes.names(school,
				Set.copyOf(visible.stream().map(r -> cards.get(r.studentId()).classId()).toList()));
		List<LatenessItem> items = visible.stream().map(r -> {
			Card card = cards.get(r.studentId());
			return new LatenessItem(r.id(), r.studentId(), card.firstName(), card.lastName(), card.classId(),
					names.get(card.classId()), r.arrivalTime(), r.recordedAt(), r.recordedBy(), r.justified());
		}).toList();
		return new LatenessDay(day, calendar.startTime(school), items);
	}

}
