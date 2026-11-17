package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.util.HashMap;
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

/** D43: the admin keeps the guardians up to date (NFR-8, NFR-9, NFR-11). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GuardianAdminTests {

	static final String OLD_PHONE = "+237690000001";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	TestWorld world;

	/** Student A's mother, the primary guardian: has the app (PARENT_A). */
	UUID mother;

	/** Student A's father: no app account. */
	UUID father;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		mother = jdbc.queryForObject("SELECT id FROM guardian WHERE phone = ?", UUID.class, OLD_PHONE);
		// as after the first sign-in: the account signs in with the guardian's phone (D24)
		jdbc.update("UPDATE app_user SET phone = ? WHERE id = ?", OLD_PHONE, world.user(PARENT_A));
		father = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone) VALUES (?, 'Père Fictif', '+237690000002') RETURNING id""",
				UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', false)""", world.schoolX, world.studentA, father);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolManagesGuardians() { // NFR-8
		Actor[] others = { TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER };
		return Stream.of(
				PermissionMatrix.row("PUT guardian", w -> body(put("/api/v1/guardians/{id}", father), details(Map.of())))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix
					.row("POST link", w -> body(post("/api/v1/students/{id}/guardians", w.studentA), newGuardian(Map.of())))
					.expect(201, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix
					.row("PUT link",
							w -> body(put("/api/v1/students/{id}/guardians/{g}", w.studentA, father),
									Map.of("relation", "GUARDIAN", "primary", false)))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix.row("DELETE link", w -> delete("/api/v1/students/{id}/guardians/{g}", w.studentA, father))
					.expect(200, ADMIN_X)
					.expect(404, ADMIN_Y),
				PermissionMatrix.row("POST password resets", w -> post("/api/v1/guardians/{id}/password-resets", mother))
					.expect(204, ADMIN_X)
					.expect(404, ADMIN_Y))
			.flatMap(row -> row.expect(403, others).expect(401, ANONYMOUS).tests(mockMvc, world));
	}

	@Test
	void theAdminLetsAParentResetTheirPasswordAgain() throws Exception { // D46
		UUID parent = world.user(PARENT_A);
		for (int i = 0; i < 3; i++) {
			jdbc.update("""
					INSERT INTO parent_sign_in (id, school_id, user_id, otp_hash, created_at, expires_at, verified_at,
					                            password_reset)
					VALUES (gen_random_uuid(), ?, ?, 'fake-otp-hash', now() - interval '1 hour', now() + interval '1 hour',
					        now() - interval '1 hour', true)""", world.schoolX, parent);
		}
		String motherLocked = "$.guardians[?(@.id == '%s')].resetsLocked".formatted(mother);
		perform(get("/api/v1/students/{id}", world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath(motherLocked).value(true))
			.andExpect(jsonPath("$.guardians[?(@.id == '%s')].resetsLocked".formatted(father)).value(false));

		perform(post("/api/v1/guardians/{id}/password-resets", mother)).andExpect(status().isNoContent());

		perform(get("/api/v1/students/{id}", world.studentA)).andExpect(jsonPath(motherLocked).value(false));
		// NFR-11: who allowed it, for which account
		assertThat(jdbc.queryForList("""
				SELECT actor_id::text || ' ' || entity_id FROM audit_log
				WHERE entity = 'password_reset_allowance' AND action = 'CREATE'""", String.class))
			.containsExactly(world.user(ADMIN_X) + " " + parent);
		// a guardian without the app has no password to reset
		perform(post("/api/v1/guardians/{id}/password-resets", father)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("guardians.no_account"));
	}

	@Test
	void aGuardiansNameAndLanguageChange() throws Exception { // BR-20
		perform(body(put("/api/v1/guardians/{id}", father), details(Map.of("name", " Papa Inventé ", "language", "EN"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Papa Inventé"))
			.andExpect(jsonPath("$.language").value("EN"))
			.andExpect(jsonPath("$.phone").value("+237690000002"))
			.andExpect(jsonPath("$.hasAccount").value(false));

		// NFR-9, NFR-11: the fields that changed, never the values
		String audit = jdbc.queryForObject(
				"SELECT after::text FROM audit_log WHERE entity = 'guardian' AND entity_id = ?", String.class,
				father.toString());
		assertThat(audit).contains("name", "language").doesNotContain("Papa", "690000002", "phone");
	}

	@Test
	void aNewPhoneWithoutAnAccountIsJustStored() throws Exception {
		perform(body(put("/api/v1/guardians/{id}", father), details(Map.of("phone", "690 000 099"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("+237690000099"));

		String audit = jdbc.queryForObject(
				"SELECT after::text FROM audit_log WHERE entity = 'guardian' AND entity_id = ?", String.class,
				father.toString());
		assertThat(audit).contains("phone").doesNotContain("690000099", "690000002");
	}

	@Test
	void aNewPhoneSignsTheParentOutEverywhere() throws Exception { // D24, D43
		UUID parent = world.user(PARENT_A);
		jdbc.update("""
				INSERT INTO refresh_token (user_id, family_id, token_hash, expires_at)
				VALUES (?, gen_random_uuid(), 'fake-hash', now() + interval '30 days')""", parent);
		jdbc.update("INSERT INTO device_token (school_id, user_id, token, platform) VALUES (?, ?, 'fake-token', 'ANDROID')",
				world.schoolX, parent);
		// a code already sent to the old number, not used yet
		jdbc.update("""
				INSERT INTO parent_sign_in (id, school_id, user_id, otp_hash, created_at, expires_at)
				VALUES (gen_random_uuid(), ?, ?, 'fake-otp-hash', now(), now() + interval '10 minutes')""", world.schoolX,
				parent);
		int version = jdbc.queryForObject("SELECT credentials_version FROM app_user WHERE id = ?", Integer.class, parent);

		perform(body(put("/api/v1/guardians/{id}", mother), details(Map.of("name", "Parent Fictif", "phone", "+237690000099"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("+237690000099"))
			.andExpect(jsonPath("$.hasAccount").value(true));

		// the account signs in with the new number from now on
		assertThat(jdbc.queryForObject("SELECT phone FROM app_user WHERE id = ?", String.class, parent))
			.isEqualTo("+237690000099");
		assertThat(jdbc.queryForObject("SELECT credentials_version FROM app_user WHERE id = ?", Integer.class, parent))
			.isEqualTo(version + 1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Integer.class, parent))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_token WHERE user_id = ?", Integer.class, parent))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_sign_in WHERE user_id = ?", Integer.class, parent))
			.isZero();
	}

	@Test
	void theSamePhoneAgainSignsNobodyOut() throws Exception {
		UUID parent = world.user(PARENT_A);
		jdbc.update("INSERT INTO device_token (school_id, user_id, token, platform) VALUES (?, ?, 'fake-token', 'ANDROID')",
				world.schoolX, parent);

		perform(body(put("/api/v1/guardians/{id}", mother), details(Map.of("name", "Maman Inventée", "phone", "690000001"))))
			.andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_token WHERE user_id = ?", Integer.class, parent))
			.isOne();
	}

	@Test
	void aPhoneSomebodyElseHasIsRefused() throws Exception { // D15, D21
		perform(body(put("/api/v1/guardians/{id}", father), details(Map.of("phone", OLD_PHONE))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("guardians.phone_taken"))
			.andExpect(jsonPath("$.message").value("Un autre tuteur de l'école a déjà ce numéro. Ajoutez ce tuteur à l'élève."));
		// a staff account signs in with a password
		jdbc.update("UPDATE app_user SET phone = '+237690000055' WHERE id = ?", world.user(TEACHER_A));
		perform(body(put("/api/v1/guardians/{id}", father), details(Map.of("phone", "+237690000055"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("guardians.phone_staff"));
		perform(body(put("/api/v1/guardians/{id}", father), details(Map.of("phone", "+33612345678"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.phone"))
			.andExpect(jsonPath("$.field").value("phone"));
		assertThat(jdbc.queryForObject("SELECT phone FROM guardian WHERE id = ?", String.class, father))
			.isEqualTo("+237690000002");
	}

	@Test
	void aGuardianIsAddedToAStudentAndCanBecomeThePrimary() throws Exception {
		perform(body(post("/api/v1/students/{id}/guardians", world.studentA), newGuardian(Map.of("primary", true))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.guardians.length()").value(3))
			.andExpect(jsonPath("$.guardians[0].name").value("Tante Inventée"))
			.andExpect(jsonPath("$.guardians[0].primary").value(true))
			.andExpect(jsonPath("$.guardians[1].primary").value(false))
			.andExpect(jsonPath("$.guardians[2].primary").value(false));

		// already a guardian of this student
		perform(body(post("/api/v1/students/{id}/guardians", world.studentA), newGuardian(Map.of())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.guardian_linked"));
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_log WHERE entity = 'student_guardian' AND action = 'CREATE'""", Integer.class))
			.isOne();
	}

	@Test
	void theRelationAndThePrimaryChange() throws Exception {
		perform(body(put("/api/v1/students/{id}/guardians/{g}", world.studentA, father),
				Map.of("relation", "GUARDIAN", "primary", true)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians[0].id").value(father.toString()))
			.andExpect(jsonPath("$.guardians[0].relation").value("GUARDIAN"))
			.andExpect(jsonPath("$.guardians[0].primary").value(true))
			.andExpect(jsonPath("$.guardians[1].primary").value(false));

		// a student always has one primary guardian
		perform(body(put("/api/v1/students/{id}/guardians/{g}", world.studentA, father),
				Map.of("relation", "GUARDIAN", "primary", false)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("students.primary_required"));
	}

	@Test
	void aGuardianIsRemovedFromAStudentButNeverTheLastOne() throws Exception {
		// the primary goes: the one who stays becomes it
		perform(delete("/api/v1/students/{id}/guardians/{g}", world.studentA, mother)).andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians.length()").value(1))
			.andExpect(jsonPath("$.guardians[0].id").value(father.toString()))
			.andExpect(jsonPath("$.guardians[0].primary").value(true));
		// the guardian stays, with their account
		assertThat(jdbc.queryForObject("SELECT count(*) FROM guardian WHERE id = ?", Integer.class, mother)).isOne();
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_log WHERE entity = 'student_guardian' AND action = 'DELETE'""", Integer.class))
			.isOne();

		perform(delete("/api/v1/students/{id}/guardians/{g}", world.studentA, father)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("students.last_guardian"));
		perform(delete("/api/v1/students/{id}/guardians/{g}", world.studentA, mother)).andExpect(status().isNotFound());
	}

	@Test
	void removingThePrimaryOfThreeAsksWhoBecomesIt() throws Exception {
		perform(body(post("/api/v1/students/{id}/guardians", world.studentA), newGuardian(Map.of())))
			.andExpect(status().isCreated());

		perform(delete("/api/v1/students/{id}/guardians/{g}", world.studentA, mother)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("students.primary_required"));
		perform(delete("/api/v1/students/{id}/guardians/{g}", world.studentA, mother).param("primary", father.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.guardians.length()").value(2))
			.andExpect(jsonPath("$.guardians[0].id").value(father.toString()));
	}

	private static Map<String, Object> details(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(Map.of("name", "Père Fictif", "language", "FR", "phone", "+237690000002"));
		body.putAll(overrides);
		return body;
	}

	private static Map<String, Object> newGuardian(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(
				Map.of("phone", "+237690000077", "name", "Tante Inventée", "relation", "GUARDIAN", "primary", false));
		body.putAll(overrides);
		return body;
	}

	private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Map<String, Object> body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(ADMIN_X)));
	}

}
