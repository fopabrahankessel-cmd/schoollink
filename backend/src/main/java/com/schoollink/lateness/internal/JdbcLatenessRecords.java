package com.schoollink.lateness.internal;

import com.schoollink.lateness.LatenessRecords;
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
class JdbcLatenessRecords implements LatenessRecords {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcLatenessRecords(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Recorded> lock(UUID schoolId, Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return List.of();
		}
		// locked in id order, so two requests over the same records cannot deadlock
		return jdbc.query("""
				SELECT id, student_id, date, cancelled_at IS NOT NULL AS cancelled, justification_id
				FROM lateness_record WHERE school_id = :school AND id IN (:ids)
				ORDER BY id FOR UPDATE""", new MapSqlParameterSource("school", schoolId).addValue("ids", ids),
				(rs, n) -> new Recorded(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
						rs.getObject("date", LocalDate.class), rs.getBoolean("cancelled"),
						rs.getObject("justification_id", UUID.class)));
	}

	@Override
	public void justify(UUID schoolId, Collection<UUID> ids, UUID justificationId) {
		if (ids.isEmpty()) {
			return;
		}
		// D20: the triggers let only the justification change on a recorded lateness
		jdbc.update("""
				UPDATE lateness_record SET justification_id = :justification
				WHERE school_id = :school AND id IN (:ids) AND cancelled_at IS NULL""",
				new MapSqlParameterSource("school", schoolId).addValue("ids", ids)
					.addValue("justification", justificationId));
	}

}
