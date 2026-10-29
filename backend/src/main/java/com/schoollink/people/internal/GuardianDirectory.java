package com.schoollink.people.internal;

import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
 * UC-A4: the guardians of a class, with where each one stands (D7): an app account, a live
 * invitation code, an expired one, or none yet. The admin issues codes from this list. Admin only,
 * in the admin's own school (NFR-8). A guardian's phone is in the body, never in a URL (NFR-9).
 */
@Service
class GuardianDirectory {

	/** ACCOUNT: activated, gets push (D7). The others get SMS and may be invited. */
	enum Status {

		ACCOUNT, INVITED, EXPIRED, NOT_INVITED

	}

	record ChildView(UUID id, String firstName, String lastName, UUID classId, String relation, boolean primary) {
	}

	/** {@code invitedAt}/{@code expiresAt}: the latest code, whatever its state; null when none. */
	record GuardianView(UUID id, String name, String phone, String language, Status status, Instant invitedAt,
			Instant expiresAt, List<ChildView> children) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolClasses schoolClasses;

	private final Clock clock;

	GuardianDirectory(NamedParameterJdbcTemplate jdbc, SchoolClasses schoolClasses, Clock clock) {
		this.jdbc = jdbc;
		this.schoolClasses = schoolClasses;
		this.clock = clock;
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
		MapSqlParameterSource p = new MapSqlParameterSource("school", school).addValue("class", classId)
			.addValue("now", Timestamp.from(clock.instant()));
		Map<UUID, GuardianView> guardians = new LinkedHashMap<>();
		jdbc.query("""
				SELECT g.id, g.name, g.phone, g.language, g.user_id IS NOT NULL AS has_account,
				       c.created_at, c.expires_at, c.used_at IS NULL AND c.expires_at <= :now AS expired
				FROM guardian g
				LEFT JOIN LATERAL (SELECT ic.created_at, ic.expires_at, ic.used_at FROM invitation_code ic
				                   WHERE ic.guardian_id = g.id AND ic.school_id = g.school_id
				                   ORDER BY ic.created_at DESC LIMIT 1) c ON true
				WHERE g.school_id = :school AND g.id IN (SELECT sg.guardian_id FROM student_guardian sg
				               JOIN student s ON s.id = sg.student_id AND s.school_id = sg.school_id
				               WHERE s.class_id = :class AND s.school_id = :school)
				ORDER BY g.name, g.id""", p, rs -> {
			Timestamp invitedAt = rs.getTimestamp("created_at");
			Timestamp expiresAt = rs.getTimestamp("expires_at");
			Status status = rs.getBoolean("has_account") ? Status.ACCOUNT
					: invitedAt == null ? Status.NOT_INVITED : rs.getBoolean("expired") ? Status.EXPIRED : Status.INVITED;
			UUID id = rs.getObject("id", UUID.class);
			guardians.put(id, new GuardianView(id, rs.getString("name"), rs.getString("phone"), rs.getString("language"), status,
					invitedAt == null ? null : invitedAt.toInstant(), expiresAt == null ? null : expiresAt.toInstant(),
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
