package com.schoollink.attendance.internal;

import com.schoollink.attendance.AttendanceHistory;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	@Override
	public Set<UUID> studentsAbsent(UUID schoolId, LocalDate from, LocalDate to) {
		return Set.copyOf(jdbc.queryForList("""
				SELECT DISTINCT r.student_id
				FROM attendance_record r JOIN attendance_session s ON s.id = r.session_id AND s.school_id = r.school_id
				WHERE r.school_id = :school AND r.status = 'ABSENT' AND r.cancelled_at IS NULL
				  AND s.date BETWEEN :from AND :to""",
				new MapSqlParameterSource("school", schoolId).addValue("from", from).addValue("to", to), UUID.class));
	}

	@Override
	public Day day(UUID schoolId, Set<UUID> classIds, LocalDate date) {
		if (classIds.isEmpty()) {
			return new Day(Map.of(), List.of());
		}
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("classes", classIds)
			.addValue("date", date);
		Map<UUID, Set<UUID>> recorded = new HashMap<>();
		jdbc.query("""
				SELECT class_id, period_id FROM attendance_session
				WHERE school_id = :school AND class_id IN (:classes) AND date = :date""", p, rs -> {
			recorded.computeIfAbsent(rs.getObject("class_id", UUID.class), c -> new HashSet<>())
				.add(rs.getObject("period_id", UUID.class));
		});
		List<DayAbsence> absences = jdbc.query("""
				SELECT r.student_id, s.class_id, s.period_id, r.justification_id IS NOT NULL AS justified
				FROM attendance_record r JOIN attendance_session s ON s.id = r.session_id AND s.school_id = r.school_id
				WHERE r.school_id = :school AND s.class_id IN (:classes) AND s.date = :date
				  AND r.status = 'ABSENT' AND r.cancelled_at IS NULL""", p,
				(rs, n) -> new DayAbsence(rs.getObject("student_id", UUID.class), rs.getObject("class_id", UUID.class),
						rs.getObject("period_id", UUID.class), rs.getBoolean("justified")));
		return new Day(recorded, absences);
	}

}
