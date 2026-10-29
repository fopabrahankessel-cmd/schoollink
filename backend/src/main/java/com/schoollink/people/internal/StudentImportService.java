package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.internal.ImportRows.Result;
import com.schoollink.people.internal.ImportRows.RowError;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC-A3, step 1 (task 1.11): the admin uploads a CSV or Excel file for one academic year; every
 * row is checked and stored in {@code student_import_row} with its errors. Nothing reaches the
 * student and guardian tables until the commit (task 1.12). Admin only (SPEC §3); another school's
 * import is 404 (NFR-8).
 */
@Service
class StudentImportService {

	private final NamedParameterJdbcTemplate jdbc;

	private final SpreadsheetReader reader;

	private final SchoolClasses schoolClasses;

	private final AuditLog audit;

	private final MessageSource messages;

	private final JsonMapper json;

	private final Clock clock;

	StudentImportService(NamedParameterJdbcTemplate jdbc, SpreadsheetReader reader, SchoolClasses schoolClasses,
			AuditLog audit, MessageSource messages, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.reader = reader;
		this.schoolClasses = schoolClasses;
		this.audit = audit;
		this.messages = messages;
		this.json = json;
		this.clock = clock;
	}

	record ImportView(UUID id, UUID academicYearId, String fileName, String status, int rowCount, int validCount,
			int errorCount, List<ErrorView> errors) {
	}

	/** @param row the row number as the spreadsheet shows it (the header is row 1) */
	record ErrorView(int row, String column, String code, String message) {
	}

	@Transactional
	ImportView preview(CurrentUser user, UUID academicYearId, String fileName, byte[] content) {
		UUID school = requireAdmin(user);
		if (!schoolClasses.yearExists(school, academicYearId)) {
			throw ApiException.invalid("reference.unknown", "academicYearId");
		}
		SpreadsheetReader.Table table = reader.read(fileName, content);
		Map<ImportColumn, Integer> positions = columns(table.headers());
		Map<ImportColumn, String> headers = new EnumMap<>(ImportColumn.class);
		positions.forEach((column, index) -> headers.put(column, table.headers().get(index).strip()));

		// rows keep their spreadsheet number; rows with nothing in the known columns are skipped
		Map<Integer, Map<ImportColumn, String>> rows = new LinkedHashMap<>();
		for (SpreadsheetReader.Line line : table.rows()) {
			Map<ImportColumn, String> cells = new EnumMap<>(ImportColumn.class);
			positions.forEach((column, index) -> cells.put(column,
					index < line.cells().size() ? line.cells().get(index).strip() : ""));
			if (cells.values().stream().anyMatch(v -> !v.isEmpty())) {
				rows.put(line.number(), cells);
			}
		}
		if (rows.isEmpty()) {
			throw ApiException.invalid("import.file_empty", "file");
		}

		Map<String, UUID> classes = schoolClasses.classesInYear(school, academicYearId)
			.entrySet()
			.stream()
			.collect(Collectors.toMap(e -> ImportRows.classKey(e.getKey()), Map.Entry::getValue, (a, b) -> a));
		String language = schoolClasses.language(school);
		int currentYear = clock.instant().atZone(ZoneId.of("Africa/Douala")).getYear();
		Set<String> existing = Set.copyOf(jdbc.queryForList("SELECT matricule FROM student WHERE school_id = :school",
				Map.of("school", school), String.class));
		Map<String, Integer> seen = new HashMap<>();

		List<MapSqlParameterSource> staged = new ArrayList<>();
		int errorCount = 0;
		UUID importId = UUID.randomUUID();
		for (var entry : rows.entrySet()) {
			Result result = ImportRows.check(entry.getValue(), headers, classes, language, currentYear);
			List<RowError> errors = new ArrayList<>(result.errors());
			String matricule = entry.getValue().get(ImportColumn.MATRICULE);
			if (!matricule.isEmpty()) {
				Integer first = seen.putIfAbsent(matricule, entry.getKey());
				if (first != null) {
					errors.add(RowError.of(headers.get(ImportColumn.MATRICULE), "import.matricule_duplicate_in_file", first));
				}
				else if (existing.contains(matricule)) {
					errors.add(RowError.of(headers.get(ImportColumn.MATRICULE), "import.matricule_exists"));
				}
			}
			if (!errors.isEmpty()) {
				errorCount++;
			}
			staged.add(new MapSqlParameterSource("school", school).addValue("import", importId)
				.addValue("row", entry.getKey())
				.addValue("data", errors.isEmpty() ? json.writeValueAsString(result.student()) : null)
				.addValue("errors", json.writeValueAsString(errors)));
		}

		String name = fileName == null ? "" : fileName.strip();
		jdbc.update("""
				INSERT INTO student_import (id, school_id, academic_year_id, file_name, row_count, error_count, created_by)
				VALUES (:id, :school, :year, :name, :rows, :errors, :user)""",
				new MapSqlParameterSource("id", importId).addValue("school", school)
					.addValue("year", academicYearId)
					.addValue("name", name)
					.addValue("rows", rows.size())
					.addValue("errors", errorCount)
					.addValue("user", user.userId()));
		jdbc.batchUpdate("""
				INSERT INTO student_import_row (school_id, import_id, row_number, data, errors)
				VALUES (:school, :import, :row, :data::jsonb, :errors::jsonb)""",
				staged.toArray(MapSqlParameterSource[]::new));
		// NFR-11: the import itself is audited; its rows (students' personal data) are not copied into the log
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "student_import", importId.toString(),
				null, Map.of("fileName", name, "academicYearId", academicYearId, "rowCount", rows.size(), "errorCount",
						errorCount)));
		return view(school, importId);
	}

	ImportView get(CurrentUser user, UUID id) {
		return view(requireAdmin(user), id);
	}

	/** Errors in the reader's language (NFR-12), by row then column order of detection. */
	private ImportView view(UUID school, UUID id) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", school).addValue("id", id);
		ImportView header = jdbc.query("""
				SELECT id, academic_year_id, file_name, status, row_count, error_count FROM student_import
				WHERE id = :id AND school_id = :school""", p,
				(rs, n) -> new ImportView(rs.getObject("id", UUID.class), rs.getObject("academic_year_id", UUID.class),
						rs.getString("file_name"), rs.getString("status"), rs.getInt("row_count"),
						rs.getInt("row_count") - rs.getInt("error_count"), rs.getInt("error_count"), List.of()))
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("not_found"));
		List<ErrorView> errors = new ArrayList<>();
		jdbc.query("""
				SELECT row_number, errors::text AS errors FROM student_import_row
				WHERE import_id = :id AND school_id = :school AND data IS NULL ORDER BY row_number""", p, rs -> {
			for (RowError error : json.readValue(rs.getString("errors"), new TypeReference<List<RowError>>() {
			})) {
				errors.add(new ErrorView(rs.getInt("row_number"), error.column(), error.code(), messages
					.getMessage("error." + error.code(), error.args().toArray(), LocaleContextHolder.getLocale())));
			}
		});
		return new ImportView(header.id(), header.academicYearId(), header.fileName(), header.status(),
				header.rowCount(), header.validCount(), header.errorCount(), List.copyOf(errors));
	}

	/** Every required column once; unknown columns are ignored. */
	private static Map<ImportColumn, Integer> columns(List<String> headers) {
		Map<ImportColumn, Integer> positions = new EnumMap<>(ImportColumn.class);
		for (int i = 0; i < headers.size(); i++) {
			int index = i;
			ImportColumn.forHeader(headers.get(i)).ifPresent(column -> {
				if (positions.putIfAbsent(column, index) != null) {
					throw ApiException.invalid("import.column_duplicate", "file", headers.get(index).strip());
				}
			});
		}
		String missing = Arrays.stream(ImportColumn.values())
			.filter(c -> c.required && !positions.containsKey(c))
			.map(ImportColumn::header)
			.collect(Collectors.joining(", "));
		if (!missing.isEmpty()) {
			throw ApiException.invalid("import.column_missing", "file", missing);
		}
		return positions;
	}

	private static UUID requireAdmin(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

}
