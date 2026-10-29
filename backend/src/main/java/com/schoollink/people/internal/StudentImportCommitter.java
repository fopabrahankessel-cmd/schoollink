package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.internal.ImportRows.StagedGuardian;
import com.schoollink.people.internal.ImportRows.StagedStudent;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.jdbc.ConstraintGuard;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC-A3, step 2 (task 1.12, decision D19): commits a previewed import in one transaction. Only an
 * import without errors is committed (all or nothing), once. A guardian is identified by phone
 * within the school (D15): brothers and sisters share one guardian, and an existing guardian is
 * linked as is, never overwritten. Afterwards the staged rows are deleted (NFR-9).
 */
@Service
class StudentImportCommitter {

	/** D19: uncommitted previews hold personal data; they expire after this. */
	static final Duration PREVIEW_LIFETIME = Duration.ofDays(7);

	private static final Logger log = LoggerFactory.getLogger(StudentImportCommitter.class);

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolClasses schoolClasses;

	private final ConstraintGuard guard;

	private final AuditLog audit;

	private final JsonMapper json;

	private final Clock clock;

	StudentImportCommitter(NamedParameterJdbcTemplate jdbc, SchoolClasses schoolClasses, ConstraintGuard guard,
			AuditLog audit, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.schoolClasses = schoolClasses;
		this.guard = guard;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	record CommitView(UUID id, String status, int studentsCreated, int guardiansCreated, int guardiansLinked) {
	}

	private record Header(UUID academicYearId, String status, int errorCount) {
	}

	@Transactional
	CommitView commit(CurrentUser user, UUID importId) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		MapSqlParameterSource p = new MapSqlParameterSource("school", school).addValue("id", importId);
		// the lock makes a second, concurrent commit of the same import wait, then see COMMITTED
		Header header = jdbc.query("""
				SELECT academic_year_id, status, error_count FROM student_import
				WHERE id = :id AND school_id = :school FOR UPDATE""", p,
				(rs, n) -> new Header(rs.getObject("academic_year_id", UUID.class), rs.getString("status"),
						rs.getInt("error_count")))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		switch (header.status()) {
			case "COMMITTED" -> throw ApiException.conflict("import.already_committed");
			case "EXPIRED" -> throw ApiException.conflict("import.expired");
			default -> {
			}
		}
		if (header.errorCount() > 0) {
			throw ApiException.conflict("import.has_errors", header.errorCount());
		}
		List<StagedStudent> students = jdbc.queryForList("""
				SELECT data::text FROM student_import_row WHERE import_id = :id AND school_id = :school
				ORDER BY row_number""", p, String.class)
			.stream()
			.map(data -> json.readValue(data, StagedStudent.class))
			.toList();

		checkStillValid(school, header.academicYearId(), students);

		// D15: one guardian per phone in the school
		Map<String, UUID> guardians = new HashMap<>();
		jdbc.query("SELECT id, phone FROM guardian WHERE school_id = :school AND phone IN (:phones)",
				new MapSqlParameterSource("school", school).addValue("phones", phones(students)),
				rs -> {
					guardians.put(rs.getString("phone"), rs.getObject("id", UUID.class));
				});
		int linked = guardians.size();
		Map<String, StagedGuardian> created = new LinkedHashMap<>();
		students.forEach(s -> s.guardians().forEach(g -> {
			if (!guardians.containsKey(g.phone())) {
				created.putIfAbsent(g.phone(), g);
			}
		}));
		List<MapSqlParameterSource> guardianRows = new ArrayList<>();
		created.values().forEach(g -> {
			UUID id = UUID.randomUUID();
			guardians.put(g.phone(), id);
			guardianRows.add(new MapSqlParameterSource("id", id).addValue("school", school)
				.addValue("name", g.name())
				.addValue("phone", g.phone())
				.addValue("language", g.language()));
		});

		List<MapSqlParameterSource> studentRows = new ArrayList<>();
		List<MapSqlParameterSource> links = new ArrayList<>();
		List<UUID> studentIds = new ArrayList<>();
		for (StagedStudent s : students) {
			UUID id = UUID.randomUUID();
			studentIds.add(id);
			studentRows.add(new MapSqlParameterSource("id", id).addValue("school", school)
				.addValue("class", s.classId())
				.addValue("matricule", s.matricule())
				.addValue("first", s.firstName())
				.addValue("last", s.lastName())
				.addValue("sex", s.sex())
				.addValue("birthYear", s.birthYear()));
			for (StagedGuardian g : s.guardians()) {
				links.add(new MapSqlParameterSource("school", school).addValue("student", id)
					.addValue("guardian", guardians.get(g.phone()))
					.addValue("relation", g.relation())
					.addValue("primary", g.primary()));
			}
		}

		// a concurrent import or edit since the checks above: same answer as the checks
		guard.run(() -> {
			jdbc.batchUpdate("""
					INSERT INTO guardian (id, school_id, name, phone, language)
					VALUES (:id, :school, :name, :phone, :language)""", guardianRows.toArray(MapSqlParameterSource[]::new));
			jdbc.batchUpdate("""
					INSERT INTO student (id, school_id, class_id, matricule, first_name, last_name, sex, birth_year)
					VALUES (:id, :school, :class, :matricule, :first, :last, :sex, :birthYear)""",
					studentRows.toArray(MapSqlParameterSource[]::new));
			return jdbc.batchUpdate("""
					INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
					VALUES (:school, :student, :guardian, :relation, :primary)""", links.toArray(MapSqlParameterSource[]::new));
		}, state -> ApiException.conflict("import.stale"));

		jdbc.update("""
				UPDATE student_import SET status = 'COMMITTED', committed_at = :now WHERE id = :id AND school_id = :school""",
				p.addValue("now", Timestamp.from(clock.instant())));
		jdbc.update("DELETE FROM student_import_row WHERE import_id = :id AND school_id = :school", p);
		// NFR-11: ids and counts only; the students' personal data is not copied into the log
		audit.record(new AuditEntry(school, user.userId(), AuditAction.IMPORT, "student_import", importId.toString(),
				null, Map.of("studentIds", studentIds, "guardianIdsCreated",
						created.keySet().stream().map(guardians::get).toList(), "guardiansLinked", linked)));
		return new CommitView(importId, "COMMITTED", students.size(), created.size(), linked);
	}

	/**
	 * D19: the school may have changed since the preview (another import, a deleted class).
	 * Rather than commit part of the file, the admin uploads it again for a fresh preview.
	 */
	private void checkStillValid(UUID school, UUID year, List<StagedStudent> students) {
		Set<UUID> classes = Set.copyOf(schoolClasses.classesInYear(school, year).values());
		boolean classGone = students.stream().anyMatch(s -> !classes.contains(s.classId()));
		boolean matriculeTaken = !jdbc.queryForList(
				"SELECT matricule FROM student WHERE school_id = :school AND matricule IN (:matricules) LIMIT 1",
				new MapSqlParameterSource("school", school).addValue("matricules",
						students.stream().map(StagedStudent::matricule).collect(Collectors.toSet())),
				String.class)
			.isEmpty();
		if (classGone || matriculeTaken) {
			throw ApiException.conflict("import.stale");
		}
	}

	private static Set<String> phones(List<StagedStudent> students) {
		return students.stream()
			.flatMap(s -> s.guardians().stream())
			.map(StagedGuardian::phone)
			.collect(Collectors.toSet());
	}

	/** D19, NFR-9: every night, previews older than 7 days expire and their rows are deleted. */
	@Scheduled(cron = "0 30 2 * * *", zone = "Africa/Douala")
	@Transactional
	public void expireOldPreviews() {
		int expired = expireBefore(clock.instant().minus(PREVIEW_LIFETIME));
		if (expired > 0) {
			log.info("Expired {} import previews", expired);
		}
	}

	@Transactional
	int expireBefore(Instant cutoff) {
		List<Map<String, Object>> expired = jdbc.queryForList("""
				UPDATE student_import SET status = 'EXPIRED' WHERE status = 'PREVIEW' AND created_at < :cutoff
				RETURNING id, school_id""", new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)));
		for (Map<String, Object> row : expired) {
			UUID id = (UUID) row.get("id");
			jdbc.update("DELETE FROM student_import_row WHERE import_id = :id", new MapSqlParameterSource("id", id));
			audit.record(new AuditEntry((UUID) row.get("school_id"), null, AuditAction.UPDATE, "student_import",
					id.toString(), null, Map.of("status", "EXPIRED")));
		}
		return expired.size();
	}

}
