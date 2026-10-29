package com.schoollink.school.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SPEC §3 "School structure" row, read side, for classes (NFR-8). Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SchoolClassAccessTests {

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

	@TestFactory
	Stream<DynamicTest> readClassA() {
		return PermissionMatrix.row("GET class A", w -> get("/api/v1/classes/{id}", w.classA))
			.expect(200, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, COUNSELLOR_X)
			.expect(404, DM_2, ADMIN_Y)
			.expect(403, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> readClassB() {
		// a teacher of class A cannot read class B
		return PermissionMatrix.row("GET class B", w -> get("/api/v1/classes/{id}", w.classB))
			.expect(200, ADMIN_X, DM_2, COUNSELLOR_X)
			.expect(404, TEACHER_A, ASSISTANT_DM_1, DM_1, ADMIN_Y)
			.expect(403, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> listClasses() {
		return PermissionMatrix.row("GET classes", w -> get("/api/v1/classes"))
			.expect(200, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, ADMIN_Y)
			.expect(403, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@ParameterizedTest
	@CsvSource({ "ADMIN_X, AB", "COUNSELLOR_X, AB", "TEACHER_A, A", "ASSISTANT_DM_1, A", "DM_1, A", "DM_2, B",
			"ADMIN_Y, C" })
	void listShowsOnlyTheClassesInScope(Actor actor, String expected) throws Exception {
		String body = mockMvc.perform(get("/api/v1/classes").header("Authorization", "Bearer " + world.token(actor)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		List<String> seen = new ArrayList<>();
		for (JsonNode node : json.readTree(body)) {
			UUID id = UUID.fromString(node.get("id").asString());
			seen.add(id.equals(world.classA) ? "A" : id.equals(world.classB) ? "B" : id.equals(world.classC) ? "C" : "?");
		}
		assertThat(seen).containsExactlyInAnyOrderElementsOf(List.of(expected.split("")));
	}

	@Test
	void aClassComesWithItsBlockAndNoPersonalData() throws Exception {
		mockMvc.perform(get("/api/v1/classes/{id}", world.classA).header("Authorization", "Bearer " + world.token(DM_1)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("6e A"))
			.andExpect(jsonPath("$.blockId").value(world.block1.toString()))
			.andExpect(jsonPath("$.schoolId").doesNotExist());
	}

	@Test
	void outOfScopeIsReportedAsNotFoundWithTheUsualErrorBody() throws Exception {
		mockMvc.perform(get("/api/v1/classes/{id}", world.classC).header("Authorization", "Bearer " + world.token(ADMIN_X))
			.header("Accept-Language", "en"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("not_found"));
	}

}
