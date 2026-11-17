package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.ParentPasswordResets;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.jdbc.ConstraintGuard;
import com.schoollink.shared.phone.PhoneNumbers;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * D43: the admin keeps the students up to date after the import (UC-A3): add one, correct the
 * details, move to another class (one student, or a whole class at the end of the year), mark as
 * having left the school and bring back. Admin only, in the admin's own school (NFR-8).
 * <p>
 * Nothing recorded for a student changes when they move or leave: a session keeps its own class
 * (BR-4) and records are never deleted (BR-15). Every change is audited with ids and the names of
 * the fields that changed, never with a name or a phone (NFR-9, NFR-11).
 */
@Service
class StudentAdmin {

	/** A list is a class, or a search: more than this is not a list anybody reads. */
	static final int LIST_LIMIT = 500;

	record StudentRow(UUID id, String matricule, String firstName, String lastName, UUID classId, String className,
			LocalDate leftOn, int guardians) {
	}

	/** {@code children}: the guardian's children in the school, so a change is known to touch them all. */
	record GuardianView(UUID id, String name, String phone, String language, String relation, boolean primary,
			boolean hasAccount, int children, boolean resetsLocked) {
	}

	/** The guardians: the primary first, then by name. */
	record StudentView(UUID id, String matricule, String firstName, String lastName, String sex, int birthYear,
			UUID classId, String className, LocalDate leftOn, List<GuardianView> guardians) {
	}

	/** NFR-10: the birth year only. */
	record DetailsRequest(@NotBlank @Size(max = 40) String matricule, @NotBlank @Size(max = 80) String firstName,
			@NotBlank @Size(max = 80) String lastName, @NotNull @Pattern(regexp = "M|F") String sex,
			@NotNull @Min(1980) @Max(2100) Integer birthYear) {
	}

	/** {@code language}: null for the school's. A phone already known in the school is that guardian. */
	record GuardianRequest(@NotBlank String phone, @NotBlank @Size(max = 120) String name,
			@NotNull @Pattern(regexp = "MOTHER|FATHER|GUARDIAN|OTHER") String relation,
			@Pattern(regexp = "FR|EN") String language, boolean primary) {
	}

	record NewStudentRequest(@NotBlank @Size(max = 40) String matricule, @NotBlank @Size(max = 80) String firstName,
			@NotBlank @Size(max = 80) String lastName, @NotNull @Pattern(regexp = "M|F") String sex,
			@NotNull @Min(1980) @Max(2100) Integer birthYear, @NotNull UUID classId,
			@NotEmpty @Size(max = 4) @Valid List<GuardianRequest> guardians) {

		DetailsRequest details() {
			return new DetailsRequest(matricule, firstName, lastName, sex, birthYear);
		}
	}

	record ClassRequest(@NotNull UUID classId) {
	}

	record MoveRequest(@NotEmpty @Size(max = 300) Set<UUID> studentIds, @NotNull UUID classId) {
	}

	record Moved(int moved) {
	}

	/** {@code date}: null for today at the school. */
	record LeaveRequest(LocalDate date) {
	}

	/** {@code classId}: null for the class the student was in. */
	record ReturnRequest(UUID classId) {
	}

	private record Current(UUID classId, LocalDate leftOn) {
	}

	private final ParentPasswordResets resets;

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolClasses schoolClasses;

	private final SchoolCalendar calendar;

	private final AuditLog audit;

	private final ConstraintGuard guard;

	private final Clock clock;

	StudentAdmin(NamedParameterJdbcTemplate jdbc, SchoolClasses schoolClasses, SchoolCalendar calendar, AuditLog audit,
			ConstraintGuard guard, ParentPasswordResets resets, Clock clock) {
		this.jdbc = jdbc;
		this.resets = resets;
		this.schoolClasses = schoolClasses;
		this.calendar = calendar;
		this.audit = audit;
		this.guard = guard;
		this.clock = clock;
	}

	/**
	 * The students of a class, or those whose name contains {@code query} or whose matricule starts
	 * with it; by name. Those who left the school only when {@code withLeft}. A phone is never a
	 * search term: it would be in the URL (NFR-9).
	 */
	@Transactional(readOnly = true)
	List<StudentRow> list(CurrentUser user, UUID classId, String query, boolean withLeft) {
		UUID school = requireAdmin(user);
		MapSqlParameterSource p = school(school).addValue("limit", LIST_LIMIT);
		StringBuilder where = new StringBuilder("s.school_id = :school");
		if (classId != null) {
			where.append(" AND s.class_id = :class");
			p.addValue("class", classId);
		}
		if (query != null && !query.isBlank()) {
			String like = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
			where.append(" AND (s.first_name || ' ' || s.last_name ILIKE :contains")
				.append(" OR s.last_name || ' ' || s.first_name ILIKE :contains OR s.matricule ILIKE :prefix)");
			p.addValue("contains", "%" + like + "%").addValue("prefix", like + "%");
		}
		if (!withLeft) {
			where.append(" AND s.left_on IS NULL");
		}
		record Row(UUID id, String matricule, String firstName, String lastName, UUID classId, LocalDate leftOn,
				int guardians) {
		}
		List<Row> rows = jdbc.query("""
				SELECT s.id, s.matricule, s.first_name, s.last_name, s.class_id, s.left_on,
				       (SELECT count(*) FROM student_guardian sg
				        WHERE sg.student_id = s.id AND sg.school_id = s.school_id) AS guardians
				FROM student s WHERE %s
				ORDER BY lower(s.last_name), lower(s.first_name), s.matricule LIMIT :limit""".formatted(where), p,
				(rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("matricule"), rs.getString("first_name"),
						rs.getString("last_name"), rs.getObject("class_id", UUID.class),
						rs.getObject("left_on", LocalDate.class), rs.getInt("guardians")));
		Map<UUID, String> names = schoolClasses.names(school, rows.stream().map(Row::classId).collect(Collectors.toSet()));
		return rows.stream()
			.map(r -> new StudentRow(r.id(), r.matricule(), r.firstName(), r.lastName(), r.classId(),
					names.get(r.classId()), r.leftOn(), r.guardians()))
			.toList();
	}

	@Transactional(readOnly = true)
	StudentView get(CurrentUser user, UUID id) {
		return view(requireAdmin(user), id);
	}

	/** A guardian phone already known in the school is linked, not created again (as in the import). */
	@Transactional
	StudentView create(CurrentUser user, NewStudentRequest request) {
		UUID school = requireAdmin(user);
		requireClass(school, request.classId());
		List<GuardianRequest> guardians = normalized(request.guardians());
		if (guardians.stream().filter(GuardianRequest::primary).count() != 1) {
			throw ApiException.invalid("students.primary_required", "guardians");
		}
		DetailsRequest details = trimmed(request.details());
		requireFreeMatricule(school, details.matricule(), null);
		UUID id = guard.run(() -> jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (:school, :class, :matricule, :first, :last, :sex, :birthYear) RETURNING id""",
				details(school, details).addValue("class", request.classId()), UUID.class), StudentAdmin::matriculeTaken);
		List<UUID> guardianIds = new ArrayList<>();
		for (GuardianRequest guardian : guardians) {
			UUID guardianId = guardianFor(school, guardian);
			guardianIds.add(guardianId);
			jdbc.update("""
					INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
					VALUES (:school, :student, :guardian, :relation, :primary)""",
					school(school).addValue("student", id)
						.addValue("guardian", guardianId)
						.addValue("relation", guardian.relation())
						.addValue("primary", guardian.primary()));
		}
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "student", id.toString(), null,
				Map.of("classId", request.classId(), "guardianIds", guardianIds)));
		return view(school, id);
	}

	@Transactional
	StudentView update(CurrentUser user, UUID id, DetailsRequest request) {
		UUID school = requireAdmin(user);
		StudentView before = view(school, id);
		DetailsRequest details = trimmed(request);
		requireFreeMatricule(school, details.matricule(), id);
		guard.run(() -> jdbc.update("""
				UPDATE student SET matricule = :matricule, first_name = :first, last_name = :last, sex = :sex,
				                   birth_year = :birthYear
				WHERE id = :id AND school_id = :school""", details(school, details).addValue("id", id)),
				StudentAdmin::matriculeTaken);
		List<String> changed = new ArrayList<>();
		changed(changed, "matricule", before.matricule(), details.matricule());
		changed(changed, "firstName", before.firstName(), details.firstName());
		changed(changed, "lastName", before.lastName(), details.lastName());
		changed(changed, "sex", before.sex(), details.sex());
		changed(changed, "birthYear", before.birthYear(), details.birthYear());
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "student", id.toString(), null,
				Map.of("changed", changed)));
		return view(school, id);
	}

	/** Sessions already recorded keep their class (BR-4); the next roll of the new class has the student. */
	@Transactional
	StudentView move(CurrentUser user, UUID id, UUID classId) {
		UUID school = requireAdmin(user);
		Current current = current(school, id);
		requireClass(school, classId);
		if (current.leftOn() != null) {
			throw ApiException.conflict("students.already_left");
		}
		moveAll(user, school, Map.of(id, current), classId);
		return view(school, id);
	}

	/** The end of the year: the students of a class go to their class of the next year, all or none. */
	@Transactional
	Moved move(CurrentUser user, MoveRequest request) {
		UUID school = requireAdmin(user);
		requireClass(school, request.classId());
		Map<UUID, Current> students = new LinkedHashMap<>();
		jdbc.query("""
				SELECT id, class_id, left_on FROM student WHERE school_id = :school AND id IN (:ids)
				ORDER BY id FOR UPDATE""", school(school).addValue("ids", request.studentIds()), rs -> {
			students.put(rs.getObject("id", UUID.class),
					new Current(rs.getObject("class_id", UUID.class), rs.getObject("left_on", LocalDate.class)));
		});
		if (students.size() != request.studentIds().size()) {
			throw ApiException.notFound("not_found");
		}
		if (students.values().stream().anyMatch(s -> s.leftOn() != null)) {
			throw ApiException.conflict("students.already_left");
		}
		moveAll(user, school, students, request.classId());
		return new Moved(students.size());
	}

	/**
	 * From {@code date} the student is in no roll and on no gate list, and nothing new is recorded
	 * or sent about them; what was recorded stays, and their guardians keep the history.
	 */
	@Transactional
	StudentView leave(CurrentUser user, UUID id, LocalDate date) {
		UUID school = requireAdmin(user);
		Current current = current(school, id);
		if (current.leftOn() != null) {
			throw ApiException.conflict("students.already_left");
		}
		LocalDate today = clock.instant().atZone(calendar.zone(school)).toLocalDate();
		LocalDate leftOn = date != null ? date : today;
		if (leftOn.isAfter(today)) {
			throw ApiException.invalid("students.leave_date_future", "date");
		}
		jdbc.update("UPDATE student SET left_on = :date WHERE id = :id AND school_id = :school",
				school(school).addValue("id", id).addValue("date", Date.valueOf(leftOn)));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "student", id.toString(),
				Map.of("classId", current.classId()), Map.of("classId", current.classId(), "leftOn", leftOn.toString())));
		return view(school, id);
	}

	/** The student is enrolled again, in the same class or another; the time away has no records. */
	@Transactional
	StudentView comeBack(CurrentUser user, UUID id, UUID classId) {
		UUID school = requireAdmin(user);
		Current current = current(school, id);
		if (current.leftOn() == null) {
			throw ApiException.conflict("students.not_left");
		}
		UUID target = classId != null ? classId : current.classId();
		requireClass(school, target);
		jdbc.update("UPDATE student SET left_on = NULL, class_id = :class WHERE id = :id AND school_id = :school",
				school(school).addValue("id", id).addValue("class", target));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "student", id.toString(),
				Map.of("classId", current.classId(), "leftOn", current.leftOn().toString()), Map.of("classId", target)));
		return view(school, id);
	}

	/** The student with their guardians; 404 for another school's (NFR-8, D17). */
	StudentView view(UUID school, UUID id) {
		MapSqlParameterSource p = school(school).addValue("id", id);
		// D46: the parents who used up their password resets this school year
		Set<UUID> locked = resets.locked(school, jdbc.queryForList("""
				SELECT g.user_id FROM student_guardian sg JOIN guardian g ON g.id = sg.guardian_id
				WHERE sg.school_id = :school AND sg.student_id = :id AND g.user_id IS NOT NULL""", p, UUID.class));
		List<GuardianView> guardians = jdbc.query("""
				SELECT g.id, g.name, g.phone, g.language, sg.relation, sg.is_primary, g.user_id IS NOT NULL AS has_account,
				       g.user_id,
				       (SELECT count(*) FROM student_guardian o
				        WHERE o.guardian_id = g.id AND o.school_id = g.school_id) AS children
				FROM student_guardian sg
				JOIN guardian g ON g.id = sg.guardian_id AND g.school_id = sg.school_id
				WHERE sg.school_id = :school AND sg.student_id = :id
				ORDER BY sg.is_primary DESC, lower(g.name), g.id""", p,
				(rs, n) -> new GuardianView(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("phone"),
						rs.getString("language"), rs.getString("relation"), rs.getBoolean("is_primary"),
						rs.getBoolean("has_account"), rs.getInt("children"),
						locked.contains(rs.getObject("user_id", UUID.class))));
		return jdbc.query("""
				SELECT matricule, first_name, last_name, sex, birth_year, class_id, left_on
				FROM student WHERE id = :id AND school_id = :school""", p, (rs, n) -> {
			UUID classId = rs.getObject("class_id", UUID.class);
			return new StudentView(id, rs.getString("matricule"), rs.getString("first_name"), rs.getString("last_name"),
					rs.getString("sex"), rs.getInt("birth_year"), classId,
					schoolClasses.names(school, Set.of(classId)).get(classId), rs.getObject("left_on", LocalDate.class),
					guardians);
		}).stream().findFirst().orElseThrow(() -> ApiException.notFound("not_found"));
	}

	/** The guardian of the school with that phone, made if nobody has it yet; the name on file stays. */
	UUID guardianFor(UUID school, GuardianRequest guardian) {
		MapSqlParameterSource p = school(school).addValue("phone", guardian.phone());
		return jdbc.queryForList("SELECT id FROM guardian WHERE school_id = :school AND phone = :phone", p, UUID.class)
			.stream()
			.findFirst()
			.orElseGet(() -> jdbc.queryForObject("""
					INSERT INTO guardian (school_id, name, phone, language)
					VALUES (:school, :name, :phone, :language) RETURNING id""",
					p.addValue("name", guardian.name().trim())
						.addValue("language",
								guardian.language() != null ? guardian.language() : schoolClasses.language(school)),
					UUID.class));
	}

	/** Phones as stored ({@code +237...}, D15), each one once. */
	static List<GuardianRequest> normalized(List<GuardianRequest> guardians) {
		List<GuardianRequest> result = new ArrayList<>();
		Set<String> phones = new HashSet<>();
		for (GuardianRequest guardian : guardians) {
			String phone = PhoneNumbers.normalize(guardian.phone())
				.orElseThrow(() -> ApiException.invalid("validation.phone", "guardians"));
			if (!phones.add(phone)) {
				throw ApiException.invalid("students.guardian_duplicate", "guardians");
			}
			result.add(new GuardianRequest(phone, guardian.name(), guardian.relation(), guardian.language(),
					guardian.primary()));
		}
		return result;
	}

	UUID requireAdmin(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	private void moveAll(CurrentUser user, UUID school, Map<UUID, Current> students, UUID classId) {
		jdbc.update("UPDATE student SET class_id = :class WHERE school_id = :school AND id IN (:ids)",
				school(school).addValue("class", classId).addValue("ids", students.keySet()));
		students.forEach((id, current) -> audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "student",
				id.toString(), Map.of("classId", current.classId()), Map.of("classId", classId))));
	}

	private Current current(UUID school, UUID id) {
		return jdbc.query("SELECT class_id, left_on FROM student WHERE id = :id AND school_id = :school FOR UPDATE",
				school(school).addValue("id", id),
				(rs, n) -> new Current(rs.getObject("class_id", UUID.class), rs.getObject("left_on", LocalDate.class)))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
	}

	private void requireClass(UUID school, UUID classId) {
		if (!schoolClasses.classExists(school, classId)) {
			throw ApiException.invalid("reference.unknown", "classId");
		}
	}

	/** A matricule is one student's in the school, whatever its case. */
	private void requireFreeMatricule(UUID school, String matricule, UUID except) {
		boolean taken = !jdbc.queryForList("""
				SELECT id FROM student
				WHERE school_id = :school AND lower(matricule) = lower(:matricule)
				  AND (CAST(:except AS uuid) IS NULL OR id <> CAST(:except AS uuid)) LIMIT 1""",
				school(school).addValue("matricule", matricule).addValue("except", Objects.toString(except, null)),
				UUID.class)
			.isEmpty();
		if (taken) {
			throw ApiException.conflict("students.matricule_exists");
		}
	}

	private static ApiException matriculeTaken(String state) {
		return ConstraintGuard.UNIQUE.equals(state) ? ApiException.conflict("students.matricule_exists") : null;
	}

	private static DetailsRequest trimmed(DetailsRequest request) {
		return new DetailsRequest(request.matricule().trim(), request.firstName().trim(), request.lastName().trim(),
				request.sex(), request.birthYear());
	}

	private static void changed(List<String> changed, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			changed.add(field);
		}
	}

	private static MapSqlParameterSource details(UUID school, DetailsRequest details) {
		return school(school).addValue("matricule", details.matricule())
			.addValue("first", details.firstName())
			.addValue("last", details.lastName())
			.addValue("sex", details.sex())
			.addValue("birthYear", details.birthYear());
	}

	private static MapSqlParameterSource school(UUID school) {
		return new MapSqlParameterSource("school", school);
	}

}
