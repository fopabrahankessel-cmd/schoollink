package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-A3 import, step 2: commit (task 1.12, D15, D19, NFR-9, NFR-11). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StudentImportCommitTests {

	/**
	 * Ali and Bella are siblings: same mother's phone (spelled differently). Carl's guardian is
	 * TestWorld's existing guardian (+237690000001, "Parent Fictif").
	 */
	static final String VALID = """
			matricule;nom;prenom;sexe;annee_naissance;classe;tuteur1_nom;tuteur1_telephone;tuteur1_lien;tuteur2_nom;tuteur2_telephone;tuteur2_lien;tuteur2_langue
			TW-3001;Frère;Ali;M;2012;6e A;Maman Frère;699300001;MERE;Papa Frère;677300002;PERE;EN
			TW-3002;Frère;Bella;F;2014;5e B;Maman Frere;699 30 00 01;MERE;;;;
			TW-3003;Voisin;Carl;M;2013;6e A;Autre Nom;+237690000001;TUTEUR;;;;
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	StudentImportCommitter committer;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolCommits() throws Exception {
		UUID id = preview(VALID);
		return PermissionMatrix.row("POST commit", w -> post("/api/v1/students/import/{id}/commit", id))
			.expect(200, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void aValidImportCreatesStudentsGuardiansAndLinks() throws Exception {
		UUID id = preview(VALID);

		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", id)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMMITTED"))
			.andExpect(jsonPath("$.studentsCreated").value(3))
			.andExpect(jsonPath("$.guardiansCreated").value(2))
			.andExpect(jsonPath("$.guardiansLinked").value(1));

		var ali = jdbc.queryForMap("SELECT * FROM student WHERE school_id = ? AND matricule = 'TW-3001'", world.schoolX);
		assertThat(ali).containsEntry("first_name", "Ali")
			.containsEntry("last_name", "Frère")
			.containsEntry("sex", "M")
			.containsEntry("class_id", world.classA);
		assertThat(((Number) ali.get("birth_year")).intValue()).isEqualTo(2012);
		var bella = jdbc.queryForMap("SELECT * FROM student WHERE school_id = ? AND matricule = 'TW-3002'", world.schoolX);
		assertThat(bella).containsEntry("class_id", world.classB);

		// D15: one guardian per phone; siblings share the mother
		UUID mother = guardian("+237699300001");
		assertThat(jdbc.queryForObject("SELECT name FROM guardian WHERE id = ?", String.class, mother)).isEqualTo("Maman Frère");
		assertThat(links(mother)).containsExactlyInAnyOrder("TW-3001 MOTHER true", "TW-3002 MOTHER true");
		UUID father = guardian("+237677300002");
		assertThat(links(father)).containsExactly("TW-3001 FATHER false");
		assertThat(jdbc.queryForObject("SELECT language FROM guardian WHERE id = ?", String.class, father)).isEqualTo("EN");
		assertThat(jdbc.queryForObject("SELECT language FROM guardian WHERE id = ?", String.class, mother)).isEqualTo("FR");

		// an existing guardian is linked, never overwritten
		UUID existing = guardian("+237690000001");
		assertThat(jdbc.queryForObject("SELECT name FROM guardian WHERE id = ?", String.class, existing))
			.isEqualTo("Parent Fictif");
		assertThat(links(existing)).containsExactlyInAnyOrder("TW-0001 MOTHER true", "TW-3003 GUARDIAN true");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM guardian WHERE school_id = ?", Integer.class, world.schoolX))
			.isEqualTo(3);
	}

	@Test
	void theStagedRowsAreDeletedAndTheCommitIsAudited() throws Exception { // NFR-9, NFR-11
		UUID id = preview(VALID);
		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", id)).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_import_row WHERE import_id = ?", Integer.class, id))
			.isZero();
		perform(ADMIN_X, get("/api/v1/students/import/{id}", id)).andExpect(jsonPath("$.status").value("COMMITTED"))
			.andExpect(jsonPath("$.rowCount").value(3));
		JsonNode after = json.readTree(jdbc.queryForObject("""
				SELECT after::text FROM audit_log WHERE entity = 'student_import' AND entity_id = ? AND action = 'IMPORT'""",
				String.class, id.toString()));
		assertThat(after.get("studentIds")).hasSize(3);
		assertThat(after.get("guardianIdsCreated")).hasSize(2);
		assertThat(after.toString()).doesNotContain("Ali").doesNotContain("699300001");
	}

	@Test
	void anImportIsCommittedOnlyOnce() throws Exception {
		UUID id = preview(VALID);
		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", id)).andExpect(status().isOk());

		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("import.already_committed"));
		assertThat(studentCount()).isEqualTo(4);
	}

	@Test
	void anImportWithErrorsIsNotCommitted() throws Exception { // D19: all or nothing
		UUID id = preview(new String(fixture("/import/students-with-errors.csv"), StandardCharsets.UTF_8));

		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("import.has_errors"))
			.andExpect(jsonPath("$.message").value(
					"6 lignes contiennent des erreurs. Corrigez le fichier et envoyez-le de nouveau."));
		assertThat(studentCount()).isOne();
	}

	@Test
	void aPreviewOutdatedByAnotherImportIsNotCommitted() throws Exception {
		UUID first = preview(VALID);
		UUID second = preview(VALID);
		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", first)).andExpect(status().isOk());

		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", second)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("import.stale"));
		assertThat(studentCount()).isEqualTo(4);
		perform(ADMIN_X, get("/api/v1/students/import/{id}", second)).andExpect(jsonPath("$.status").value("PREVIEW"));
	}

	@Test
	void aPreviewWhoseClassWasRemovedIsNotCommitted() throws Exception {
		UUID id = preview(VALID);
		jdbc.update("DELETE FROM school_class WHERE id = ?", world.classB);

		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", id)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("import.stale"));
		assertThat(studentCount()).isOne();
	}

	@Test
	void oldPreviewsExpireAndLoseTheirRows() throws Exception { // D19, NFR-9
		UUID old = preview(VALID);
		UUID recent = preview(VALID);
		jdbc.update("UPDATE student_import SET created_at = now() - interval '8 days' WHERE id = ?", old);

		int expired = committer.expireBefore(Instant.now().minus(7, ChronoUnit.DAYS));

		assertThat(expired).isOne();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_import_row WHERE import_id = ?", Integer.class, old))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_import_row WHERE import_id = ?", Integer.class, recent))
			.isEqualTo(3);
		perform(ADMIN_X, post("/api/v1/students/import/{id}/commit", old)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("import.expired"));
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_log
				WHERE entity = 'student_import' AND entity_id = ? AND action = 'UPDATE' AND actor_id IS NULL""",
				Integer.class, old.toString())).isOne();
	}

	// Helpers

	private UUID guardian(String phone) {
		return jdbc.queryForObject("SELECT id FROM guardian WHERE school_id = ? AND phone = ?", UUID.class, world.schoolX,
				phone);
	}

	private java.util.List<String> links(UUID guardian) {
		return jdbc.query("""
				SELECT s.matricule, sg.relation, sg.is_primary FROM student_guardian sg
				JOIN student s ON s.id = sg.student_id WHERE sg.guardian_id = ?""",
				(rs, n) -> rs.getString(1) + " " + rs.getString(2) + " " + rs.getBoolean(3), guardian);
	}

	private int studentCount() {
		return jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id = ?", Integer.class, world.schoolX);
	}

	private UUID preview(String csv) throws Exception {
		var request = multipart("/api/v1/students/import")
			.file(new MockMultipartFile("file", "eleves.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
		request.param("academicYearId", world.yearX.toString());
		String body = perform(ADMIN_X, request).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		return UUID.fromString(json.readTree(body).get("id").asString());
	}

	private static byte[] fixture(String path) throws IOException {
		try (InputStream in = StudentImportCommitTests.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}

	private ResultActions perform(Actor actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}


}
