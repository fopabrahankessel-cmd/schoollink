package com.schoollink.attendance.internal;

import com.schoollink.attendance.AttendanceHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAttendanceHistory implements AttendanceHistory {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcAttendanceHistory(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Absence> absences(UUID schoolId, UUID studentId, LocalDate from, LocalDate to) {
		return jdbc.query("""
				SELECT r.id, s.date, s.period_id, r.justification_id IS NOT NULL AS justified
				FROM attendance_record r JOIN attendance_session s ON s.id = r.session_id AND s.school_id = r.school_id
				WHERE r.school_id = :school AND r.student_id = :student AND r.status = 'ABSENT' AND r.cancelled_at IS NULL
				  AND s.date BETWEEN :from AND :to
				ORDER BY s.date DESC""",
				new MapSqlParameterSource("school", schoolId).addValue("student", studentId)
					.addValue("from", from)
					.addValue("to", to),
				(rs, n) -> new Absence(rs.getObject("id", UUID.class), rs.getObject("date", LocalDate.class),
						rs.getObject("period_id", UUID.class), rs.getBoolean("justified")));
	}

}
