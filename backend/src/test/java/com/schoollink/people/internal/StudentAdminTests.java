package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Date;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** D43: the admin keeps the students up to date after the import (NFR-8, NFR-11). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class StudentAdminTests {

	/** 08:40 in Douala, Monday 2 November 2026. */
	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

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

	/** In class B, with the same guardian as student A. */
	UUID studentB;

	/** In school Y. */
	UUID studentY;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		clock.set(NOW);
		studentB = student(world.schoolX, world.classB, "TW-0002", "Bruno", "Inventé");
		studentY = student(world.schoolY, world.classC, "TW-Y001", "Yann", "Imaginaire");
		UUID guardian = jdbc.queryForObject("SELECT id FROM guardian WHERE phone = '+237690000001'", UUID.class);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', true)""", world.schoolX, studentB, guardian);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolManagesStudents() { // NFR-8
		Actor[] others = { TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER };
		return Stream.of(
				PermissionMatrix.row("GET students", w -> get("/api/v1/students").param("classId", w.classA.toString()))
					.expect(200, ADMIN_X, ADMIN_Y),
				PermissionMatrix.row("GET student", w -> get("/api/v1/students/{id}", w.studentA))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix.row("POST student", w -> body(post("/api/v1/students"), newStudent(Map.of())))
					.expect(201, ADMIN_X)
					.expect(400, ADMIN_Y), // class A is not in school Y
				PermissionMatrix.row("PUT student", w -> body(put("/api/v1/students/{id}", w.studentA), details(Map.of())))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix
					.row("PUT class", w -> body(put("/api/v1/students/{id}/class", w.studentA), Map.of("classId", w.classB)))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix
					.row("POST move",
							w -> body(post("/api/v1/students/move"),
									Map.of("studentIds", List.of(w.studentA), "classId", w.classB)))
					.expect(200, ADMIN_X)
					.expect(400, ADMIN_Y),
				PermissionMatrix.row("POST leave", w -> body(post("/api/v1/students/{id}/leave", w.studentA), Map.of()))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix.row("POST return", w -> body(post("/api/v1/students/{id}/return", w.studentA), Map.of()))
					.expect(200, ADMIN_X) // after the leave above
					.expect(404, ADMIN_Y))
			.flatMap(row -> row.expect(403, others).expect(401, ANONYMOUS).tests(mockMvc, world));
	}

	@Test
	void theListIsTheSchoolsByClassOrByNameOrMatricule() throws Exception { // NFR-8
		perform(get("/api/v1/students").param("classId", world.classA.toString())).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(world.studentA.toString()))
			.andExpect(jsonPath("$[0].matricule").value("TW-0001"))
			.andExpect(jsonPath("$[0].firstName").value("Élève"))
			.andExpect(jsonPath("$[0].lastName").value("Fictif"))
			.andExpect(jsonPath("$[0].classId").value(world.classA.toString()))
			.andExpect(jsonPath("$[0].className").value("6e A"))
			.andExpect(jsonPath("$[0].leftOn").isEmpty())
			.andExpect(jsonPath("$[0].guardians").value(1));
		// by name, then by matricule; never a student of another school
		perform(get("/api/v1/students").param("q", "invent")).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(studentB.toString()));
		perform(get("/api/v1/students").param("q", "TW-")).andExpect(jsonPath("$.length()").value(2));
		perform(get("/api/v1/students").param("q", "Yann")).andExpect(jsonPath("$.length()").value(0));
		perform(get("/api/v1/students").param("classId", world.classC.toString()))
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void studentsWhoLeftAreListedOnlyOnRequest() throws Exception {
		jdbc.update("UPDATE student SET left_on = '2026-10-30' WHERE id = ?", world.studentA);

		perform(get("/api/v1/students").param("classId", world.classA.toString()))
			.andExpect(jsonPath("$.length()").value(0));
		perform(get("/api/v1/students").param("classId", world.classA.toString()).param("left", "true"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].leftOn").value("2026-10-30"));
	}

	@Test
	void aStudentIsShownWithTheirGuardians() throws Exception {
		perform(get("/api/v1/students/{id}", world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.matricule").value("TW-0001"))
			.andExpect(jsonPath("$.sex").value("F"))
			.andExpect(jsonPath("$.birthYear").value(2012))
			.andExpect(jsonPath("$.className").value("6e A"))
			.andExpect(jsonPath("$.guardians.length()").value(1))
			.andExpect(jsonPath("$.guardians[0].name").value("Parent Fictif"))
			.andExpect(jsonPath("$.guardians[0].phone").value("+237690000001"))
			.andExpect(jsonPath("$.guardians[0].language").value("FR"))
			.andExpect(jsonPath("$.guardians[0].primary").value(true))
			.andExpect(jsonPath("$.guardians[0].hasAccount").value(true))
			.andExpect(jsonPath("$.guardians[0].children").value(2));
		perform(get("/api/v1/students/{id}", studentY)).andExpect(status().isNotFound());
	}

	@Test
	void aStudentIsAddedWithANewGuardianAndAKnownOne() throws Exception {
		String response = perform(body(post("/api/v1/students"), newStudent(Map.of("guardians", List.of(
				Map.of("phone", "690 000 001", "name", "Autre Nom", "relation", "MOTHER", "primary", false),
				Map.of("phone", "+237690000077", "name", "Tante Inventée", "relation", "GUARDIAN", "language", "EN",
						"primary", true))))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.matricule").value("TW-0100"))
			.andExpect(jsonPath("$.className").value("6e A"))
			.andExpect(jsonPath("$.guardians.length()").value(2))
			// the primary first; the known phone keeps the name on file
			.andExpect(jsonPath("$.guardians[0].name").value("Tante Inventée"))
			.andExpect(jsonPath("$.guardians[0].language").value("EN"))
			.andExpect(jsonPath("$.guardians[0].hasAccount").value(false))
			.andExpect(jsonPath("$.guardians[1].name").value("Parent Fictif"))
			.andExpect(jsonPath("$.guardians[1].relation").value("MOTHER"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = json.readTree(response).get("id").asString();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM guardian WHERE school_id = ?", Integer.class, world.schoolX))
			.isEqualTo(2);
		// NFR-11, NFR-9: audited with ids, without names or phones
		String after = jdbc.queryForObject(
				"SELECT after::text FROM audit_log WHERE entity = 'student' AND entity_id = ? AND action = 'CREATE'",
				String.class, id);
		assertThat(after).contains(world.classA.toString()).doesNotContain("Nouvelle", "690000077");
		// the roll of the class has them
		perform(get("/api/v1/students").param("classId", world.classA.toString()))
			.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void anInvalidNewStudentIsRefusedWithTheField() throws Exception {
		expectInvalid(newStudent(Map.of("matricule", " ")), "matricule");
		expectInvalid(newStudent(Map.of("sex", "X")), "sex");
		expectInvalid(newStudent(Map.of("birthYear", 1900)), "birthYear");
		expectInvalid(newStudent(Map.of("classId", world.classC)), "classId"); // another school's class
		expectInvalid(newStudent(Map.of("guardians", List.of())), "guardians");
		perform(body(post("/api/v1/students"), newStudent(Map.of("guardians", List.of(guardian("+33612345678", true))))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.phone"));
		// exactly one primary guardian, each phone once
		perform(body(post("/api/v1/students"),
				newStudent(Map.of("guardians", List.of(guardian("+237690000077", false))))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("students.primary_required"));
		perform(body(post("/api/v1/students"), newStudent(
				Map.of("guardians", List.of(guardian("+237690000077", true), guardian("690000077", false))))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("students.guardian_duplicate"));
		perform(body(post("/api/v1/students"), newStudent(Map.of("matricule", "tw-0001")))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.matricule_exists"))
			.andExpect(jsonPath("$.message").value("Un élève a déjà ce matricule."));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE school_id = ?", Integer.class, world.schoolX))
			.isEqualTo(2);
	}

	@Test
	void theDetailsOfAStudentAreCorrected() throws Exception {
		perform(body(put("/api/v1/students/{id}", world.studentA), details(Map.of("firstName", " Awa ", "birthYear", 2011))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Awa"))
			.andExpect(jsonPath("$.birthYear").value(2011))
			.andExpect(jsonPath("$.classId").value(world.classA.toString()));

		String audit = jdbc.queryForObject("""
				SELECT after::text FROM audit_log WHERE entity = 'student' AND entity_id = ? AND action = 'UPDATE'""",
				String.class, world.studentA.toString());
		assertThat(audit).contains("firstName", "birthYear").doesNotContain("Awa");
		// another student's matricule
		perform(body(put("/api/v1/students/{id}", world.studentA), details(Map.of("matricule", "TW-0002"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.matricule_exists"));
	}

	@Test
	void aStudentMovesToAnotherClassAndKeepsTheirRecords() throws Exception { // BR-4
		UUID session = jdbc.queryForObject("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at, synced_at)
				VALUES (gen_random_uuid(), ?, ?, '2026-10-30', ?, ?, now(), now()) RETURNING id""", UUID.class,
				world.schoolX, world.classA,
				world.period1, world.user(TEACHER_A));
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (gen_random_uuid(), ?, ?, ?, 'ABSENT')""", world.schoolX, session, world.studentA);

		perform(body(put("/api/v1/students/{id}/class", world.studentA), Map.of("classId", world.classB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.classId").value(world.classB.toString()))
			.andExpect(jsonPath("$.className").value("5e B"));

		assertThat(jdbc.queryForObject("SELECT class_id FROM attendance_session WHERE id = ?", UUID.class, session))
			.isEqualTo(world.classA);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE student_id = ?", Integer.class,
				world.studentA))
			.isOne();
		String audit = jdbc.queryForObject("""
				SELECT before::text || after::text FROM audit_log
				WHERE entity = 'student' AND entity_id = ? AND action = 'UPDATE'""", String.class,
				world.studentA.toString());
		assertThat(audit).contains(world.classA.toString(), world.classB.toString());
		// an unknown class, or another school's
		perform(body(put("/api/v1/students/{id}/class", world.studentA), Map.of("classId", world.classC)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("classId"));
	}

	@Test
	void aWholeClassMovesInOneStepOrNotAtAll() throws Exception { // promotion
		UUID second = student(world.schoolX, world.classA, "TW-0003", "Chantal", "Inventée");

		perform(body(post("/api/v1/students/move"),
				Map.of("studentIds", List.of(world.studentA, second), "classId", world.classB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.moved").value(2));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student WHERE class_id = ?", Integer.class, world.classB))
			.isEqualTo(3);
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_log WHERE entity = 'student' AND action = 'UPDATE' AND entity_id IN (?, ?)""",
				Integer.class, world.studentA.toString(), second.toString()))
			.isEqualTo(2);

		// one student of another school in the list: nobody moves
		perform(body(post("/api/v1/students/move"),
				Map.of("studentIds", List.of(world.studentA, studentY), "classId", world.classA)))
			.andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT class_id FROM student WHERE id = ?", UUID.class, world.studentA))
			.isEqualTo(world.classB);
		perform(body(post("/api/v1/students/move"), Map.of("studentIds", List.of(), "classId", world.classA)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void aStudentLeavesTheSchoolAndComesBack() throws Exception {
		perform(body(post("/api/v1/students/{id}/leave", world.studentA), Map.of("date", "2026-10-30")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.leftOn").value("2026-10-30"));
		assertThat(jdbc.queryForObject("SELECT left_on FROM student WHERE id = ?", Date.class, world.studentA))
			.isEqualTo(Date.valueOf("2026-10-30"));

		// already left: not twice, not moved
		perform(body(post("/api/v1/students/{id}/leave", world.studentA), Map.of())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.already_left"));
		perform(body(put("/api/v1/students/{id}/class", world.studentA), Map.of("classId", world.classB)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.already_left"));
		perform(body(post("/api/v1/students/move"), Map.of("studentIds", List.of(world.studentA), "classId", world.classB)))
			.andExpect(status().isConflict());

		// back, in another class
		perform(body(post("/api/v1/students/{id}/return", world.studentA), Map.of("classId", world.classB)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.leftOn").isEmpty())
			.andExpect(jsonPath("$.classId").value(world.classB.toString()));
		perform(body(post("/api/v1/students/{id}/return", world.studentA), Map.of())).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.not_left"));
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_log WHERE entity = 'student' AND entity_id = ? AND action = 'UPDATE'""",
				Integer.class, world.studentA.toString()))
			.isEqualTo(2);
	}

	@Test
	void theLeavingDateIsTodayByDefaultAndNeverInTheFuture() throws Exception {
		perform(body(post("/api/v1/students/{id}/leave", world.studentA), Map.of("date", "2026-11-03")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("students.leave_date_future"))
			.andExpect(jsonPath("$.field").value("date"));
		perform(body(post("/api/v1/students/{id}/leave", world.studentA), Map.of())).andExpect(status().isOk())
			.andExpect(jsonPath("$.leftOn").value("2026-11-02"));
	}

	private void expectInvalid(Map<String, Object> request, String field) throws Exception {
		perform(body(post("/api/v1/students"), request)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value(field));
	}

	private Map<String, Object> newStudent(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(details(Map.of("matricule", "TW-0100", "firstName", "Nouvelle")));
		body.put("classId", world.classA);
		body.put("guardians", List.of(guardian("+237690000077", true)));
		body.putAll(overrides);
		return body;
	}

	private static Map<String, Object> details(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(Map.of("matricule", "TW-0001", "firstName", "Élève", "lastName", "Fictif",
				"sex", "F", "birthYear", 2012));
		body.putAll(overrides);
		return body;
	}

	private static Map<String, Object> guardian(String phone, boolean primary) {
		return Map.of("phone", phone, "name", "Tante Inventée", "relation", "GUARDIAN", "primary", primary);
	}

	private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Map<String, Object> body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(ADMIN_X)));
	}

	private UUID student(UUID school, UUID classId, String matricule, String firstName, String lastName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, ?, 'M', 2012) RETURNING id""", UUID.class, school, classId, matricule, firstName,
				lastName);
	}

}
