package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ADMIN_X;
import static com.schoollink.testsupport.Actor.ADMIN_Y;
import static com.schoollink.testsupport.Actor.ANONYMOUS;
import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.COUNSELLOR_X;
import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.DM_2;
import static com.schoollink.testsupport.Actor.PARENT_A;
import static com.schoollink.testsupport.Actor.RESEARCHER;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-A3 import, step 1: preview (task 1.11, NFR-8, NFR-10, NFR-11, NFR-12). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StudentImportPreviewTests {

	/** Exactly the errors planted in students-with-errors.csv: row, column, code. */
	static final List<String> EXPECTED_ERRORS = List.of(
			"5 nom validation.not_blank",
			"5 sexe import.sex_invalid",
			"5 annee_naissance import.birth_year_invalid",
			"5 classe import.class_unknown",
			"5 tuteur1_telephone validation.phone",
			"5 tuteur1_lien import.relation_invalid",
			"5 tuteur1_langue import.language_invalid",
			"6 matricule import.matricule_duplicate_in_file",
			"7 matricule import.matricule_exists",
			"8 tuteur2_nom validation.not_blank",
			"9 tuteur1_nom validation.not_blank",
			"9 tuteur1_telephone validation.not_blank",
			"10 tuteur2_telephone import.guardian_duplicate");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
	}

	// Permission matrix (SPEC §3 "Student list, guardians": admin writes)

	@TestFactory
	Stream<DynamicTest> onlyTheAdminImports() throws IOException {
		byte[] csv = fixture();
		return PermissionMatrix.row("POST import", w -> upload("eleves.csv", csv, w.yearX))
			.expect(201, ADMIN_X)
			// the academic year belongs to school X
			.expect(400, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void onlyTheAdminOfTheSchoolReadsAnImport() throws Exception {
		UUID id = importId(perform(ADMIN_X, upload("eleves.csv", fixture(), world.yearX)));

		perform(ADMIN_X, get("/api/v1/students/import/{id}", id)).andExpect(status().isOk());
		perform(ADMIN_Y, get("/api/v1/students/import/{id}", id)).andExpect(status().isNotFound());
		perform(DM_1, get("/api/v1/students/import/{id}", id)).andExpect(status().isForbidden());
	}

	// The acceptance check: known errors, and nothing in student

	@Test
	void aFixtureWithKnownErrorsReturnsExactlyThoseErrors() throws Exception {
		JsonNode preview = body(perform(ADMIN_X, upload("eleves.csv", fixture(), world.yearX))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PREVIEW"))
			.andExpect(jsonPath("$.fileName").value("eleves.csv"))
			.andExpect(jsonPath("$.rowCount").value(8))
			.andExpect(jsonPath("$.validCount").value(2))
			.andExpect(jsonPath("$.errorCount").value(6)));

		assertThat(errors(preview)).containsExactlyElementsOf(EXPECTED_ERRORS);
		assertThat(message(preview, "5 classe")).isEqualTo("La classe « 6e Z » n'existe pas pour cette année scolaire.");
		assertThat(message(preview, "6 matricule")).isEqualTo("Ce matricule figure déjà à la ligne 2.");
		assertNoStudentCreated();
	}

	@Test
	void validRowsAreStagedNormalized() throws Exception {
		UUID id = importId(perform(ADMIN_X, upload("eleves.csv", fixture(), world.yearX)));

		JsonNode alice = staged(id, 2);
		assertThat(alice.get("classId").asString()).isEqualTo(world.classA.toString());
		assertThat(alice.get("sex").asString()).isEqualTo("F");
		assertThat(alice.get("guardians").get(0).get("phone").asString()).isEqualTo("+237699100001");
		assertThat(alice.get("guardians").get(0).get("relation").asString()).isEqualTo("MOTHER");
		assertThat(alice.get("guardians").get(0).get("primary").asBoolean()).isTrue();

		JsonNode bruno = staged(id, 3);
		assertThat(bruno.get("sex").asString()).isEqualTo("M");
		// NFR-10: the full birth date is reduced to the year
		assertThat(bruno.get("birthYear").asInt()).isEqualTo(2012);
		assertThat(bruno.toString()).doesNotContain("15/03");
		// class names match ignoring case and spaces
		assertThat(bruno.get("classId").asString()).isEqualTo(world.classB.toString());
		assertThat(bruno.get("guardians")).hasSize(2);
		assertThat(bruno.get("guardians").get(0).get("language").asString()).isEqualTo("FR");
		assertThat(bruno.get("guardians").get(1).get("language").asString()).isEqualTo("EN");
		assertThat(bruno.get("guardians").get(1).get("primary").asBoolean()).isFalse();
	}

	@Test
	void anExcelWorkbookGivesTheSameResult() throws Exception {
		JsonNode preview = body(perform(ADMIN_X, upload("eleves.xlsx", xlsx(fixture()), world.yearX))
			.andExpect(status().isCreated()));

		assertThat(errors(preview)).containsExactlyElementsOf(EXPECTED_ERRORS);
		// row 3's birth date is a real Excel date cell
		assertThat(staged(UUID.fromString(preview.get("id").asString()), 3).get("birthYear").asInt()).isEqualTo(2012);
		assertNoStudentCreated();
	}

	@Test
	void aWindowsCsvWithCommasAndEnglishHeadersIsRead() throws Exception {
		String csv = """
				Student ID,Last name,First name,Sex,Birth year,Class,Guardian1 name,Guardian1 phone
				TW-2001,Fotsô,Gaëlle,Female,2013,6e A,Maman Fotsô,699200001
				""";
		JsonNode preview = body(perform(ADMIN_X, upload("pupils.csv", csv.getBytes(Charset.forName("windows-1252")),
				world.yearX))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.errorCount").value(0)));

		JsonNode row = staged(UUID.fromString(preview.get("id").asString()), 2);
		assertThat(row.get("lastName").asString()).isEqualTo("Fotsô");
		assertThat(row.get("firstName").asString()).isEqualTo("Gaëlle");
	}

	@Test
	void fileLevelProblemsRejectTheUpload() throws Exception {
		perform(ADMIN_X, upload("eleves.pdf", fixture(), world.yearX)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("import.format_unsupported"));
		perform(ADMIN_X, upload("eleves.xlsx", "not a workbook".getBytes(StandardCharsets.UTF_8), world.yearX))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("import.file_unreadable"));
		perform(ADMIN_X, upload("eleves.csv", "matricule;nom;prenom;sexe;annee_naissance;classe;tuteur1_nom;tuteur1_telephone\n"
			.getBytes(StandardCharsets.UTF_8), world.yearX))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("import.file_empty"));
		perform(ADMIN_X, upload("eleves.csv", "matricule;nom\nTW-1;X\n".getBytes(StandardCharsets.UTF_8), world.yearX))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("import.column_missing"))
			.andExpect(jsonPath("$.message").value(
					"Colonnes manquantes : prenom, sexe, annee_naissance, classe, tuteur1_nom, tuteur1_telephone. Utilisez le modèle d'import."));
		perform(ADMIN_X, upload("eleves.csv", "nom;Nom\n".getBytes(StandardCharsets.UTF_8), world.yearX))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("import.column_duplicate"));
		perform(ADMIN_X, upload("eleves.csv", fixture(), UUID.randomUUID())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("reference.unknown"))
			.andExpect(jsonPath("$.field").value("academicYearId"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_import WHERE school_id = ?", Integer.class,
				world.schoolX)).isZero();
	}

	@Test
	void errorsAreShownInTheReadersLanguage() throws Exception { // NFR-12
		UUID id = importId(perform(ADMIN_X, upload("eleves.csv", fixture(), world.yearX)));
		jdbc.update("UPDATE app_user SET language = 'EN' WHERE id = ?", world.user(ADMIN_X));

		JsonNode preview = body(perform(ADMIN_X, get("/api/v1/students/import/{id}", id)));

		assertThat(message(preview, "7 matricule")).isEqualTo("A student with this matricule already exists.");
	}

	@Test
	void theImportIsAuditedWithoutStudentData() throws Exception { // NFR-11, NFR-9
		UUID id = importId(perform(ADMIN_X, upload("eleves.csv", fixture(), world.yearX)));

		String after = jdbc.queryForObject("""
				SELECT after::text FROM audit_log WHERE entity = 'student_import' AND entity_id = ? AND action = 'CREATE'""",
				String.class, id.toString());
		assertThat(after).contains("\"rowCount\": 8").contains("\"errorCount\": 6").doesNotContain("Alice")
			.doesNotContain("699");
	}

	// Helpers

	private void assertNoStudentCreated() {
		// TestWorld's own student only
		assertThat(jdbc.queryForList("SELECT matricule FROM student WHERE school_id = ?", String.class, world.schoolX))
			.containsExactly("TW-0001");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM guardian WHERE school_id = ?", Integer.class, world.schoolX))
			.isOne();
	}

	private JsonNode staged(UUID importId, int row) {
		return json.readTree(jdbc.queryForObject(
				"SELECT data::text FROM student_import_row WHERE import_id = ? AND row_number = ?", String.class, importId,
				row));
	}

	private static List<String> errors(JsonNode preview) {
		List<String> errors = new ArrayList<>();
		preview.get("errors")
			.forEach(e -> errors.add(e.get("row").asInt() + " " + e.get("column").asString() + " " + e.get("code").asString()));
		return errors;
	}

	private static String message(JsonNode preview, String rowAndColumn) {
		for (JsonNode e : preview.get("errors")) {
			if ((e.get("row").asInt() + " " + e.get("column").asString()).equals(rowAndColumn)) {
				return e.get("message").asString();
			}
		}
		throw new AssertionError("no error at " + rowAndColumn);
	}

	private static byte[] fixture() throws IOException {
		try (InputStream in = StudentImportPreviewTests.class.getResourceAsStream("/import/students-with-errors.csv")) {
			return in.readAllBytes();
		}
	}

	/** The same rows as an .xlsx workbook, with row 3's birth date as a real date cell. */
	private static byte[] xlsx(byte[] csv) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Élèves");
			CellStyle dateStyle = workbook.createCellStyle();
			dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("dd/mm/yyyy"));
			List<String> lines = new String(csv, StandardCharsets.UTF_8).lines().toList();
			for (int r = 0; r < lines.size(); r++) {
				Row row = sheet.createRow(r);
				String[] cells = lines.get(r).split(";", -1);
				for (int c = 0; c < cells.length; c++) {
					if (r == 2 && c == 4) {
						row.createCell(c).setCellValue(LocalDate.of(2012, 3, 15));
						row.getCell(c).setCellStyle(dateStyle);
					}
					else if (!cells[c].isEmpty()) {
						row.createCell(c).setCellValue(cells[c]);
					}
				}
			}
			workbook.write(out);
			return out.toByteArray();
		}
	}

	private static MockMultipartHttpServletRequestBuilder upload(String name, byte[] content, UUID year) {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/students/import")
			.file(new MockMultipartFile("file", name, "application/octet-stream", content));
		request.param("academicYearId", year.toString());
		return request;
	}

	private UUID importId(ResultActions result) throws Exception {
		return UUID.fromString(body(result.andExpect(status().isCreated())).get("id").asString());
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	private ResultActions perform(Actor actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

}
