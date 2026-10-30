package com.schoollink.school.internal;

import com.schoollink.school.SchoolCalendar;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSchoolCalendar implements SchoolCalendar {

	/** The term holding the date, when it is not one of the term's holidays (D3, D10). */
	private static final String TERM_DAY = """
			term t WHERE t.school_id = :school AND CAST(:date AS date) BETWEEN t.start_date AND t.end_date
			  AND NOT EXISTS (SELECT 1 FROM holiday h WHERE h.term_id = t.id AND h.school_id = t.school_id
			                  AND CAST(:date AS date) BETWEEN h.start_date AND h.end_date)""";

	private final NamedParameterJdbcTemplate jdbc;

	JdbcSchoolCalendar(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public LocalTime startTime(UUID schoolId) {
		return jdbc.queryForObject("SELECT start_time FROM school WHERE id = :school", Map.of("school", schoolId),
				LocalTime.class);
	}

	@Override
	public ZoneId zone(UUID schoolId) {
		return ZoneId.of(jdbc.queryForObject("SELECT timezone FROM school WHERE id = :school", Map.of("school", schoolId),
				String.class));
	}

	@Override
	public boolean periodExists(UUID schoolId, UUID periodId) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM period WHERE id = :id AND school_id = :school)",
				Map.of("school", schoolId, "id", periodId), Boolean.class));
	}

	@Override
	public Period period(UUID schoolId, UUID periodId) {
		return jdbc.queryForObject("SELECT number, start_time, end_time FROM period WHERE id = :id AND school_id = :school",
				Map.of("school", schoolId, "id", periodId),
				(rs, n) -> new Period(periodId, rs.getInt("number"), rs.getTime("start_time").toLocalTime(),
						rs.getTime("end_time").toLocalTime()));
	}

	@Override
	public boolean isSchoolDay(UUID schoolId, LocalDate date) {
		return isWeekday(date) && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + TERM_DAY + ")",
				params(schoolId, date), Boolean.class));
	}

	@Override
	public List<Lesson> lessonsOf(UUID schoolId, UUID teacherId, LocalDate date) {
		if (!isWeekday(date)) {
			return List.of();
		}
		return jdbc.query("""
				SELECT ts.class_id, c.name AS class_name, ts.period_id, p.number, p.start_time, p.end_time, ts.subject
				FROM timetable_slot ts
				JOIN school_class c ON c.id = ts.class_id AND c.school_id = ts.school_id
				JOIN period p ON p.id = ts.period_id AND p.school_id = ts.school_id
				WHERE ts.school_id = :school AND ts.teacher_id = :teacher AND ts.weekday = :weekday
				  AND c.academic_year_id IN (SELECT t.academic_year_id FROM %s)
				ORDER BY p.number, c.name, c.id""".formatted(TERM_DAY),
				params(schoolId, date).addValue("teacher", teacherId).addValue("weekday", date.getDayOfWeek().getValue()),
				(rs, n) -> new Lesson(rs.getObject("class_id", UUID.class), rs.getString("class_name"),
						rs.getObject("period_id", UUID.class), rs.getInt("number"),
						rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
						rs.getString("subject")));
	}

	/** D3: Monday to Friday; timetable weekdays are ISO (1 = Monday). */
	private static boolean isWeekday(LocalDate date) {
		return date.getDayOfWeek().getValue() <= 5;
	}

	private static MapSqlParameterSource params(UUID schoolId, LocalDate date) {
		return new MapSqlParameterSource("school", schoolId).addValue("date", date);
	}

}
