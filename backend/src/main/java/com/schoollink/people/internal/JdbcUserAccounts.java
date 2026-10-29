package com.schoollink.people.internal;

import com.schoollink.people.ScopeAssignments;
import com.schoollink.people.UserAccount;
import com.schoollink.people.UserAccounts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserAccounts implements UserAccounts {

	private static final String SELECT = """
			SELECT u.id, u.school_id, u.password_hash, u.language, u.status,
			       array_remove(array_agg(DISTINCT r.role), NULL) AS roles
			FROM app_user u
			LEFT JOIN role_assignment r ON r.user_id = u.id
			""";

	private final JdbcTemplate jdbc;

	JdbcUserAccounts(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
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

	private Set<UUID> ids(String sql, Object... args) {
		return Set.copyOf(jdbc.queryForList(sql, UUID.class, args));
	}

	private static UserAccount map(ResultSet rs, int row) throws SQLException {
		String[] roles = (String[]) rs.getArray("roles").getArray();
		Set<String> roleSet = Arrays.stream(roles).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
		return new UserAccount(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
				rs.getString("password_hash"), rs.getString("language"), rs.getString("status"), roleSet);
	}

}
