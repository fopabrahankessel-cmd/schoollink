package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.ParentAccounts;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** UC-P1 step 2: the guardian's account, made when the SMS one-time code is right. */
@Repository
class JdbcParentAccounts implements ParentAccounts {

	private record Guardian(String name, String phone, String language, UUID userId) {
	}

	private record Account(UUID id, String status) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final AuditLog audit;

	private final Clock clock;

	JdbcParentAccounts(NamedParameterJdbcTemplate jdbc, AuditLog audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<UUID> activate(UUID schoolId, UUID invitationId) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("invitation", invitationId)
			.addValue("now", Timestamp.from(clock.instant()));
		// locked: of two concurrent activations, the second waits and then finds the code used.
		// Everything is checked before anything is written, so a refusal changes nothing.
		List<UUID> live = jdbc.queryForList("""
				SELECT guardian_id FROM invitation_code
				WHERE id = :invitation AND school_id = :school AND used_at IS NULL AND revoked_at IS NULL AND expires_at > :now
				FOR UPDATE""", p, UUID.class);
		if (live.isEmpty()) {
			return Optional.empty();
		}
		UUID guardianId = live.getFirst();
		p.addValue("guardian", guardianId);
		Guardian guardian = jdbc.queryForObject("""
				SELECT name, phone, language, user_id FROM guardian WHERE id = :guardian AND school_id = :school FOR UPDATE""",
				p, (rs, n) -> new Guardian(rs.getString("name"), rs.getString("phone"), rs.getString("language"),
						rs.getObject("user_id", UUID.class)));
		if (guardian.userId() != null) {
			// D7: codes go to guardians without an account; one linked meanwhile keeps its account,
			// unless it was disabled (D18): a code never brings a disabled account back
			String status = jdbc.queryForObject("""
					SELECT status FROM app_user WHERE id = :user AND school_id = :school FOR UPDATE""",
					p.addValue("user", guardian.userId()), String.class);
			if ("DISABLED".equals(status)) {
				return Optional.empty();
			}
			useInvitation(p);
			return Optional.of(guardian.userId());
		}
		p.addValue("phone", guardian.phone());
		Optional<Account> existing = jdbc.query("""
				SELECT id, status FROM app_user WHERE school_id = :school AND phone = :phone FOR UPDATE""", p,
				(rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("status"))).stream().findFirst();
		if (existing.isPresent() && "DISABLED".equals(existing.get().status())) {
			return Optional.empty();
		}
		useInvitation(p);
		UUID userId;
		if (existing.isPresent()) {
			// D21: a staff member who is also a parent keeps one account, now with the PARENT role
			userId = existing.get().id();
		}
		else {
			String[] names = splitName(guardian.name());
			userId = jdbc.queryForObject("""
					INSERT INTO app_user (school_id, first_name, last_name, phone, language)
					VALUES (:school, :first, :last, :phone, :language) RETURNING id""",
					p.addValue("first", names[0]).addValue("last", names[1]).addValue("language", guardian.language()),
					UUID.class);
			// NFR-11, NFR-9: no phone in the audit
			audit.record(new AuditEntry(schoolId, userId, AuditAction.CREATE, "app_user", userId.toString(), null,
					Map.of("role", "PARENT", "guardianId", guardianId, "language", guardian.language())));
		}
		p.addValue("user", userId);
		jdbc.update("""
				INSERT INTO role_assignment (school_id, user_id, role) VALUES (:school, :user, 'PARENT')
				ON CONFLICT DO NOTHING""", p);
		jdbc.update("UPDATE guardian SET user_id = :user WHERE id = :guardian AND school_id = :school", p);
		audit.record(new AuditEntry(schoolId, userId, AuditAction.UPDATE, "guardian", guardianId.toString(),
				Map.of("account", false), Map.of("account", true, "userId", userId, "invitationId", invitationId)));
		return Optional.of(userId);
	}

	private void useInvitation(MapSqlParameterSource p) {
		jdbc.update("UPDATE invitation_code SET used_at = :now WHERE id = :invitation AND school_id = :school", p);
	}

	/** "Mbarga Paul Éric" -> first "Mbarga", last "Paul Éric"; one word -> first only. */
	static String[] splitName(String name) {
		String trimmed = name.strip();
		int space = trimmed.indexOf(' ');
		return space < 0 ? new String[] { trimmed, "" }
				: new String[] { trimmed.substring(0, space), trimmed.substring(space + 1).strip() };
	}

}
