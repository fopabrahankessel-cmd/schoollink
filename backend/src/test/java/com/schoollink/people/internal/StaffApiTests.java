package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.shared.ratelimit.RateLimiter;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-A2 staff accounts and assignments (D5, D15, D18, NFR-8, NFR-11). Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StaffApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	RateLimiter limiter;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		limiter.clear();
		world = TestWorld.create(jdbc, encoder);
	}

	// Permission matrix (SPEC §3 "School structure, accounts")

	@TestFactory
	Stream<DynamicTest> staffReadTheirColleagues() {
		return PermissionMatrix.row("GET staff", w -> get("/api/v1/staff"))
			.expect(200, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, ADMIN_Y)
			.expect(403, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminCreatesStaff() {
		return PermissionMatrix.row("POST staff",
				w -> jsonBody(post("/api/v1/staff"), Map.of("firstName", "Nouvel", "lastName", "Agent", "phone", "699 11 22 33")))
			.expect(201, ADMIN_X, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolAssignsClasses() {
		return PermissionMatrix.row("POST teaching", w -> jsonBody(post("/api/v1/staff/{id}/teaching", w.user(TEACHER_A)),
				Map.of("classId", w.classB, "subject", "Physique")))
			.expect(201, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolEditsAccounts() {
		return PermissionMatrix.row("PUT staff", w -> jsonBody(put("/api/v1/staff/{id}", w.user(DM_2)),
				Map.of("firstName", "Autre", "lastName", "Nom", "email", "dm2@example.test")))
			.expect(200, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void colleaguesSeeNamesAndRolesWithinTheirScopeOnly() throws Exception {
		JsonNode teacherView = body(perform(TEACHER_A, get("/api/v1/staff")).andExpect(status().isOk()));
		assertThat(lastNames(teacherView)).containsExactlyInAnyOrder("TEACHER_A", "ASSISTANT_DM_1", "DM_1");
		assertThat(teacherView.get(0).has("phone")).isFalse();
		assertThat(teacherView.get(0).has("email")).isFalse();

		assertThat(lastNames(body(perform(DM_2, get("/api/v1/staff"))))).containsExactly("DM_2");
		// parents (no password) and admins are not staff
		assertThat(lastNames(body(perform(COUNSELLOR_X, get("/api/v1/staff"))))).containsExactlyInAnyOrder(
				"TEACHER_A", "ASSISTANT_DM_1", "DM_1", "DM_2", "COUNSELLOR_X");
		assertThat(body(perform(ADMIN_Y, get("/api/v1/staff")))).isEmpty();

		JsonNode adminView = body(perform(ADMIN_X, get("/api/v1/staff")));
		assertThat(adminView).hasSize(5);
		assertThat(adminView.get(0).has("email")).isTrue();
	}

	@Test
	void adminsAndParentsAreNotManagedHere() throws Exception {
		perform(ADMIN_X, get("/api/v1/staff/{id}", world.user(ADMIN_X))).andExpect(status().isNotFound());
		perform(ADMIN_X, get("/api/v1/staff/{id}", world.user(PARENT_A))).andExpect(status().isNotFound());
	}

	// D18: first sign-in with a temporary password

	@Test
	void aNewStaffMemberMustReplaceTheTemporaryPasswordFirst() throws Exception {
		JsonNode created = body(perform(ADMIN_X, jsonBody(post("/api/v1/staff"),
				Map.of("firstName", "Awa", "lastName", "Nouvelle", "phone", "699 11 22 33")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.staff.phone").value("+237699112233"))
			.andExpect(jsonPath("$.staff.language").value("FR"))
			.andExpect(jsonPath("$.staff.passwordChangeRequired").value(true)));
		String temporary = created.get("temporaryPassword").asString();
		assertThat(temporary).hasSize(12);
		assertThat(jdbc.queryForObject("SELECT password_hash FROM app_user WHERE phone = '+237699112233'", String.class))
			.startsWith("$2").doesNotContain(temporary);

		JsonNode tokens = login("699112233", temporary).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.passwordChangeRequired").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString()
			.transform(json::readTree);
		String access = tokens.get("accessToken").asString();

		mockMvc.perform(get("/api/v1/classes").header("Authorization", "Bearer " + access))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("auth.password_change_required"));
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

		changePassword(access, "wrong", "Nouveau-Pass1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.wrong_password"))
			.andExpect(jsonPath("$.field").value("currentPassword"));
		changePassword(access, temporary, temporary).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.password_unchanged"));
		changePassword(access, temporary, "court").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("newPassword"));

		JsonNode fresh = body(changePassword(access, temporary, "Nouveau-Pass1").andExpect(status().isOk())
			.andExpect(jsonPath("$.user.passwordChangeRequired").value(false)));
		// past the gate: no role yet, so the plain 403
		mockMvc.perform(get("/api/v1/classes").header("Authorization", "Bearer " + fresh.get("accessToken").asString()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("auth.forbidden"));
		// every older session ends
		refresh(tokens.get("refreshToken").asString()).andExpect(status().isUnauthorized());
		refresh(fresh.get("refreshToken").asString()).andExpect(status().isOk());
		login("699112233", temporary).andExpect(status().isUnauthorized());
		login("699112233", "Nouveau-Pass1").andExpect(status().isOk());
	}

	@Test
	void aPasswordResetEndsTheSessionsAndRequiresANewPassword() throws Exception {
		UUID id = createStaff("Bruno", "699 44 55 66");
		String temporary = body(perform(ADMIN_X, post("/api/v1/staff/{id}/password-reset", id))).get("temporaryPassword")
			.asString();
		JsonNode tokens = body(login("699445566", temporary));
		changePassword(tokens.get("accessToken").asString(), temporary, "Bruno-Pass1").andExpect(status().isOk());
		JsonNode signedIn = body(login("699445566", "Bruno-Pass1"));

		String again = body(perform(ADMIN_X, post("/api/v1/staff/{id}/password-reset", id)).andExpect(status().isOk()))
			.get("temporaryPassword")
			.asString();

		assertThat(again).isNotEqualTo(temporary);
		refresh(signedIn.get("refreshToken").asString()).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/classes").header("Authorization", "Bearer " + signedIn.get("accessToken").asString()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("auth.password_change_required"));
		login("699445566", "Bruno-Pass1").andExpect(status().isUnauthorized());
	}

	@Test
	void aDisabledAccountIsSignedOutAtOnce() throws Exception {
		String token = world.token(TEACHER_A);
		perform(ADMIN_X, jsonBody(put("/api/v1/staff/{id}/status", world.user(TEACHER_A)), Map.of("status", "DISABLED")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DISABLED"));

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
	}

	// Contact rules (D15, NFR-9)

	@Test
	void aStaffMemberNeedsACameroonPhoneOrAnEmailUniqueInTheSchool() throws Exception {
		perform(ADMIN_X, jsonBody(post("/api/v1/staff"), Map.of("firstName", "A", "lastName", "B")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("staff.contact_required"));
		perform(ADMIN_X, jsonBody(post("/api/v1/staff"), Map.of("firstName", "A", "lastName", "B", "phone", "+33612345678")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.phone"))
			.andExpect(jsonPath("$.field").value("phone"));

		createStaff("Premier", "699 77 88 99");
		perform(ADMIN_X, jsonBody(post("/api/v1/staff"), Map.of("firstName", "A", "lastName", "B", "phone", "+237699778899")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.phone_taken"));
		String takenEmail = jdbc.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class,
				world.user(DM_1));
		perform(ADMIN_X, jsonBody(post("/api/v1/staff"),
				Map.of("firstName", "A", "lastName", "B", "email", takenEmail.toUpperCase())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.email_taken"));
		// D15: the same phone is fine in another school
		perform(ADMIN_Y, jsonBody(post("/api/v1/staff"), Map.of("firstName", "A", "lastName", "B", "phone", "699778899")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.staff.language").value("EN"));
	}

	// Roles and blocks (D5)

	@Test
	void rolesFollowTheBlockRules() throws Exception {
		UUID teacher = world.user(TEACHER_A);
		addRole(teacher, Map.of("role", "DM", "blockId", world.block1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.block_has_dm"));
		addRole(teacher, Map.of("role", "DM")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("blockId"));
		addRole(teacher, Map.of("role", "COUNSELLOR", "blockId", world.block1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("staff.block_not_expected"));
		addRole(teacher, Map.of("role", "ADMIN")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("staff.role_not_allowed"));
		addRole(teacher, Map.of("role", "TEACHER")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.role_duplicate"));
		UUID blockY = jdbc.queryForObject("SELECT id FROM block WHERE school_id = ?", UUID.class, world.schoolY);
		addRole(teacher, Map.of("role", "ASSISTANT_DM", "blockId", blockY)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("reference.unknown"));
	}

	@Test
	void aNewBlockWidensTheScopeAtOnce() throws Exception {
		perform(TEACHER_A, get("/api/v1/classes/{id}", world.classB)).andExpect(status().isNotFound());

		addRole(world.user(TEACHER_A), Map.of("role", "ASSISTANT_DM", "blockId", world.block2))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.blockId").value(world.block2.toString()));

		perform(TEACHER_A, get("/api/v1/classes/{id}", world.classB)).andExpect(status().isOk());
	}

	@Test
	void theTeacherRoleStaysWhileClassesAreAssigned() throws Exception {
		UUID teacher = world.user(TEACHER_A);
		UUID role = jdbc.queryForObject("SELECT id FROM role_assignment WHERE user_id = ?", UUID.class, teacher);

		perform(ADMIN_X, delete("/api/v1/staff/{id}/roles/{role}", teacher, role)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.teaching_assigned"));
	}

	// Classes taught (BR-1, D1)

	@Test
	void classesAreAssignedToTeachersOnly() throws Exception {
		perform(ADMIN_X, teaching(world.user(COUNSELLOR_X), world.classA, "Maths")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.not_a_teacher"));
		perform(ADMIN_X, teaching(world.user(TEACHER_A), world.classC, "Maths")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("reference.unknown"))
			.andExpect(jsonPath("$.field").value("classId"));
		perform(ADMIN_X, teaching(world.user(TEACHER_A), world.classA, "Maths")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("staff.teaching_duplicate"));
	}

	@Test
	void aNewClassAppliesAtOnceAndAClassInTheTimetableStays() throws Exception {
		UUID teacher = world.user(TEACHER_A);
		perform(ADMIN_X, teaching(teacher, world.classB, "Physique")).andExpect(status().isCreated());
		perform(TEACHER_A, get("/api/v1/classes/{id}", world.classB)).andExpect(status().isOk());

		UUID maths = jdbc.queryForObject("SELECT id FROM teaching_assignment WHERE teacher_id = ? AND subject = 'Maths'",
				UUID.class, teacher);
		perform(ADMIN_X, delete("/api/v1/staff/{id}/teaching/{t}", teacher, maths)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("in_use"));

		jdbc.update("DELETE FROM timetable_slot WHERE id = ?", world.slotA);
		perform(ADMIN_X, delete("/api/v1/staff/{id}/teaching/{t}", teacher, maths)).andExpect(status().isNoContent());
		perform(TEACHER_A, get("/api/v1/classes/{id}", world.classA)).andExpect(status().isNotFound());
	}

	// Audit (NFR-11, NFR-9)

	@Test
	void everyAccountChangeAndAssignmentIsAuditedWithoutSecrets() throws Exception {
		JsonNode created = body(perform(ADMIN_X, jsonBody(post("/api/v1/staff"),
				Map.of("firstName", "Chantal", "lastName", "Audit", "phone", "699 12 12 12"))));
		UUID id = UUID.fromString(created.get("staff").get("id").asString());
		String temporary = created.get("temporaryPassword").asString();
		perform(ADMIN_X, jsonBody(post("/api/v1/staff/{id}/roles", id), Map.of("role", "TEACHER")));
		perform(ADMIN_X, teaching(id, world.classA, "Histoire"));
		perform(ADMIN_X, post("/api/v1/staff/{id}/password-reset", id));

		var entries = jdbc.queryForList("""
				SELECT entity, action, actor_id, coalesce(before::text, '') || coalesce(after::text, '') AS snapshot
				FROM audit_log WHERE school_id = ? AND actor_id = ? ORDER BY at""", world.schoolX, world.user(ADMIN_X));
		assertThat(entries).extracting(e -> e.get("entity") + " " + e.get("action"))
			.containsExactly("app_user CREATE", "role_assignment CREATE", "teaching_assignment CREATE", "app_user UPDATE");
		assertThat(entries).extracting(e -> (String) e.get("snapshot"))
			.allSatisfy(snapshot -> assertThat(snapshot).doesNotContain(temporary).doesNotContain("699121212"))
			.anySatisfy(snapshot -> assertThat(snapshot).contains("+237*******12"));
	}

	// Helpers

	private UUID createStaff(String firstName, String phone) throws Exception {
		JsonNode created = body(perform(ADMIN_X, jsonBody(post("/api/v1/staff"),
				Map.of("firstName", firstName, "lastName", "Test", "phone", phone)))
			.andExpect(status().isCreated()));
		return UUID.fromString(created.get("staff").get("id").asString());
	}

	private ResultActions addRole(UUID staff, Map<String, ?> body) throws Exception {
		return perform(ADMIN_X, jsonBody(post("/api/v1/staff/{id}/roles", staff), body));
	}

	private MockHttpServletRequestBuilder teaching(UUID staff, UUID schoolClass, String subject) {
		return jsonBody(post("/api/v1/staff/{id}/teaching", staff), Map.of("classId", schoolClass, "subject", subject));
	}

	private ResultActions login(String login, String password) throws Exception {
		return mockMvc.perform(jsonBody(post("/api/v1/auth/login"),
				Map.of("schoolId", world.schoolX, "login", login, "password", password)));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(jsonBody(post("/api/v1/auth/refresh"), Map.of("refreshToken", refreshToken)));
	}

	private ResultActions changePassword(String access, String current, String next) throws Exception {
		return mockMvc.perform(jsonBody(post("/api/v1/me/password"), Map.of("currentPassword", current, "newPassword", next))
			.header("Authorization", "Bearer " + access));
	}

	private static java.util.List<String> lastNames(JsonNode staff) {
		java.util.List<String> names = new java.util.ArrayList<>();
		staff.forEach(s -> names.add(s.get("lastName").asString()));
		return names;
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder request, Map<String, ?> body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

}
