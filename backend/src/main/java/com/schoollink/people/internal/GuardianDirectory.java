package com.schoollink.people.internal;

import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-A4: the guardians of a class, and whether each one has signed in to the app yet (D7). Admin
 * only, in the admin's own school (NFR-8). A guardian's phone is in the body, never in a URL (NFR-9).
 */
@Service
class GuardianDirectory {

	/** ACCOUNT: signed in to the app, gets push (D7). NO_ACCOUNT: gets SMS, and can sign in at any time. */
	enum Status {

		ACCOUNT, NO_ACCOUNT

	}

	record ChildView(UUID id, String firstName, String lastName, UUID classId, String relation, boolean primary) {
	}

	record GuardianView(UUID id, String name, String phone, String language, Status status, List<ChildView> children) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolClasses schoolClasses;

	GuardianDirectory(NamedParameterJdbcTemplate jdbc, SchoolClasses schoolClasses) {
		this.jdbc = jdbc;
		this.schoolClasses = schoolClasses;
	}

	/** Every guardian of a student of the class, with all their children in the school (siblings too). */
	@Transactional(readOnly = true)
	List<GuardianView> forClass(CurrentUser user, UUID classId) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		if (!schoolClasses.classExists(school, classId)) {
			throw ApiException.notFound("not_found");
		}
		MapSqlParameterSource p = new MapSqlParameterSource("school", school).addValue("class", classId);
		Map<UUID, GuardianView> guardians = new LinkedHashMap<>();
		jdbc.query("""
				SELECT g.id, g.name, g.phone, g.language, g.user_id IS NOT NULL AS has_account
				FROM guardian g
				WHERE g.school_id = :school AND g.id IN (SELECT sg.guardian_id FROM student_guardian sg
				               JOIN student s ON s.id = sg.student_id AND s.school_id = sg.school_id
				               WHERE s.class_id = :class AND s.school_id = :school)
				ORDER BY g.name, g.id""", p, rs -> {
			Status status = rs.getBoolean("has_account") ? Status.ACCOUNT : Status.NO_ACCOUNT;
			UUID id = rs.getObject("id", UUID.class);
			guardians.put(id, new GuardianView(id, rs.getString("name"), rs.getString("phone"), rs.getString("language"), status,
					new ArrayList<>()));
		});
		if (guardians.isEmpty()) {
			return List.of();
		}
		jdbc.query("""
				SELECT sg.guardian_id, s.id, s.first_name, s.last_name, s.class_id, sg.relation, sg.is_primary
				FROM student_guardian sg JOIN student s ON s.id = sg.student_id AND s.school_id = sg.school_id
				WHERE sg.school_id = :school AND sg.guardian_id IN (:guardians)
				ORDER BY s.last_name, s.first_name, s.id""", p.addValue("guardians", guardians.keySet()),
				rs -> {
					guardians.get(rs.getObject("guardian_id", UUID.class)).children()
						.add(new ChildView(rs.getObject("id", UUID.class), rs.getString("first_name"),
								rs.getString("last_name"), rs.getObject("class_id", UUID.class), rs.getString("relation"),
								rs.getBoolean("is_primary")));
				});
		return List.copyOf(guardians.values());
	}

}
