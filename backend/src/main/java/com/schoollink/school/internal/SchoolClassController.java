package com.schoollink.school.internal;

import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side of the classes (SPEC §3 "School structure" row, UC-A1); writes come with task 1.9.
 * Admin and counsellor read the whole school; teacher, assistant DM and DM only the classes in their
 * scope; parent and researcher get 403. Another school's class, or one outside the scope, is 404 (NFR-8).
 */
@RestController
class SchoolClassController {

	private static final Role[] WHOLE_SCHOOL = { Role.ADMIN, Role.COUNSELLOR };

	private static final Role[] SCOPED = { Role.TEACHER, Role.ASSISTANT_DM, Role.DM };

	private static final String SELECT = """
			SELECT id, academic_year_id, block_id, name, level FROM school_class
			WHERE school_id = :school AND (:all OR id IN (:ids))
			""";

	private final NamedParameterJdbcTemplate jdbc;

	SchoolClassController(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@GetMapping("/api/v1/classes")
	List<SchoolClassView> list(@AuthenticationPrincipal CurrentUser user) {
		return jdbc.query(SELECT + " ORDER BY level, name", scope(user), SchoolClassController::map);
	}

	@GetMapping("/api/v1/classes/{id}")
	SchoolClassView get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return jdbc.query(SELECT + " AND id = :id", scope(user).addValue("id", id), SchoolClassController::map)
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
	}

	private static MapSqlParameterSource scope(CurrentUser user) {
		if (user.schoolId() == null || !user.hasAny(WHOLE_SCHOOL) && !user.hasAny(SCOPED)) {
			throw ApiException.forbidden();
		}
		Set<UUID> ids = user.classesAs(SCOPED);
		return new MapSqlParameterSource("school", user.schoolId()).addValue("all", user.hasAny(WHOLE_SCHOOL))
			// IN () is invalid SQL; a random id matches nothing
			.addValue("ids", ids.isEmpty() ? Set.of(UUID.randomUUID()) : ids);
	}

	private static SchoolClassView map(ResultSet rs, int row) throws SQLException {
		return new SchoolClassView(rs.getObject("id", UUID.class), rs.getObject("academic_year_id", UUID.class),
				rs.getObject("block_id", UUID.class), rs.getString("name"), rs.getString("level"));
	}

	record SchoolClassView(UUID id, UUID academicYearId, UUID blockId, String name, String level) {
	}

}
