package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.GuardianPhoneChanged;
import com.schoollink.people.internal.StudentAdmin.GuardianRequest;
import com.schoollink.people.internal.StudentAdmin.StudentView;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.jdbc.ConstraintGuard;
import com.schoollink.shared.phone.PhoneNumbers;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.schoollink.shared.security.CurrentUser;

/**
 * D43: the admin keeps the guardians up to date: name, language and phone, and which guardians a
 * student has. Admin only, in the admin's own school (NFR-8).
 * <p>
 * A new phone is where the next SMS goes. When the guardian has an app account, the account signs
 * in with the new number from then on and every session ends (D24): the old number may be
 * somebody else's now. A student always keeps at least one guardian, and exactly one primary.
 * Audited with ids and the names of the fields that changed, never a name or a phone (NFR-9, NFR-11).
 */
@Service
class GuardianAdmin {

	record GuardianDetails(UUID id, String name, String phone, String language, boolean hasAccount, int children) {
	}

	record GuardianUpdate(@NotBlank @Size(max = 120) String name, @NotNull @Pattern(regexp = "FR|EN") String language,
			@NotBlank String phone) {
	}

	record LinkUpdate(@NotNull @Pattern(regexp = "MOTHER|FATHER|GUARDIAN|OTHER") String relation, boolean primary) {
	}

	private record Stored(String name, String phone, String language, UUID userId) {
	}

	private record Link(UUID guardianId, boolean primary) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final StudentAdmin students;

	private final AuditLog audit;

	private final ConstraintGuard guard;

	private final ApplicationEventPublisher events;

	GuardianAdmin(NamedParameterJdbcTemplate jdbc, StudentAdmin students, AuditLog audit, ConstraintGuard guard,
			ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.students = students;
		this.audit = audit;
		this.guard = guard;
		this.events = events;
	}

	@Transactional
	GuardianDetails update(CurrentUser user, UUID id, GuardianUpdate request) {
		UUID school = students.requireAdmin(user);
		MapSqlParameterSource p = school(school).addValue("id", id);
		Stored before = jdbc.query("""
				SELECT name, phone, language, user_id FROM guardian WHERE id = :id AND school_id = :school FOR UPDATE""", p,
				(rs, n) -> new Stored(rs.getString("name"), rs.getString("phone"), rs.getString("language"),
						rs.getObject("user_id", UUID.class)))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		String phone = PhoneNumbers.normalize(request.phone())
			.orElseThrow(() -> ApiException.invalid("validation.phone", "phone"));
		String name = request.name().trim();
		boolean phoneChanged = !phone.equals(before.phone());
		if (phoneChanged) {
			requireFreePhone(school, id, before.userId(), phone);
		}
		p.addValue("name", name).addValue("phone", phone).addValue("language", request.language());
		guard.run(() -> jdbc.update("""
				UPDATE guardian SET name = :name, phone = :phone, language = :language
				WHERE id = :id AND school_id = :school""", p), GuardianAdmin::phoneTaken);
		if (before.userId() != null) {
			// the account signs in with the guardian's phone (D24) and reads the app in their language
			p.addValue("user", before.userId()).addValue("bump", phoneChanged ? 1 : 0);
			guard.run(() -> jdbc.update("""
					UPDATE app_user SET phone = :phone, language = :language,
					                    credentials_version = credentials_version + :bump
					WHERE id = :user AND school_id = :school""", p), GuardianAdmin::phoneTaken);
		}
		if (phoneChanged) {
			events.publishEvent(new GuardianPhoneChanged(school, id, before.userId()));
		}
		List<String> changed = new ArrayList<>();
		if (!name.equals(before.name())) {
			changed.add("name");
		}
		if (!request.language().equals(before.language())) {
			changed.add("language");
		}
		if (phoneChanged) {
			changed.add("phone");
		}
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "guardian", id.toString(), null,
				Map.of("changed", changed, "sessionsEnded", phoneChanged && before.userId() != null)));
		int children = jdbc.queryForObject(
				"SELECT count(*) FROM student_guardian WHERE guardian_id = :id AND school_id = :school", p, Integer.class);
		return new GuardianDetails(id, name, phone, request.language(), before.userId() != null, children);
	}

	/** A phone already known in the school is that guardian, linked to this student too. */
	@Transactional
	StudentView add(CurrentUser user, UUID studentId, GuardianRequest request) {
		UUID school = students.requireAdmin(user);
		List<Link> links = links(school, studentId);
		GuardianRequest guardian = StudentAdmin.normalized(List.of(request)).get(0);
		UUID guardianId = students.guardianFor(school, guardian);
		if (links.stream().anyMatch(link -> link.guardianId().equals(guardianId))) {
			throw ApiException.conflict("students.guardian_linked");
		}
		boolean primary = guardian.primary() || links.isEmpty();
		MapSqlParameterSource p = link(school, studentId, guardianId);
		if (primary) {
			jdbc.update("UPDATE student_guardian SET is_primary = false WHERE student_id = :student AND school_id = :school",
					p);
		}
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (:school, :student, :guardian, :relation, :primary)""",
				p.addValue("relation", guardian.relation()).addValue("primary", primary));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "student_guardian",
				studentId + ":" + guardianId, null,
				Map.of("studentId", studentId, "guardianId", guardianId, "relation", guardian.relation(), "primary",
						primary)));
		return students.view(school, studentId);
	}

	@Transactional
	StudentView updateLink(CurrentUser user, UUID studentId, UUID guardianId, LinkUpdate request) {
		UUID school = students.requireAdmin(user);
		Link current = link(links(school, studentId), guardianId);
		if (current.primary() && !request.primary()) {
			// name the new primary instead: a student always has one
			throw ApiException.invalid("students.primary_required", "primary");
		}
		MapSqlParameterSource p = link(school, studentId, guardianId);
		if (request.primary() && !current.primary()) {
			jdbc.update("UPDATE student_guardian SET is_primary = false WHERE student_id = :student AND school_id = :school",
					p);
		}
		jdbc.update("""
				UPDATE student_guardian SET relation = :relation, is_primary = :primary
				WHERE student_id = :student AND guardian_id = :guardian AND school_id = :school""",
				p.addValue("relation", request.relation()).addValue("primary", request.primary()));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "student_guardian",
				studentId + ":" + guardianId, null, Map.of("studentId", studentId, "guardianId", guardianId, "relation",
						request.relation(), "primary", request.primary())));
		return students.view(school, studentId);
	}

	/**
	 * Removes the link only: the guardian stays, with their other children. Never the last guardian.
	 * When the primary goes, {@code newPrimary} becomes it; with one guardian left, that one does.
	 */
	@Transactional
	StudentView remove(CurrentUser user, UUID studentId, UUID guardianId, UUID newPrimary) {
		UUID school = students.requireAdmin(user);
		List<Link> links = links(school, studentId);
		Link current = link(links, guardianId);
		if (links.size() == 1) {
			throw ApiException.conflict("students.last_guardian");
		}
		MapSqlParameterSource p = link(school, studentId, guardianId);
		UUID promoted = null;
		if (current.primary()) {
			List<UUID> others = links.stream().map(Link::guardianId).filter(g -> !g.equals(guardianId)).toList();
			promoted = others.size() == 1 ? others.get(0) : newPrimary;
			if (promoted == null || !others.contains(promoted)) {
				throw ApiException.invalid("students.primary_required", "primary");
			}
		}
		jdbc.update("""
				DELETE FROM student_guardian
				WHERE student_id = :student AND guardian_id = :guardian AND school_id = :school""", p);
		if (promoted != null) {
			jdbc.update("""
					UPDATE student_guardian SET is_primary = true
					WHERE student_id = :student AND guardian_id = :promoted AND school_id = :school""",
					p.addValue("promoted", promoted));
		}
		audit.record(new AuditEntry(school, user.userId(), AuditAction.DELETE, "student_guardian",
				studentId + ":" + guardianId, Map.of("studentId", studentId, "guardianId", guardianId), null));
		return students.view(school, studentId);
	}

	/** The student's guardians, locked; 404 for another school's student (NFR-8, D17). */
	private List<Link> links(UUID school, UUID studentId) {
		MapSqlParameterSource p = school(school).addValue("student", studentId);
		if (jdbc.queryForList("SELECT id FROM student WHERE id = :student AND school_id = :school FOR UPDATE", p,
				UUID.class)
			.isEmpty()) {
			throw ApiException.notFound("not_found");
		}
		return jdbc.query("""
				SELECT guardian_id, is_primary FROM student_guardian
				WHERE student_id = :student AND school_id = :school""", p,
				(rs, n) -> new Link(rs.getObject("guardian_id", UUID.class), rs.getBoolean("is_primary")));
	}

	private static Link link(List<Link> links, UUID guardianId) {
		return links.stream()
			.filter(link -> link.guardianId().equals(guardianId))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
	}

	/** D15: one guardian per phone in a school. D21: a staff member's phone is their own sign-in. */
	private void requireFreePhone(UUID school, UUID guardianId, UUID userId, String phone) {
		MapSqlParameterSource p = school(school).addValue("id", guardianId).addValue("phone", phone);
		if (!jdbc.queryForList("SELECT id FROM guardian WHERE school_id = :school AND phone = :phone AND id <> :id", p,
				UUID.class)
			.isEmpty()) {
			throw ApiException.conflict("guardians.phone_taken");
		}
		List<UUID> accounts = jdbc.queryForList("SELECT id FROM app_user WHERE school_id = :school AND phone = :phone", p,
				UUID.class);
		if (accounts.stream().anyMatch(account -> !account.equals(userId))) {
			throw ApiException.conflict("guardians.phone_staff");
		}
	}

	private static ApiException phoneTaken(String state) {
		return ConstraintGuard.UNIQUE.equals(state) ? ApiException.conflict("guardians.phone_taken") : null;
	}

	private static MapSqlParameterSource link(UUID school, UUID studentId, UUID guardianId) {
		return school(school).addValue("student", studentId).addValue("guardian", guardianId);
	}

	private static MapSqlParameterSource school(UUID school) {
		return new MapSqlParameterSource("school", school);
	}

}
