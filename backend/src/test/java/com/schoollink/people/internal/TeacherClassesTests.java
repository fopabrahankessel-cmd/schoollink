package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-T1, UC-T2: {@code GET /me/classes}, the rosters of the classes a teacher teaches, for the app
 * to record offline (NFR-3). TEACHER_A teaches 6e A only. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class TeacherClassesTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		student(world.schoolX, world.classA, "TW-0102", "Aline", "Abanda");
		student(world.schoolX, world.classB, "TW-0101", "Brice", "Fictif");
		student(world.schoolY, world.classC, "TW-0201", "Yann", "Fictif");
	}

	@TestFactory
	Stream<DynamicTest> onlyTeachersReadTheirRosters() {
		return PermissionMatrix.row("GET /me/classes", w -> get("/api/v1/me/classes"))
			.expect(200, TEACHER_A)
			.expect(403, ADMIN_X, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void aTeacherGetsTheStudentsOfTheClassesTheyTeachOnly() throws Exception {
		mockMvc.perform(get("/api/v1/me/classes").header("Authorization", "Bearer " + world.token(TEACHER_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].classId").value(world.classA.toString()))
			.andExpect(jsonPath("$[0].className").value("6e A"))
			// by last name: Abanda, then Fictif
			.andExpect(jsonPath("$[0].students[*].lastName", contains("Abanda", "Fictif")))
			.andExpect(jsonPath("$[0].students[1].id").value(world.studentA.toString()))
			.andExpect(jsonPath("$[0].students[1].firstName").value("Élève"))
			.andExpect(jsonPath("$[0].students[0].matricule").value("TW-0102"));
	}

	@Test
	void nothingBeyondWhatIdentifiesAStudent() throws Exception {
		// NFR-9: no birth year, sex or guardian
		mockMvc.perform(get("/api/v1/me/classes").header("Authorization", "Bearer " + world.token(TEACHER_A)))
			.andExpect(jsonPath("$[0].students[0].birthYear").doesNotExist())
			.andExpect(jsonPath("$[0].students[0].sex").doesNotExist())
			.andExpect(jsonPath("$[0].students[0].classId").doesNotExist());
	}

	@Test
	void aTeacherWithNoClassGetsAnEmptyList() throws Exception {
		jdbc.update("DELETE FROM timetable_slot WHERE teacher_id = ?", world.user(TEACHER_A));
		jdbc.update("DELETE FROM teaching_assignment WHERE teacher_id = ?", world.user(TEACHER_A));

		mockMvc.perform(get("/api/v1/me/classes").header("Authorization", "Bearer " + world.token(TEACHER_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	private UUID student(UUID school, UUID classId, String matricule, String firstName, String lastName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, ?, 'F', 2012) RETURNING id""", UUID.class, school, classId, matricule, firstName,
				lastName);
	}

}
