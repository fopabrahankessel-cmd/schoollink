package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.ParentAccounts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-P1: the guardian's account, made when the SMS one-time code is right; D21: staff who are parents. */
@Repository
class JdbcParentAccounts implements ParentAccounts {

	private record Guardian(String name, String phone, String language, UUID userId) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final AuditLog audit;

	JdbcParentAccounts(NamedParameterJdbcTemplate jdbc, AuditLog audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<UUID> activate(UUID schoolId, UUID guardianId) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("guardian", guardianId);
		// locked: of two concurrent first sign-ins, the second waits and then finds the account.
		// Everything is checked before anything is written, so a refusal changes nothing.
		Guardian guardian = jdbc.queryForObject("""
				SELECT name, phone, language, user_id FROM guardian WHERE id = :guardian AND school_id = :school FOR UPDATE""",
				p, (rs, n) -> new Guardian(rs.getString("name"), rs.getString("phone"), rs.getString("language"),
						rs.getObject("user_id", UUID.class)));
		if (guardian.userId() != null) {
			return Optional.of(guardian.userId());
		}
		p.addValue("phone", guardian.phone());
		if (!jdbc.queryForList("SELECT id FROM app_user WHERE school_id = :school AND phone = :phone FOR UPDATE", p, UUID.class)
			.isEmpty()) {
			return Optional.empty();
		}
		String[] names = splitName(guardian.name());
		UUID userId = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, language)
				VALUES (:school, :first, :last, :phone, :language) RETURNING id""",
				p.addValue("first", names[0]).addValue("last", names[1]).addValue("language", guardian.language()),
				UUID.class);
		// NFR-11, NFR-9: no phone in the audit
		audit.record(new AuditEntry(schoolId, userId, AuditAction.CREATE, "app_user", userId.toString(), null,
				Map.of("role", "PARENT", "guardianId", guardianId, "language", guardian.language())));
		link(schoolId, guardianId, userId);
		return Optional.of(userId);
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean linkStaff(UUID schoolId, UUID userId) {
		List<UUID> guardians = jdbc.queryForList("""
				SELECT g.id FROM guardian g
				JOIN app_user u ON u.school_id = g.school_id AND u.phone = g.phone
				WHERE u.id = :user AND g.school_id = :school AND g.user_id IS NULL FOR UPDATE OF g""",
				new MapSqlParameterSource("school", schoolId).addValue("user", userId), UUID.class);
		if (guardians.isEmpty()) {
			return false;
		}
		link(schoolId, guardians.getFirst(), userId);
		return true;
	}

	private void link(UUID schoolId, UUID guardianId, UUID userId) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("guardian", guardianId)
			.addValue("user", userId);
		jdbc.update("""
				INSERT INTO role_assignment (school_id, user_id, role) VALUES (:school, :user, 'PARENT')
				ON CONFLICT DO NOTHING""", p);
		jdbc.update("UPDATE guardian SET user_id = :user WHERE id = :guardian AND school_id = :school", p);
		audit.record(new AuditEntry(schoolId, userId, AuditAction.UPDATE, "guardian", guardianId.toString(),
				Map.of("account", false), Map.of("account", true, "userId", userId)));
	}

	/** "Mbarga Paul Éric" -> first "Mbarga", last "Paul Éric"; one word -> first only. */
	static String[] splitName(String name) {
		String trimmed = name.strip();
		int space = trimmed.indexOf(' ');
		return space < 0 ? new String[] { trimmed, "" }
				: new String[] { trimmed.substring(0, space), trimmed.substring(space + 1).strip() };
	}

}
