package com.schoollink.school.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.util.Map;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-A1 school structure API: SPEC §3 "School structure" row, rules and audit (NFR-8, NFR-11). Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SchoolStructureApiTests {

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

	// Permission matrix

	@TestFactory
	Stream<DynamicTest> everyStaffMemberReadsTheCalendar() {
		return Stream.of("/api/v1/academic-years", "/api/v1/terms", "/api/v1/holidays", "/api/v1/periods")
			.flatMap(path -> PermissionMatrix.row("GET " + path, w -> get(path))
				.expect(200, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, ADMIN_Y)
				.expect(403, PARENT_A, RESEARCHER)
				.expect(401, ANONYMOUS)
				.tests(mockMvc, world));
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminCreates() {
		return Stream.of(
				create("/api/v1/academic-years", w -> Map.of("label", "2027-2028", "startDate", "2027-09-06",
						"endDate", "2028-06-30"), 201),
				create("/api/v1/terms", w -> Map.of("academicYearId", w.yearX, "label", "Trimestre 2", "startDate",
						"2027-01-04", "endDate", "2027-03-26"), 400),
				create("/api/v1/holidays", w -> Map.of("termId", w.termX, "label", "Toussaint", "startDate",
						"2026-11-02", "endDate", "2026-11-03"), 400),
				create("/api/v1/periods", w -> Map.of("number", 2, "startTime", "08:25", "endTime", "09:20"), 201),
				create("/api/v1/blocks", w -> Map.of("name", "Bloc 3"), 201),
				create("/api/v1/classes", w -> Map.of("academicYearId", w.yearX, "blockId", w.block1, "name", "6e C",
						"level", "6e"), 400),
				create("/api/v1/timetable-slots", w -> Map.of("classId", w.classA, "weekday", 2, "periodId", w.period1,
						"teacherId", w.user(TEACHER_A), "subject", "Maths"), 400))
			.flatMap(row -> row.tests(mockMvc, world));
	}

	/** ADMIN_Y gets {@code adminY}: 201 when the body has no references, 400 when it points into school X. */
	private PermissionMatrix create(String path, java.util.function.Function<TestWorld, Map<String, Object>> body,
			int adminY) {
		return PermissionMatrix.row("POST " + path, w -> jsonBody(post(path), body.apply(w)))
			.expect(201, ADMIN_X)
			.expect(adminY, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolUpdates() {
		return PermissionMatrix.row("PUT class A", w -> jsonBody(put("/api/v1/classes/{id}", w.classA),
				Map.of("academicYearId", w.yearX, "blockId", w.block1, "name", "6e A bis", "level", "6e")))
			.expect(200, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolDeletes() {
		// ADMIN_Y runs after ADMIN_X has deleted the slot, and would get 404 either way
		return PermissionMatrix.row("DELETE slot", w -> delete("/api/v1/timetable-slots/{id}", w.slotA))
			.expect(204, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> blocksFollowTheScope() {
		return PermissionMatrix.row("GET block 2", w -> get("/api/v1/blocks/{id}", w.block2))
			.expect(200, ADMIN_X, DM_2, COUNSELLOR_X)
			.expect(404, TEACHER_A, ASSISTANT_DM_1, DM_1, ADMIN_Y)
			.expect(403, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void theTimetableFollowsTheScope() throws Exception {
		UUID teacherB = teacherOf(world.classB, "Français");
		jdbc.update("""
				INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
				VALUES (?, ?, 1, ?, ?, 'Français')""", world.schoolX, world.classB, world.period1, teacherB);

		perform(TEACHER_A, get("/api/v1/timetable-slots")).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].classId").value(world.classA.toString()));
		perform(DM_2, get("/api/v1/timetable-slots")).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].classId").value(world.classB.toString()));
		perform(ADMIN_X, get("/api/v1/timetable-slots")).andExpect(jsonPath("$.length()").value(2));
		perform(ADMIN_X, get("/api/v1/timetable-slots").param("classId", world.classB.toString()))
			.andExpect(jsonPath("$.length()").value(1));
		perform(ADMIN_Y, get("/api/v1/timetable-slots")).andExpect(jsonPath("$.length()").value(0));
	}

	// Rules

	@Test
	void datesMustBeInOrder() throws Exception {
		perform(ADMIN_X, jsonBody(post("/api/v1/academic-years"),
				Map.of("label", "2030", "startDate", "2030-09-01", "endDate", "2030-08-01")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.date_order"))
			.andExpect(jsonPath("$.field").value("endDate"));
		perform(ADMIN_X, jsonBody(post("/api/v1/periods"), Map.of("number", 3, "startTime", "10:00", "endTime", "09:00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("endTime"));
	}

	@Test
	void termsLieWithinTheirYearAndNeverOverlap() throws Exception {
		perform(ADMIN_X, term("Trimestre 4", "2027-06-01", "2027-07-15")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("term.outside_year"));
		perform(ADMIN_X, term("Chevauche", "2026-12-01", "2027-01-15")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("term.overlap"));
		perform(ADMIN_X, term("Trimestre 1", "2027-01-04", "2027-03-26")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("term.duplicate"));
		perform(ADMIN_X, term("Trimestre 2", "2027-01-04", "2027-03-26")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.label").value("Trimestre 2"));
	}

	@Test
	void holidaysLieWithinTheirTerm() throws Exception { // D10
		perform(ADMIN_X, holiday("2026-12-20", "2027-01-02")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("holiday.outside_term"));
		perform(ADMIN_X, holiday("2026-11-02", "2026-11-02")).andExpect(status().isCreated());

		// the term cannot then shrink past its holidays
		perform(ADMIN_X, jsonBody(put("/api/v1/terms/{id}", world.termX), Map.of("academicYearId", world.yearX,
				"label", "Trimestre 1", "startDate", "2026-09-07", "endDate", "2026-10-30")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("term.holidays_outside"));
	}

	@Test
	void aYearCannotShrinkPastItsTerms() throws Exception {
		perform(ADMIN_X, jsonBody(put("/api/v1/academic-years/{id}", world.yearX),
				Map.of("label", "2026-2027", "startDate", "2026-10-01", "endDate", "2027-06-30")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("academic_year.terms_outside"));
	}

	@Test
	void periodsNeverOverlap() throws Exception {
		perform(ADMIN_X, jsonBody(post("/api/v1/periods"), Map.of("number", 2, "startTime", "08:00", "endTime", "09:00")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("period.overlap"));
		perform(ADMIN_X, jsonBody(post("/api/v1/periods"), Map.of("number", 1, "startTime", "09:00", "endTime", "10:00")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("period.duplicate"));
		// a period may keep its own times when edited
		perform(ADMIN_X, jsonBody(put("/api/v1/periods/{id}", world.period1),
				Map.of("number", 1, "startTime", "07:30", "endTime", "08:30")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.endTime").value("08:30:00"));
	}

	@Test
	void referencesMustBelongToTheSameSchool() throws Exception { // NFR-8
		UUID blockY = jdbc.queryForObject("SELECT id FROM block WHERE school_id = ?", UUID.class, world.schoolY);

		perform(ADMIN_X, jsonBody(post("/api/v1/classes"),
				Map.of("academicYearId", world.yearX, "blockId", blockY, "name", "6e Z", "level", "6e")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("reference.unknown"))
			.andExpect(jsonPath("$.field").value("blockId"));
	}

	@Test
	void classNamesAreUniquePerYear() throws Exception {
		perform(ADMIN_X, jsonBody(post("/api/v1/classes"),
				Map.of("academicYearId", world.yearX, "blockId", world.block2, "name", "6e A", "level", "6e")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("class.duplicate"));
	}

	@Test
	void aSlotNeedsAnAssignedTeacherWhoIsFree() throws Exception { // D1, BR-1
		UUID teacher = world.user(TEACHER_A);
		perform(ADMIN_X, slot(world.classB, 1, teacher, "Maths")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("timetable.teacher_not_assigned"))
			.andExpect(jsonPath("$.field").value("teacherId"));

		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Maths')",
				world.schoolX, teacher, world.classB);
		perform(ADMIN_X, slot(world.classB, 1, teacher, "Maths")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.teacher_busy"));

		UUID other = teacherOf(world.classA, "Anglais");
		perform(ADMIN_X, slot(world.classA, 1, other, "Anglais")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("timetable.slot_taken"));

		perform(ADMIN_X, slot(world.classB, 2, teacher, "Maths")).andExpect(status().isCreated());
	}

	@Test
	void somethingStillInUseIsNotDeleted() throws Exception {
		perform(ADMIN_X, delete("/api/v1/classes/{id}", world.classA)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("in_use"))
			.andExpect(jsonPath("$.message").value("Cet élément est encore utilisé et ne peut pas être supprimé."));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM school_class WHERE id = ?", Integer.class, world.classA))
			.isOne();
	}

	@Test
	void unknownIdsAreNotFound() throws Exception {
		perform(ADMIN_X, delete("/api/v1/terms/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void missingFieldsAreRejectedWithTheField() throws Exception {
		perform(ADMIN_X, jsonBody(post("/api/v1/blocks"), Map.of("name", " ")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.not_blank"))
			.andExpect(jsonPath("$.field").value("name"));
		perform(ADMIN_X, slot(world.classA, 6, world.user(TEACHER_A), "Maths")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("weekday"));
	}

	// Audit (NFR-11)

	@Test
	void everyChangeIsAudited() throws Exception {
		String body = perform(ADMIN_X, jsonBody(post("/api/v1/blocks"), Map.of("name", "Bloc 3")))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID block = UUID.fromString(json.readTree(body).get("id").asString());
		perform(ADMIN_X, jsonBody(put("/api/v1/blocks/{id}", block), Map.of("name", "Bloc 3 bis")))
			.andExpect(status().isOk());
		perform(ADMIN_X, delete("/api/v1/blocks/{id}", block)).andExpect(status().isNoContent());

		var rows = jdbc.queryForList("""
				SELECT action, school_id, actor_id, before ->> 'name' AS before, after ->> 'name' AS after
				FROM audit_log WHERE entity = 'block' AND entity_id = ? ORDER BY at, action""", block.toString());
		assertThat(rows).extracting(r -> r.get("action")).containsExactlyInAnyOrder("CREATE", "UPDATE", "DELETE");
		assertThat(rows).allSatisfy(r -> {
			assertThat(r.get("school_id")).isEqualTo(world.schoolX);
			assertThat(r.get("actor_id")).isEqualTo(world.user(ADMIN_X));
		});
		assertThat(rows).filteredOn(r -> r.get("action").equals("UPDATE"))
			.singleElement()
			.satisfies(r -> {
				assertThat(r.get("before")).isEqualTo("Bloc 3");
				assertThat(r.get("after")).isEqualTo("Bloc 3 bis");
			});
	}

	@Test
	void aRejectedChangeLeavesNoAuditRow() throws Exception {
		perform(ADMIN_X, jsonBody(post("/api/v1/blocks"), Map.of("name", "Bloc 1"))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("block.duplicate"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity = 'block' AND school_id = ?",
				Integer.class, world.schoolX)).isZero();
	}

	// Helpers

	private UUID teacherOf(UUID schoolClass, String subject) {
		UUID teacher = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, email)
				VALUES (?, 'Autre', 'Enseignant', ?) RETURNING id""", UUID.class, world.schoolX,
				"teacher-" + UUID.randomUUID() + "@example.test");
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'TEACHER')", world.schoolX,
				teacher);
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, ?)",
				world.schoolX, teacher, schoolClass, subject);
		return teacher;
	}

	private MockHttpServletRequestBuilder term(String label, String start, String end) {
		return jsonBody(post("/api/v1/terms"),
				Map.of("academicYearId", world.yearX, "label", label, "startDate", start, "endDate", end));
	}

	private MockHttpServletRequestBuilder holiday(String start, String end) {
		return jsonBody(post("/api/v1/holidays"),
				Map.of("termId", world.termX, "label", "Congé", "startDate", start, "endDate", end));
	}

	private MockHttpServletRequestBuilder slot(UUID schoolClass, int weekday, UUID teacher, String subject) {
		return jsonBody(post("/api/v1/timetable-slots"), Map.of("classId", schoolClass, "weekday", weekday, "periodId",
				world.period1, "teacherId", teacher, "subject", subject));
	}

	private MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder request, Map<String, ?> body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

}
