package com.schoollink.attendance.internal;

import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolCalendar.Lesson;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-T3: the sessions a teacher is expected to record on a day, from the timetable (D1), each with
 * whether it is recorded yet. The period under way comes first (UC-T1 step 1), then the others by
 * period. A session counts as recorded whoever recorded it (a colleague standing in, for example).
 * Teachers only, for their own timetable (NFR-8).
 */
@Service
class TeacherSessions {

	/**
	 * {@code current}: the period under way, in the school's time zone, on today's date only.
	 * {@code sessionId}, {@code confirmedAt} and {@code absentCount} are null until it is recorded;
	 * cancelled absences are not counted (BR-15).
	 */
	record ExpectedSession(UUID classId, String className, UUID periodId, int periodNumber, LocalTime startTime,
			LocalTime endTime, String subject, boolean current, boolean recorded, UUID sessionId, Instant confirmedAt,
			Integer absentCount) {
	}

	/** {@code schoolDay} false (weekend, holiday, outside any term) means nothing is expected (D3, D10). */
	record TeacherDay(LocalDate date, boolean schoolDay, List<ExpectedSession> sessions) {
	}

	private record Recorded(UUID sessionId, Instant confirmedAt, int absentCount) {
	}

	private record Slot(UUID classId, UUID periodId) {
	}

	private final SchoolCalendar calendar;

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	TeacherSessions(SchoolCalendar calendar, NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.calendar = calendar;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** {@code date} null means today, in the school's time zone. */
	@Transactional(readOnly = true)
	TeacherDay forDay(CurrentUser user, LocalDate date) {
		if (user.schoolId() == null || !user.has(Role.TEACHER)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		ZonedDateTime now = clock.instant().atZone(calendar.zone(school));
		LocalDate day = date == null ? now.toLocalDate() : date;
		if (!calendar.isSchoolDay(school, day)) {
			return new TeacherDay(day, false, List.of());
		}
		List<Lesson> lessons = calendar.lessonsOf(school, user.userId(), day);
		Map<Slot, Recorded> recorded = recorded(school, day, lessons);
		boolean today = day.equals(now.toLocalDate());
		LocalTime time = now.toLocalTime();
		List<ExpectedSession> sessions = lessons.stream().map(lesson -> {
			Recorded r = recorded.get(new Slot(lesson.classId(), lesson.periodId()));
			boolean current = today && !time.isBefore(lesson.startTime()) && time.isBefore(lesson.endTime());
			return new ExpectedSession(lesson.classId(), lesson.className(), lesson.periodId(), lesson.periodNumber(),
					lesson.startTime(), lesson.endTime(), lesson.subject(), current, r != null,
					r == null ? null : r.sessionId(), r == null ? null : r.confirmedAt(),
					r == null ? null : r.absentCount());
		})
			// stable sort: the lessons are already by period
			.sorted(Comparator.comparing(session -> !session.current()))
			.toList();
		return new TeacherDay(day, true, sessions);
	}

	/** The sessions already recorded for these lessons' classes on that day, by class and period. */
	private Map<Slot, Recorded> recorded(UUID school, LocalDate day, List<Lesson> lessons) {
		Map<Slot, Recorded> result = new HashMap<>();
		if (lessons.isEmpty()) {
			return result;
		}
		MapSqlParameterSource p = new MapSqlParameterSource("school", school).addValue("date", day)
			.addValue("classes", lessons.stream().map(Lesson::classId).distinct().toList());
		jdbc.query("""
				SELECT s.id, s.class_id, s.period_id, s.confirmed_at,
				       (SELECT count(*) FROM attendance_record r
				        WHERE r.session_id = s.id AND r.school_id = s.school_id
				          AND r.status = 'ABSENT' AND r.cancelled_at IS NULL) AS absent_count
				FROM attendance_session s
				WHERE s.school_id = :school AND s.date = :date AND s.class_id IN (:classes)""", p, rs -> {
			result.put(new Slot(rs.getObject("class_id", UUID.class), rs.getObject("period_id", UUID.class)),
					new Recorded(rs.getObject("id", UUID.class), rs.getTimestamp("confirmed_at").toInstant(),
							rs.getInt("absent_count")));
		});
		return result;
	}

}
