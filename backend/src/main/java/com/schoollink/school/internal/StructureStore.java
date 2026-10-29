package com.schoollink.school.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.jdbc.ConstraintGuard;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the school structure tables (UC-A1) with the SPEC §3 "School structure" row:
 * the admin writes; admin and counsellor read the whole school; teacher, assistant DM and DM read
 * the calendar in full and classes, blocks and timetable within their scope; parent and researcher
 * get 403. Every query is filtered by the signed-in user's school (NFR-8), and every change is
 * audited in the same transaction (NFR-11).
 */
@Component
class StructureStore {

	private static final Role[] WHOLE_SCHOOL = { Role.ADMIN, Role.COUNSELLOR };

	private static final Role[] SCOPED = { Role.TEACHER, Role.ASSISTANT_DM, Role.DM };

	private final NamedParameterJdbcTemplate jdbc;

	private final AuditLog audit;

	private final ConstraintGuard guard;

	StructureStore(NamedParameterJdbcTemplate jdbc, AuditLog audit, ConstraintGuard guard) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.guard = guard;
	}

	/** @param filters column -> value, ANDed; {@code null} values are ignored */
	<V> List<V> list(CurrentUser user, StructureTable<V> table, Map<String, Object> filters, String orderBy) {
		MapSqlParameterSource params = readScope(user, table);
		StringBuilder sql = new StringBuilder(select(table, params));
		filters.forEach((column, value) -> {
			if (value != null) {
				sql.append(" AND ").append(column).append(" = :f_").append(column);
				params.addValue("f_" + column, value);
			}
		});
		return jdbc.query(sql + " ORDER BY " + orderBy, params, DataClassRowMapper.newInstance(table.view()));
	}

	<V> V get(CurrentUser user, StructureTable<V> table, UUID id) {
		return find(table, readScope(user, table), id);
	}

	@Transactional
	<V> V create(CurrentUser user, StructureTable<V> table, Structure.Columns request) {
		UUID school = requireAdmin(user);
		Map<String, Object> values = request.columns();
		table.rules().check(school, null, values);
		String columns = String.join(", ", values.keySet());
		String params = values.keySet().stream().map(c -> ":" + c).collect(Collectors.joining(", "));
		UUID id = translate(table, () -> jdbc.queryForObject(
				"INSERT INTO " + table.table() + " (school_id, " + columns + ") VALUES (:school, " + params + ") RETURNING id",
				new MapSqlParameterSource(values).addValue("school", school), UUID.class));
		V after = find(table, adminScope(school), id);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, table.table(), id.toString(), null, after));
		return after;
	}

	@Transactional
	<V> V update(CurrentUser user, StructureTable<V> table, UUID id, Structure.Columns request) {
		UUID school = requireAdmin(user);
		V before = find(table, adminScope(school), id);
		Map<String, Object> values = request.columns();
		table.rules().check(school, id, values);
		String assignments = values.keySet().stream().map(c -> c + " = :" + c).collect(Collectors.joining(", "));
		translate(table, () -> jdbc.update(
				"UPDATE " + table.table() + " SET " + assignments + " WHERE id = :id AND school_id = :school",
				new MapSqlParameterSource(values).addValue("id", id).addValue("school", school)));
		V after = find(table, adminScope(school), id);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, table.table(), id.toString(), before, after));
		return after;
	}

	/** A row still referenced elsewhere (a class with students, a term with holidays, ...) is not deleted: 409. */
	@Transactional
	<V> void delete(CurrentUser user, StructureTable<V> table, UUID id) {
		UUID school = requireAdmin(user);
		V before = find(table, adminScope(school), id);
		translate(table, () -> jdbc.update("DELETE FROM " + table.table() + " WHERE id = :id AND school_id = :school",
				new MapSqlParameterSource("id", id).addValue("school", school)));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.DELETE, table.table(), id.toString(), before, null));
	}

	private <V> V find(StructureTable<V> table, MapSqlParameterSource scope, UUID id) {
		return jdbc.query(select(table, scope) + " AND id = :id", scope.addValue("id", id),
				DataClassRowMapper.newInstance(table.view()))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
	}

	private static String select(StructureTable<?> table, MapSqlParameterSource scope) {
		String sql = "SELECT " + table.columns() + " FROM " + table.table() + " WHERE school_id = :school";
		if (table.scope() != null && !(Boolean) scope.getValue("all")) {
			sql += " AND " + table.scope();
		}
		return sql;
	}

	private static MapSqlParameterSource readScope(CurrentUser user, StructureTable<?> table) {
		if (user.schoolId() == null || !user.hasAny(WHOLE_SCHOOL) && !user.hasAny(SCOPED)) {
			throw ApiException.forbidden();
		}
		Set<UUID> ids = user.classesAs(SCOPED);
		return new MapSqlParameterSource("school", user.schoolId()).addValue("all", user.hasAny(WHOLE_SCHOOL))
			// IN () is invalid SQL; a random id matches nothing
			.addValue("ids", ids.isEmpty() ? Set.of(UUID.randomUUID()) : ids);
	}

	private static MapSqlParameterSource adminScope(UUID school) {
		return new MapSqlParameterSource("school", school).addValue("all", true);
	}

	private static UUID requireAdmin(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	/** Database constraints are the last line of defence; their violations become 409s with a code. */
	private <T> T translate(StructureTable<?> table, Supplier<T> statement) {
		return guard.run(statement, state -> switch (state) {
			case ConstraintGuard.UNIQUE -> ApiException.conflict(table.duplicateCode());
			case ConstraintGuard.EXCLUSION -> ApiException.conflict("term.overlap"); // the only one (V1)
			case ConstraintGuard.FOREIGN_KEY -> ApiException.conflict("in_use");
			default -> null;
		});
	}

}
