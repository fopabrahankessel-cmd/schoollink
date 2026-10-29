package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.StaffCredentialsReset;
import com.schoollink.people.internal.Staff.CreatedStaff;
import com.schoollink.people.internal.Staff.RoleRequest;
import com.schoollink.people.internal.Staff.RoleView;
import com.schoollink.people.internal.Staff.StaffRequest;
import com.schoollink.people.internal.Staff.StaffSummary;
import com.schoollink.people.internal.Staff.StaffView;
import com.schoollink.people.internal.Staff.TeachingRequest;
import com.schoollink.people.internal.Staff.TeachingView;
import com.schoollink.people.internal.Staff.TemporaryPassword;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.jdbc.ConstraintGuard;
import com.schoollink.shared.phone.PhoneNumbers;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-A2: the admin creates staff accounts and assigns roles, blocks (D5) and classes. "Staff" are
 * the school's users who sign in with a password and are not admins; parents (no password) and
 * the researcher (no school) are never reached here, and admin accounts are not managed through
 * this API. Every change is audited (NFR-11). Phone numbers never appear in logs or URLs (NFR-9).
 */
@Service
class StaffService {

	/** The roles UC-A2 lets the admin give. */
	private static final Set<Role> ASSIGNABLE = EnumSet.of(Role.TEACHER, Role.ASSISTANT_DM, Role.DM, Role.COUNSELLOR);

	private static final Set<Role> BLOCK_ROLES = EnumSet.of(Role.DM, Role.ASSISTANT_DM);

	private static final Role[] SCOPED = { Role.TEACHER, Role.ASSISTANT_DM, Role.DM };

	/** Staff of :school, as a condition on {@code u}. */
	private static final String IS_STAFF = """
			u.school_id = :school AND u.password_hash IS NOT NULL
			AND NOT EXISTS (SELECT 1 FROM role_assignment a WHERE a.user_id = u.id AND a.role = 'ADMIN')""";

	private final NamedParameterJdbcTemplate jdbc;

	private final AuditLog audit;

	private final ConstraintGuard guard;

	private final PasswordEncoder passwords;

	private final TemporaryPasswords temporaryPasswords;

	private final ApplicationEventPublisher events;

	private final SchoolClasses schoolClasses;

	StaffService(NamedParameterJdbcTemplate jdbc, AuditLog audit, ConstraintGuard guard, PasswordEncoder passwords,
			TemporaryPasswords temporaryPasswords, ApplicationEventPublisher events, SchoolClasses schoolClasses) {
		this.schoolClasses = schoolClasses;
		this.jdbc = jdbc;
		this.audit = audit;
		this.guard = guard;
		this.passwords = passwords;
		this.temporaryPasswords = temporaryPasswords;
		this.events = events;
	}

	// Reads (SPEC §3 "School structure, accounts" row)

	/**
	 * The admin gets every staff member in full. The counsellor gets the whole school, and a
	 * teacher, assistant DM or DM the colleagues working on the classes in their scope, as
	 * {@link StaffSummary} without contact details. Parent and researcher get 403.
	 */
	List<?> list(CurrentUser user) {
		if (user.schoolId() != null && user.has(Role.ADMIN)) {
			return views(user.schoolId(), null);
		}
		if (user.schoolId() == null || !user.has(Role.COUNSELLOR) && !user.hasAny(SCOPED)) {
			throw ApiException.forbidden();
		}
		MapSqlParameterSource p = school(user.schoolId());
		String sql = "SELECT u.id, u.first_name, u.last_name FROM app_user u WHERE " + IS_STAFF;
		if (!user.has(Role.COUNSELLOR)) {
			Set<UUID> classes = user.classesAs(SCOPED);
			Set<UUID> blocks = schoolClasses.blocksOf(user.schoolId(), classes);
			// IN () is invalid SQL; a random id matches nothing
			p.addValue("classes", classes.isEmpty() ? Set.of(UUID.randomUUID()) : classes)
				.addValue("blocks", blocks.isEmpty() ? Set.of(UUID.randomUUID()) : blocks);
			sql += """
					 AND u.id IN (
					   SELECT t.teacher_id FROM teaching_assignment t WHERE t.school_id = :school AND t.class_id IN (:classes)
					   UNION
					   SELECT r.user_id FROM role_assignment r WHERE r.school_id = :school AND r.block_id IN (:blocks))""";
		}
		Map<UUID, List<RoleView>> roles = roles(user.schoolId(), null);
		return jdbc.query(sql + " ORDER BY u.last_name, u.first_name", p,
				(rs, n) -> {
					UUID id = rs.getObject("id", UUID.class);
					return new StaffSummary(id, rs.getString("first_name"), rs.getString("last_name"),
							roles.getOrDefault(id, List.of()).stream().map(r -> r.role().name()).distinct().sorted().toList());
				});
	}

	StaffView get(CurrentUser user, UUID id) {
		return view(requireAdmin(user), id);
	}

	// Accounts

	/** D18: the account starts with a temporary password that must be changed at first sign-in. */
	@Transactional
	CreatedStaff create(CurrentUser user, StaffRequest request) {
		UUID school = requireAdmin(user);
		Contact contact = contact(school, null, request);
		String language = request.language() != null ? request.language() : schoolClasses.language(school);
		String temporary = temporaryPasswords.generate();
		UUID id = guard.run(() -> jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, email, language, password_hash,
				                      password_change_required)
				VALUES (:school, :first, :last, :phone, :email, :language, :hash, true) RETURNING id""",
				names(school, request).addValue("phone", contact.phone())
					.addValue("email", contact.email())
					.addValue("language", language)
					.addValue("hash", passwords.encode(temporary)),
				UUID.class), StaffService::contactTaken);
		StaffView after = view(school, id);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "app_user", id.toString(), null, after));
		return new CreatedStaff(after, temporary);
	}

	@Transactional
	StaffView update(CurrentUser user, UUID id, StaffRequest request) {
		UUID school = requireAdmin(user);
		StaffView before = view(school, id);
		Contact contact = contact(school, id, request);
		guard.run(() -> jdbc.update("""
				UPDATE app_user SET first_name = :first, last_name = :last, phone = :phone, email = :email,
				                    language = COALESCE(:language, language)
				WHERE id = :id AND school_id = :school""",
				names(school, request).addValue("id", id)
					.addValue("phone", contact.phone())
					.addValue("email", contact.email())
					.addValue("language", request.language())),
				StaffService::contactTaken);
		StaffView after = view(school, id);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "app_user", id.toString(), before, after));
		return after;
	}

	/** Disabling ends every session at once (D18); the account and its history stay. */
	@Transactional
	StaffView setStatus(CurrentUser user, UUID id, String status) {
		UUID school = requireAdmin(user);
		StaffView before = view(school, id);
		jdbc.update("""
				UPDATE app_user SET status = :status,
				    credentials_version = credentials_version + CASE WHEN :status = 'DISABLED' THEN 1 ELSE 0 END
				WHERE id = :id AND school_id = :school""",
				school(school).addValue("id", id).addValue("status", status));
		if ("DISABLED".equals(status)) {
			events.publishEvent(new StaffCredentialsReset(id));
		}
		StaffView after = view(school, id);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "app_user", id.toString(), before, after));
		return after;
	}

	/** D18: a new temporary password, shown once; the user's sessions end. */
	@Transactional
	TemporaryPassword resetPassword(CurrentUser user, UUID id) {
		UUID school = requireAdmin(user);
		view(school, id);
		String temporary = temporaryPasswords.generate();
		jdbc.update("""
				UPDATE app_user SET password_hash = :hash, password_change_required = true,
				    credentials_version = credentials_version + 1
				WHERE id = :id AND school_id = :school""",
				school(school).addValue("id", id).addValue("hash", passwords.encode(temporary)));
		events.publishEvent(new StaffCredentialsReset(id));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "app_user", id.toString(), null,
				Map.of("passwordReset", true)));
		return new TemporaryPassword(temporary);
	}

	// Roles and blocks (D5)

	@Transactional
	RoleView addRole(CurrentUser user, UUID staffId, RoleRequest request) {
		UUID school = requireAdmin(user);
		view(school, staffId);
		Role role = request.role();
		if (!ASSIGNABLE.contains(role)) {
			throw ApiException.invalid("staff.role_not_allowed", "role");
		}
		UUID block = request.blockId();
		if (BLOCK_ROLES.contains(role)) {
			if (block == null) {
				throw ApiException.invalid("validation.not_null", "blockId");
			}
			if (!schoolClasses.blockExists(school, block)) {
				throw ApiException.invalid("reference.unknown", "blockId");
			}
			// D5: one DM per block, so "the DM in charge" of a student is unique (BR-11, BR-18)
			if (role == Role.DM && exists("""
					SELECT 1 FROM role_assignment WHERE block_id = :block AND role = 'DM' AND user_id <> :user""",
					school(school).addValue("block", block).addValue("user", staffId))) {
				throw ApiException.conflict("staff.block_has_dm");
			}
		}
		else if (block != null) {
			throw ApiException.invalid("staff.block_not_expected", "blockId");
		}
		UUID id = guard.run(() -> jdbc.queryForObject("""
				INSERT INTO role_assignment (school_id, user_id, role, block_id)
				VALUES (:school, :user, :role, :block) RETURNING id""",
				school(school).addValue("user", staffId).addValue("role", role.name()).addValue("block", block),
				UUID.class), state -> ConstraintGuard.UNIQUE.equals(state) ? ApiException.conflict("staff.role_duplicate") : null);
		RoleView after = new RoleView(id, role, block);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "role_assignment", id.toString(), null,
				Map.of("userId", staffId, "assignment", after)));
		return after;
	}

	@Transactional
	void removeRole(CurrentUser user, UUID staffId, UUID roleId) {
		UUID school = requireAdmin(user);
		view(school, staffId);
		RoleView before = roles(school, staffId).getOrDefault(staffId, List.of())
			.stream()
			.filter(r -> r.id().equals(roleId))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		if (before.role() == Role.TEACHER && exists(
				"SELECT 1 FROM teaching_assignment WHERE teacher_id = :user AND school_id = :school",
				school(school).addValue("user", staffId))) {
			throw ApiException.conflict("staff.teaching_assigned");
		}
		jdbc.update("DELETE FROM role_assignment WHERE id = :id AND school_id = :school",
				school(school).addValue("id", roleId));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.DELETE, "role_assignment", roleId.toString(),
				Map.of("userId", staffId, "assignment", before), null));
	}

	// Classes taught (BR-1: only an assigned teacher opens a session)

	@Transactional
	TeachingView addTeaching(CurrentUser user, UUID staffId, TeachingRequest request) {
		UUID school = requireAdmin(user);
		StaffView staff = view(school, staffId);
		if (staff.roles().stream().noneMatch(r -> r.role() == Role.TEACHER)) {
			throw ApiException.conflict("staff.not_a_teacher");
		}
		if (!schoolClasses.classExists(school, request.classId())) {
			throw ApiException.invalid("reference.unknown", "classId");
		}
		String subject = request.subject().strip();
		UUID id = guard.run(() -> jdbc.queryForObject("""
				INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject)
				VALUES (:school, :user, :class, :subject) RETURNING id""",
				school(school).addValue("user", staffId).addValue("class", request.classId()).addValue("subject", subject),
				UUID.class),
				state -> ConstraintGuard.UNIQUE.equals(state) ? ApiException.conflict("staff.teaching_duplicate") : null);
		TeachingView after = new TeachingView(id, request.classId(), subject);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "teaching_assignment", id.toString(),
				null, Map.of("userId", staffId, "assignment", after)));
		return after;
	}

	/** A class still in the teacher's timetable is not unassigned: 409 {@code in_use} (D1). */
	@Transactional
	void removeTeaching(CurrentUser user, UUID staffId, UUID teachingId) {
		UUID school = requireAdmin(user);
		TeachingView before = view(school, staffId).teaching()
			.stream()
			.filter(t -> t.id().equals(teachingId))
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		guard.run(() -> jdbc.update("DELETE FROM teaching_assignment WHERE id = :id AND school_id = :school",
				school(school).addValue("id", teachingId)),
				state -> ConstraintGuard.FOREIGN_KEY.equals(state) ? ApiException.conflict("in_use") : null);
		audit.record(new AuditEntry(school, user.userId(), AuditAction.DELETE, "teaching_assignment",
				teachingId.toString(), Map.of("userId", staffId, "assignment", before), null));
	}

	// Helpers

	private record Contact(String phone, String email) {
	}

	/** D15: Cameroon numbers only, stored in E.164; phone and email are each unique within the school. */
	private Contact contact(UUID school, UUID self, StaffRequest request) {
		String phone = null;
		if (request.phone() != null && !request.phone().isBlank()) {
			phone = PhoneNumbers.normalize(request.phone())
				.orElseThrow(() -> ApiException.invalid("validation.phone", "phone"));
		}
		String email = request.email() == null || request.email().isBlank() ? null
				: request.email().strip().toLowerCase(Locale.ROOT);
		if (phone == null && email == null) {
			throw ApiException.invalid("staff.contact_required", "phone");
		}
		MapSqlParameterSource p = school(school).addValue("self", self == null ? new UUID(0, 0) : self);
		if (phone != null && exists("SELECT 1 FROM app_user WHERE school_id = :school AND phone = :phone AND id <> :self",
				p.addValue("phone", phone))) {
			throw ApiException.conflict("staff.phone_taken");
		}
		if (email != null && exists("SELECT 1 FROM app_user WHERE school_id = :school AND email = :email AND id <> :self",
				p.addValue("email", email))) {
			throw ApiException.conflict("staff.email_taken");
		}
		return new Contact(phone, email);
	}

	private static ApiException contactTaken(String state) {
		return ConstraintGuard.UNIQUE.equals(state) ? ApiException.conflict("staff.contact_taken") : null;
	}

	private StaffView view(UUID school, UUID id) {
		return views(school, id).stream().findFirst().orElseThrow(() -> ApiException.notFound("not_found"));
	}

	/** @param id one staff member, or {@code null} for all */
	private List<StaffView> views(UUID school, UUID id) {
		MapSqlParameterSource p = school(school).addValue("id", id == null ? new UUID(0, 0) : id)
			.addValue("all", id == null);
		Map<UUID, List<RoleView>> roles = roles(school, id);
		Map<UUID, List<TeachingView>> teaching = jdbc.query("""
				SELECT id, teacher_id, class_id, subject FROM teaching_assignment
				WHERE school_id = :school AND (:all OR teacher_id = :id) ORDER BY subject""", p,
				(rs, n) -> Map.entry(rs.getObject("teacher_id", UUID.class),
						new TeachingView(rs.getObject("id", UUID.class), rs.getObject("class_id", UUID.class),
								rs.getString("subject"))))
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		return jdbc.query("""
				SELECT u.id, u.first_name, u.last_name, u.phone, u.email, u.language, u.status, u.password_change_required
				FROM app_user u WHERE\s""" + IS_STAFF + " AND (:all OR u.id = :id) ORDER BY u.last_name, u.first_name", p,
				(rs, n) -> {
					UUID userId = rs.getObject("id", UUID.class);
					return new StaffView(userId, rs.getString("first_name"), rs.getString("last_name"),
							rs.getString("phone"), rs.getString("email"), rs.getString("language"), rs.getString("status"),
							rs.getBoolean("password_change_required"), roles.getOrDefault(userId, List.of()),
							teaching.getOrDefault(userId, List.of()));
				});
	}

	/** @param id one user, or {@code null} for the whole school */
	private Map<UUID, List<RoleView>> roles(UUID school, UUID id) {
		MapSqlParameterSource p = school(school).addValue("id", id == null ? new UUID(0, 0) : id)
			.addValue("all", id == null);
		return jdbc.query("""
				SELECT id, user_id, role, block_id FROM role_assignment
				WHERE school_id = :school AND (:all OR user_id = :id) ORDER BY role""", p,
				(rs, n) -> Map.entry(rs.getObject("user_id", UUID.class), new RoleView(rs.getObject("id", UUID.class),
						Role.valueOf(rs.getString("role")), rs.getObject("block_id", UUID.class))))
			.stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
	}

	private boolean exists(String sql, MapSqlParameterSource params) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + sql + ")", params, Boolean.class));
	}

	private static MapSqlParameterSource school(UUID school) {
		return new MapSqlParameterSource("school", school);
	}

	private static MapSqlParameterSource names(UUID school, StaffRequest request) {
		return school(school).addValue("first", request.firstName().strip()).addValue("last", request.lastName().strip());
	}

	private static UUID requireAdmin(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

}
