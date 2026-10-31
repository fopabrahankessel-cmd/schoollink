package com.schoollink.attendance.internal;

import com.schoollink.attendance.AbsenceRecords;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcAbsenceRecords implements AbsenceRecords {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcAbsenceRecords(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Recorded> lock(UUID schoolId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		// locked in id order, so two requests over the same records cannot deadlock
		return jdbc.query("""
				SELECT r.id, r.student_id, s.date, r.status = 'ABSENT' AS absent, r.cancelled_at IS NOT NULL AS cancelled,
				       r.justification_id
				FROM attendance_record r JOIN attendance_session s ON s.id = r.session_id
				WHERE r.school_id = :school AND r.id IN (:ids)
				ORDER BY r.id FOR UPDATE OF r""", new MapSqlParameterSource("school", schoolId).addValue("ids", ids),
				(rs, n) -> new Recorded(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
						rs.getObject("date", LocalDate.class), rs.getBoolean("absent"), rs.getBoolean("cancelled"),
						rs.getObject("justification_id", UUID.class)));
	}

	@Override
	public void justify(UUID schoolId, Collection<UUID> ids, UUID justificationId) {
		if (ids.isEmpty()) {
			return;
		}
		// D20: the triggers let only the justification change on a locked record
		jdbc.update("""
				UPDATE attendance_record SET justification_id = :justification
				WHERE school_id = :school AND id IN (:ids) AND status = 'ABSENT' AND cancelled_at IS NULL""",
				new MapSqlParameterSource("school", schoolId).addValue("ids", ids)
					.addValue("justification", justificationId));
	}

}
