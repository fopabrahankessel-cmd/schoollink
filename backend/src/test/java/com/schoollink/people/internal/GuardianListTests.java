package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-A4 guardian list for the admin (task 1.19, D7, NFR-8, NFR-9). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GuardianListTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	TestWorld world;

	/** Father of student A (class A) and of student B (class B), without an account. */
	UUID father;

	/** Guardian of a class B student only: never in class A's list. */
	UUID otherClass;

	UUID studentB;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		father = guardian("Père Fictif", "+237690000002");
		otherClass = guardian("Tutrice Ailleurs", "+237690000003");
		studentB = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0002', 'Cadet', 'Fictif', 'M', 2014) RETURNING id""", UUID.class, world.schoolX,
				world.classB);
		UUID studentC = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0003', 'Autre', 'Exemple', 'F', 2013) RETURNING id""", UUID.class, world.schoolX,
				world.classB);
		link(world.studentA, father, "FATHER");
		link(studentB, father, "FATHER");
		link(studentC, otherClass, "GUARDIAN");
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolListsTheGuardians() {
		return PermissionMatrix.row("GET guardians", w -> get("/api/v1/guardians").param("classId", w.classA.toString()))
			.expect(200, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void listsTheClassGuardiansWithTheirStatusAndAllTheirChildren() throws Exception {
		JsonNode list = list(world.classA);

		assertThat(list).hasSize(2);
		JsonNode withAccount = byName(list, "Parent Fictif");
		assertThat(withAccount.get("status").asString()).isEqualTo("ACCOUNT"); // D7: push, no code needed
		assertThat(withAccount.get("phone").asString()).isEqualTo("+237690000001");

		JsonNode dad = byName(list, "Père Fictif");
		assertThat(dad.get("status").asString()).isEqualTo("NOT_INVITED");
		assertThat(dad.get("invitedAt").isNull()).isTrue();
		// siblings in another class are shown too, primary flag and relation included
		assertThat(dad.get("children")).extracting(c -> c.get("firstName").asString()).containsExactly("Cadet", "Élève");
		assertThat(dad.get("children").get(0).get("classId").asString()).isEqualTo(world.classB.toString());
		assertThat(dad.get("children").get(0).get("relation").asString()).isEqualTo("FATHER");
		assertThat(list).noneMatch(g -> g.get("id").asString().equals(otherClass.toString()));
	}

	@Test
	void followsTheLatestCodeFromInvitedToExpired() throws Exception {
		perform(ADMIN_X, post("/api/v1/guardians/{id}/invitations", father)).andExpect(status().isCreated());

		JsonNode invited = byName(list(world.classA), "Père Fictif");
		assertThat(invited.get("status").asString()).isEqualTo("INVITED");
		assertThat(invited.get("expiresAt").isNull()).isFalse();

		jdbc.update("""
				UPDATE invitation_code SET created_at = now() - interval '31 days', expires_at = now() - interval '1 day'
				WHERE guardian_id = ?""", father);
		assertThat(byName(list(world.classA), "Père Fictif").get("status").asString()).isEqualTo("EXPIRED");
	}

	@Test
	void anUnknownClassIsNotFound() throws Exception {
		perform(ADMIN_X, get("/api/v1/guardians").param("classId", UUID.randomUUID().toString()))
			.andExpect(status().isNotFound());
	}

	private JsonNode list(UUID classId) throws Exception {
		String body = perform(ADMIN_X, get("/api/v1/guardians").param("classId", classId.toString()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(body);
	}

	private ResultActions perform(Actor actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private static JsonNode byName(JsonNode list, String name) {
		for (JsonNode g : list) {
			if (g.get("name").asString().equals(name)) {
				return g;
			}
		}
		throw new AssertionError("no guardian " + name);
	}

	private UUID guardian(String name, String phone) {
		return jdbc.queryForObject("INSERT INTO guardian (school_id, name, phone) VALUES (?, ?, ?) RETURNING id",
				UUID.class, world.schoolX, name, phone);
	}

	private void link(UUID student, UUID guardian, String relation) {
		jdbc.update("INSERT INTO student_guardian (school_id, student_id, guardian_id, relation) VALUES (?, ?, ?, ?)",
				world.schoolX, student, guardian, relation);
	}

}
