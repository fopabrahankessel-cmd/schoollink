package com.schoollink.discipline.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D5, D37: the DM of the student's block records disciplinary incidents, with a category, the
 * date of the incident (school day, at most 30 days back) and a measure; the DM and the admin read
 * them (NFR-8). Records are never deleted or rewritten. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class DisciplinaryRecordTests {

	/** 11:00 in Douala on Tuesday 3 November 2026. */
	static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

	static final LocalDate TODAY = LocalDate.parse("2026-11-03");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	TestClock clock;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (NFR-8)

	@TestFactory
	Stream<DynamicTest> onlyTheStudentsDmRecords() {
		return PermissionMatrix.row("POST /students/{id}/disciplinary-records", w -> create(w.studentA, fight()))
			.expect(201, DM_1)
			.expect(404, DM_2)
			.expect(403, ADMIN_X, ADMIN_Y, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> theStudentsDmAndTheAdminRead() {
		return PermissionMatrix.row("GET /students/{id}/disciplinary-records", w -> list(w.studentA))
			.expect(200, DM_1, ADMIN_X)
			.expect(404, DM_2, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	// Recording

	@Test
	void recordsAnIncidentWithItsCategoryDateAndMeasure() throws Exception {
		String id = body(perform(DM_1, create(world.studentA, Map.of("category", "FIGHT", "measure", "DETENTION",
				"occurredOn", TODAY.minusDays(2).toString(), "description", "  Bagarre à la récréation  ")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.studentId").value(world.studentA.toString()))
			.andExpect(jsonPath("$.category").value("FIGHT"))
			.andExpect(jsonPath("$.measure").value("DETENTION"))
			.andExpect(jsonPath("$.occurredOn").value("2026-11-01"))
			.andExpect(jsonPath("$.description").value("Bagarre à la récréation"))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.recordedByName").value("Test DM_1"))
			.andExpect(jsonPath("$.alertId").isEmpty()), "id");

		Map<String, Object> audit = jdbc.queryForMap("""
				SELECT action, actor_id, after ->> 'category' AS category FROM audit_log
				WHERE entity = 'disciplinary_record' AND entity_id = ?""", id);
		assertThat(audit).containsEntry("action", "CREATE")
			.containsEntry("actor_id", world.user(DM_1))
			.containsEntry("category", "FIGHT");
	}

	@Test
	void measureAndDateDefaultToNoneAndToday() throws Exception {
		perform(DM_1, create(world.studentA, Map.of("category", "PHONE", "description", "Téléphone en classe")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.measure").value("NONE"))
			.andExpect(jsonPath("$.occurredOn").value(TODAY.toString()));
	}

	@Test
	void listsNewestIncidentFirstAndLeavesOutCancelledOnes() throws Exception {
		record(TODAY.minusDays(5), "Retard répété");
		record(TODAY.minusDays(1), "Insolence");
		String cancelled = record(TODAY, "Erreur de saisie");
		jdbc.update("""
				UPDATE disciplinary_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Mauvais élève'
				WHERE id = ?::uuid""", world.user(DM_1), cancelled);

		perform(DM_1, list(world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].description", contains("Insolence", "Retard répété")));
	}

	@Test
	void theDateOfTheIncidentFollowsTheSchoolDay() throws Exception { // Review Focus 1
		// 23:30 UTC on 9 November is already 00:30 on 10 November in Douala
		clock.set(Instant.parse("2026-11-09T23:30:00Z"));

		perform(DM_1, create(world.studentA, dated("2026-11-10"))).andExpect(status().isCreated());
		perform(DM_1, create(world.studentA, dated("2026-11-11"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("discipline.occurred_on_range"))
			.andExpect(jsonPath("$.field").value("occurredOn"));
		perform(DM_1, create(world.studentA, dated("2026-10-11"))).andExpect(status().isCreated());
		perform(DM_1, create(world.studentA, dated("2026-10-10"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("occurredOn"));
	}

	@Test
	void aBlankDescriptionOrMissingCategoryIsRefused() throws Exception { // Review Focus 5
		perform(DM_1, create(world.studentA, Map.of("category", "FIGHT", "description", "   ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("discipline.description_required"))
			.andExpect(jsonPath("$.field").value("description"));
		perform(DM_1, create(world.studentA, Map.of("category", "FIGHT", "description", "x".repeat(501))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("description"));
		perform(DM_1, create(world.studentA, Map.of("description", "Sans catégorie")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("discipline.category_required"))
			.andExpect(jsonPath("$.field").value("category"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM disciplinary_record", Integer.class)).isZero();
	}

	@Test
	void aStudentWhoMovedBlockBelongsToTheNewBlocksDm() throws Exception { // Review Focus 2
		record(TODAY, "Bagarre");
		jdbc.update("UPDATE student SET class_id = ? WHERE id = ?", world.classB, world.studentA);

		perform(DM_1, list(world.studentA)).andExpect(status().isNotFound());
		perform(DM_2, list(world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$[*].description", contains("Bagarre")));
	}

	@Test
	void anAlertDecisionRecordsAnIncidentDatedToday() throws Exception {
		UUID alert = jdbc.queryForObject("""
				INSERT INTO risk_alert (school_id, student_id, class_id, rule_type, threshold, as_of, evidence, raised_at)
				VALUES (?, ?, ?, 'FULL_DAY_ABSENCE', 1, '2026-11-02', '{"dates": ["2026-11-02"]}', ?) RETURNING id""",
				UUID.class, world.schoolX, world.studentA, world.classA, Timestamp.from(NOW.minusSeconds(60)));
		perform(DM_1, post("/api/v1/alerts/{id}/decision", alert).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("decision", "DISCIPLINARY", "note", "Absence non justifiée"))))
			.andExpect(status().isOk());

		perform(DM_1, list(world.studentA)).andExpect(jsonPath("$[0].alertId").value(alert.toString()))
			.andExpect(jsonPath("$[0].category").value("OTHER"))
			.andExpect(jsonPath("$[0].measure").value("NONE"))
			.andExpect(jsonPath("$[0].occurredOn").value(TODAY.toString()))
			.andExpect(jsonPath("$[0].description").value("Absence non justifiée"));
	}

	@Test
	void aRecordCannotBeDeletedOrRewritten() throws Exception { // D37, NFR-11
		String id = record(TODAY, "Bagarre");

		assertThatThrownBy(() -> refused("DELETE FROM disciplinary_record WHERE id = ?::uuid", id))
			.hasMessageContaining("cannot be deleted");
		assertThatThrownBy(() -> refused("UPDATE disciplinary_record SET description = 'Autre' WHERE id = ?::uuid", id))
			.hasMessageContaining("cannot change");
		assertThatThrownBy(() -> refused("UPDATE disciplinary_record SET category = 'DRESS' WHERE id = ?::uuid", id))
			.hasMessageContaining("cannot change");
	}

	// Helpers

	private static Map<String, Object> fight() {
		return Map.of("category", "FIGHT", "description", "Bagarre à la récréation");
	}

	private static Map<String, Object> dated(String day) {
		return Map.of("category", "BEHAVIOUR", "occurredOn", day, "description", "Chahut");
	}

	private String record(LocalDate day, String description) throws Exception {
		return body(perform(DM_1, create(world.studentA,
				Map.of("category", "BEHAVIOUR", "occurredOn", day.toString(), "description", description)))
			.andExpect(status().isCreated()), "id");
	}

	private MockHttpServletRequestBuilder create(UUID student, Map<String, Object> body) {
		return post("/api/v1/students/{id}/disciplinary-records", student).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(new HashMap<>(body)));
	}

	private static MockHttpServletRequestBuilder list(UUID student) {
		return get("/api/v1/students/{id}/disciplinary-records", student);
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private String body(ResultActions result, String field) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString()).get(field).asString();
	}

	/** In a savepoint, so the test's transaction goes on after the database refuses. */
	private void refused(String sql, Object... args) {
		jdbc.execute("SAVEPOINT refused");
		try {
			jdbc.update(sql, args);
		}
		finally {
			jdbc.execute("ROLLBACK TO SAVEPOINT refused");
		}
	}

}
