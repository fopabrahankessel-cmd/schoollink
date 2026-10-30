package com.schoollink.people.internal;

import com.schoollink.people.Guardians;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGuardians implements Guardians {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcGuardians(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<StudentGuardian> ofStudents(UUID schoolId, Collection<UUID> studentIds) {
		if (studentIds.isEmpty()) {
			return List.of();
		}
		return jdbc.query("""
				SELECT s.id AS student_id, s.first_name, g.id AS guardian_id, g.language, g.user_id IS NOT NULL AS has_account
				FROM student s
				JOIN student_guardian sg ON sg.student_id = s.id AND sg.school_id = s.school_id
				JOIN guardian g ON g.id = sg.guardian_id AND g.school_id = s.school_id
				WHERE s.school_id = :school AND s.id IN (:students)
				ORDER BY s.id, sg.is_primary DESC, g.id""",
				new MapSqlParameterSource("school", schoolId).addValue("students", studentIds),
				(rs, n) -> new StudentGuardian(rs.getObject("student_id", UUID.class), rs.getString("first_name"),
						rs.getObject("guardian_id", UUID.class), rs.getString("language"), rs.getBoolean("has_account")));
	}

	@Override
	public Contact contact(UUID schoolId, UUID guardianId) {
		return jdbc.queryForObject("SELECT user_id, phone FROM guardian WHERE id = :id AND school_id = :school",
				Map.of("school", schoolId, "id", guardianId),
				(rs, n) -> new Contact(rs.getObject("user_id", UUID.class), rs.getString("phone")));
	}

	@Override
	public String language(UUID schoolId, UUID guardianId) {
		return jdbc.queryForObject("SELECT language FROM guardian WHERE id = :id AND school_id = :school",
				Map.of("school", schoolId, "id", guardianId), String.class);
	}

}
