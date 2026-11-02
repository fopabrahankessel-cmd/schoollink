package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.ScopeAssignments;
import com.schoollink.people.UserAccount;
import com.schoollink.people.UserAccounts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcUserAccounts implements UserAccounts {

	private static final String SELECT = """
			SELECT u.id, u.school_id, u.password_hash, u.language, u.status, u.password_change_required,
			       u.credentials_version,
			       array_remove(array_agg(DISTINCT r.role), NULL) AS roles
			FROM app_user u
			LEFT JOIN role_assignment r ON r.user_id = u.id
			""";

	private final JdbcTemplate jdbc;

	private final AuditLog audit;

	JdbcUserAccounts(JdbcTemplate jdbc, AuditLog audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Override
	public List<UserAccount> findByLogin(String login, UUID schoolId) {
		String column = login.contains("@") ? "u.email" : "u.phone";
		return jdbc.query(SELECT + " WHERE " + column + " = ? AND (?::uuid IS NULL OR u.school_id = ?) GROUP BY u.id",
				JdbcUserAccounts::map, login, schoolId, schoolId);
	}

	@Override
	public Optional<UserAccount> findById(UUID id) {
		return jdbc.query(SELECT + " WHERE u.id = ? GROUP BY u.id", JdbcUserAccounts::map, id).stream().findFirst();
	}

	@Override
	public Optional<String> fullName(UUID schoolId, UUID userId) {
		return jdbc.queryForList("SELECT first_name || ' ' || last_name FROM app_user WHERE id = ? AND school_id = ?",
				String.class, userId, schoolId)
			.stream()
			.findFirst();
	}

	@Override
	public ScopeAssignments assignmentsOf(UUID userId, UUID schoolId) {
		return new ScopeAssignments(
				ids("SELECT class_id FROM teaching_assignment WHERE teacher_id = ? AND school_id = ?", userId, schoolId),
				ids("SELECT block_id FROM role_assignment WHERE user_id = ? AND school_id = ? AND role = 'DM'", userId,
						schoolId),
				ids("SELECT block_id FROM role_assignment WHERE user_id = ? AND school_id = ? AND role = 'ASSISTANT_DM'",
						userId, schoolId),
				ids("""
						SELECT sg.student_id FROM guardian g
						JOIN student_guardian sg ON sg.guardian_id = g.id AND sg.school_id = g.school_id
						WHERE g.user_id = ? AND g.school_id = ?""", userId, schoolId));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void changePassword(UUID userId, String passwordHash) {
		UUID school = jdbc.queryForObject(
				"""
				UPDATE app_user
				SET password_hash = ?, password_change_required = false, credentials_version = credentials_version + 1
				WHERE id = ? RETURNING school_id""",
				UUID.class, passwordHash, userId);
		audit.record(new AuditEntry(school, userId, AuditAction.UPDATE, "app_user", userId.toString(), null,
				Map.of("passwordChanged", true)));
	}

	private Set<UUID> ids(String sql, Object... args) {
		return Set.copyOf(jdbc.queryForList(sql, UUID.class, args));
	}

	private static UserAccount map(ResultSet rs, int row) throws SQLException {
		String[] roles = (String[]) rs.getArray("roles").getArray();
		Set<String> roleSet = Arrays.stream(roles).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
		return new UserAccount(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
				rs.getString("password_hash"), rs.getString("language"), rs.getString("status"),
				rs.getBoolean("password_change_required"), rs.getInt("credentials_version"), roleSet);
	}

}
