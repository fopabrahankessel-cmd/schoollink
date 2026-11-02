package com.schoollink.attendance.internal;

import com.schoollink.attendance.AttendanceCounts;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAttendanceCounts implements AttendanceCounts {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcAttendanceCounts(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<DayClass> daily(UUID schoolId, Set<UUID> classIds, LocalDate from, LocalDate to) {
		if (classIds.isEmpty()) {
			return List.of();
		}
		// BR-15: a cancelled absence is left out of the absences; the row stays a student period
		return jdbc.query("""
				SELECT s.date, s.class_id, count(DISTINCT s.id) AS sessions, count(r.id) AS student_periods,
				       count(r.id) FILTER (WHERE r.status = 'ABSENT' AND r.cancelled_at IS NULL) AS absent,
				       count(r.id) FILTER (WHERE r.status = 'ABSENT' AND r.cancelled_at IS NULL
				                             AND r.justification_id IS NULL) AS unjustified
				FROM attendance_session s
				LEFT JOIN attendance_record r ON r.session_id = s.id AND r.school_id = s.school_id
				WHERE s.school_id = :school AND s.class_id IN (:classes) AND s.date BETWEEN :from AND :to
				GROUP BY s.date, s.class_id""",
				new MapSqlParameterSource("school", schoolId).addValue("classes", classIds)
					.addValue("from", from)
					.addValue("to", to),
				(rs, n) -> new DayClass(rs.getObject("date", LocalDate.class), rs.getObject("class_id", UUID.class),
						rs.getInt("sessions"), rs.getInt("student_periods"), rs.getInt("absent"),
						rs.getInt("unjustified")));
	}

}
