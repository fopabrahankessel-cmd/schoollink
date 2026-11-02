package com.schoollink.lateness.internal;

import com.schoollink.lateness.LatenessHistory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLatenessHistory implements LatenessHistory {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcLatenessHistory(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Lateness> lateness(UUID schoolId, UUID studentId, LocalDate from, LocalDate to) {
		return jdbc.query("""
				SELECT id, date, arrival_time, justification_id IS NOT NULL AS justified FROM lateness_record
				WHERE school_id = :school AND student_id = :student AND cancelled_at IS NULL AND date BETWEEN :from AND :to
				ORDER BY date DESC""",
				new MapSqlParameterSource("school", schoolId).addValue("student", studentId)
					.addValue("from", from)
					.addValue("to", to),
				(rs, n) -> new Lateness(rs.getObject("id", UUID.class), rs.getObject("date", LocalDate.class),
						rs.getObject("arrival_time", LocalTime.class), rs.getBoolean("justified")));
	}

	@Override
	public List<StudentLateness> latenessOf(UUID schoolId, Collection<UUID> studentIds, LocalDate from, LocalDate to) {
		if (studentIds.isEmpty()) {
			return List.of();
		}
		return jdbc.query("""
				SELECT student_id, date, justification_id IS NOT NULL AS justified FROM lateness_record
				WHERE school_id = :school AND student_id IN (:students) AND cancelled_at IS NULL
				  AND date BETWEEN :from AND :to""",
				new MapSqlParameterSource("school", schoolId).addValue("students", studentIds)
					.addValue("from", from)
					.addValue("to", to),
				(rs, n) -> new StudentLateness(rs.getObject("student_id", UUID.class),
						rs.getObject("date", LocalDate.class), rs.getBoolean("justified")));
	}

	@Override
	public Set<UUID> studentsLate(UUID schoolId, LocalDate from, LocalDate to) {
		return Set.copyOf(jdbc.queryForList("""
				SELECT DISTINCT student_id FROM lateness_record
				WHERE school_id = :school AND cancelled_at IS NULL AND date BETWEEN :from AND :to""",
				new MapSqlParameterSource("school", schoolId).addValue("from", from).addValue("to", to), UUID.class));
	}

}
