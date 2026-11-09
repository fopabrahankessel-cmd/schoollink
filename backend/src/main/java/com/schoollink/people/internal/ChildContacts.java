package com.schoollink.people.internal;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * UC-P5, D39: who a parent can contact about a child: the teachers of the class with their
 * subjects, and the DMs and assistant DMs of its block, with their phone numbers. Disabled accounts
 * are left out. The phones go in the response body only, never in a log (NFR-9).
 */
@Component
class ChildContacts {

	record Contacts(String className, List<Teacher> teachers, List<DisciplineStaff> discipline) {
	}

	/** {@code phone} null when the account signs in with an email only. */
	record Teacher(String name, List<String> subjects, String phone) {
	}

	/** {@code role}: DM or ASSISTANT_DM. */
	record DisciplineStaff(String name, String role, String phone) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	ChildContacts(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Contacts of(UUID schoolId, UUID classId, UUID blockId, String className) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("class", classId)
			.addValue("block", blockId);
		// one row per teacher, whatever the number of subjects they teach the class
		List<Teacher> teachers = jdbc.query("""
				SELECT u.first_name || ' ' || u.last_name AS name, u.phone, array_agg(t.subject ORDER BY t.subject) AS subjects
				FROM teaching_assignment t JOIN app_user u ON u.school_id = t.school_id AND u.id = t.teacher_id
				WHERE t.school_id = :school AND t.class_id = :class AND u.status <> 'DISABLED'
				GROUP BY u.id, u.first_name, u.last_name, u.phone
				ORDER BY u.last_name, u.first_name, u.id""", p,
				(rs, n) -> new Teacher(rs.getString("name"), strings(rs.getArray("subjects")), rs.getString("phone")));
		List<DisciplineStaff> discipline = blockId == null ? List.of() : jdbc.query("""
				SELECT u.first_name || ' ' || u.last_name AS name, u.phone, r.role
				FROM role_assignment r JOIN app_user u ON u.id = r.user_id AND u.school_id = r.school_id
				WHERE r.school_id = :school AND r.block_id = :block AND r.role IN ('DM', 'ASSISTANT_DM')
				  AND u.status <> 'DISABLED'
				ORDER BY r.role = 'ASSISTANT_DM', u.last_name, u.first_name, u.id""", p,
				(rs, n) -> new DisciplineStaff(rs.getString("name"), rs.getString("role"), rs.getString("phone")));
		return new Contacts(className, teachers, discipline);
	}

	private static List<String> strings(Array array) throws SQLException {
		return Arrays.asList((String[]) array.getArray());
	}

}
