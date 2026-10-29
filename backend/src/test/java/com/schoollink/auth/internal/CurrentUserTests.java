package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scope resolution (NFR-8, SPEC §3): roles, language and classes come from the database. Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CurrentUserTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JwtDecoder decoder;

	@Autowired
	CurrentUserResolver resolver;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
	}

	@Test
	void aTeacherCoversTheClassesTheyTeach() {
		CurrentUser user = resolve(Actor.TEACHER_A);

		assertThat(user.schoolId()).isEqualTo(world.schoolX);
		assertThat(user.roles()).containsExactly(Role.TEACHER);
		assertThat(user.classesAs(Role.TEACHER)).containsExactly(world.classA);
		assertThat(user.children()).isEmpty();
	}

	@Test
	void aDmAndAnAssistantDmCoverTheClassesOfTheirBlock() { // D5
		assertThat(resolve(Actor.DM_1).classesAs(Role.DM)).containsExactly(world.classA);
		assertThat(resolve(Actor.DM_2).classesAs(Role.DM)).containsExactly(world.classB);
		assertThat(resolve(Actor.ASSISTANT_DM_1).classesAs(Role.ASSISTANT_DM)).containsExactly(world.classA);
	}

	@Test
	void aParentCoversTheirOwnChildrenOnly() {
		CurrentUser parent = resolve(Actor.PARENT_A);

		assertThat(parent.children()).containsExactly(world.studentA);
		assertThat(parent.classesAs(Role.values())).isEmpty();
	}

	@Test
	void theResearcherHasNoSchool() {
		CurrentUser researcher = resolve(Actor.RESEARCHER);

		assertThat(researcher.schoolId()).isNull();
		assertThat(researcher.classesAs(Role.values())).isEmpty();
	}

	@Test
	void severalRolesKeepTheirOwnScope() {
		UUID dm = world.user(Actor.DM_2);
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'TEACHER')", world.schoolX, dm);
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'SVT')",
				world.schoolX, dm, world.classA);

		CurrentUser user = resolve(Actor.DM_2);

		assertThat(user.classesAs(Role.DM)).containsExactly(world.classB);
		assertThat(user.classesAs(Role.TEACHER)).containsExactly(world.classA);
		assertThat(user.classesAs(Role.TEACHER, Role.DM)).containsExactlyInAnyOrder(world.classA, world.classB);
	}

	@Test
	void aNewAssignmentAppliesWithoutANewToken() throws Exception {
		String token = world.token(Actor.TEACHER_A);
		assertStatus(token, "/api/v1/classes/" + world.classB, 404);

		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'SVT')",
				world.schoolX, world.user(Actor.TEACHER_A), world.classB);

		assertStatus(token, "/api/v1/classes/" + world.classB, 200);
	}

	@Test
	void aRemovedRoleStopsWorkingAtOnce() throws Exception {
		String token = world.token(Actor.ADMIN_X);
		jdbc.update("DELETE FROM role_assignment WHERE user_id = ?", world.user(Actor.ADMIN_X));

		assertStatus(token, "/api/v1/classes", 403);
	}

	@Test
	void aDisabledAccountIsSignedOutAtOnce() throws Exception {
		String token = world.token(Actor.TEACHER_A);
		jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", world.user(Actor.TEACHER_A));

		assertStatus(token, "/api/v1/me", 401);
	}

	@Test
	void aTokenWhoseSchoolDoesNotMatchTheAccountIsRejected() throws Exception {
		assertStatus(token(world.user(Actor.ADMIN_X), world.schoolY), "/api/v1/me", 401);
		assertStatus(token(world.user(Actor.ADMIN_X), null), "/api/v1/me", 401);
		assertStatus(token(world.user(Actor.RESEARCHER), world.schoolX), "/api/v1/me", 401);
		assertStatus(token(UUID.randomUUID(), world.schoolX), "/api/v1/me", 401);
	}

	@Test
	void meShowsTheRolesAndLanguageFromTheDatabase() throws Exception {
		String token = world.token(Actor.DM_1);
		jdbc.update("UPDATE app_user SET language = 'EN' WHERE id = ?", world.user(Actor.DM_1));

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[0]").value("DM"))
			.andExpect(jsonPath("$.language").value("EN"));
		// NFR-12: error messages follow the account language at once
		mockMvc.perform(get("/api/v1/classes/{id}", world.classC).header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("The requested resource was not found."));
	}

	private CurrentUser resolve(Actor actor) {
		return (CurrentUser) resolver.convert(decoder.decode(world.token(actor))).getPrincipal();
	}

	/** A validly signed token for {@code subject} with the given school claim (none if {@code null}). */
	private String token(UUID subject, UUID school) {
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(SecurityConfig.ISSUER)
			.subject(subject.toString())
			.issuedAt(now)
			.expiresAt(now.plusSeconds(600))
			.claim("roles", List.of("ADMIN"));
		if (school != null) {
			claims.claim("school", school.toString());
		}
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
			.getTokenValue();
	}

	private void assertStatus(String token, String path, int expected) throws Exception {
		int actual = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
			.andReturn()
			.getResponse()
			.getStatus();
		assertThat(actual).as(path).isEqualTo(expected);
	}

}
