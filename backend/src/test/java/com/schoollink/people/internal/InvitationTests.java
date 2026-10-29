package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.people.InvitationIssued;
import com.schoollink.people.Invitations;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-A4 invitation codes (task 1.13, D7, D13, NFR-9, NFR-11). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@RecordApplicationEvents
class InvitationTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	Invitations invitations;

	@Autowired
	ApplicationEvents events;

	TestWorld world;

	/** A second guardian of student A, without an app account (D7). */
	UUID guardian;

	/** TestWorld's guardian, linked to PARENT_A's account. */
	UUID guardianWithAccount;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone)
				VALUES (?, 'Tuteur Sans Compte', '+237690000002') RETURNING id""", UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation)
				VALUES (?, ?, ?, 'FATHER')""", world.schoolX, world.studentA, guardian);
		guardianWithAccount = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class,
				world.user(PARENT_A));
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolIssuesCodesForAGuardian() {
		return PermissionMatrix.row("POST guardian invitation", w -> post("/api/v1/guardians/{id}/invitations", guardian))
			.expect(201, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminOfTheSchoolIssuesCodesForAClass() {
		return PermissionMatrix.row("POST class invitations", w -> post("/api/v1/classes/{id}/invitations", w.classA))
			.expect(201, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void onlyTheHashOfTheCodeIsStored() throws Exception {
		JsonNode issued = issue(guardian);
		String code = issued.get("code").asString();
		assertThat(code).matches("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}");
		assertThat(issued.get("guardianName").asString()).isEqualTo("Tuteur Sans Compte");

		var row = jdbc.queryForMap("SELECT * FROM invitation_code WHERE guardian_id = ?", guardian);
		assertThat(row.get("code_hash")).isEqualTo(InvitationService.hash(code))
			.asString()
			.hasSize(64)
			.doesNotContain(InvitationService.normalize(code));
		assertThat(row.values()).noneSatisfy(v -> assertThat(String.valueOf(v)).contains(InvitationService.normalize(code)));
		// NFR-11: audited, without the code
		String audit = jdbc.queryForObject("""
				SELECT after::text FROM audit_log WHERE entity = 'invitation_code' AND entity_id = ?""", String.class,
				row.get("id").toString());
		assertThat(audit).contains(guardian.toString()).doesNotContain(code).doesNotContain(InvitationService.normalize(code));
	}

	@Test
	void theCodeIsHandedToTheSmsOutboxButNeverPrinted() throws Exception { // D13
		String code = issue(guardian).get("code").asString();

		assertThat(events.stream(InvitationIssued.class)).singleElement().satisfies(e -> {
			assertThat(e.guardianId()).isEqualTo(guardian);
			assertThat(e.code()).isEqualTo(code);
			assertThat(e.toString()).doesNotContain(code);
		});
	}

	@Test
	void aCodeExpiresAfterThirtyDays() throws Exception {
		String code = issue(guardian).get("code").asString();
		Instant expiresAt = jdbc.queryForObject("SELECT expires_at FROM invitation_code WHERE guardian_id = ?",
				Timestamp.class, guardian).toInstant();
		assertThat(expiresAt).isCloseTo(Instant.now().plus(30, ChronoUnit.DAYS), within(1, ChronoUnit.MINUTES));
		assertThat(invitations.find(code)).isPresent();

		jdbc.update("""
				UPDATE invitation_code SET created_at = now() - interval '31 days', expires_at = now() - interval '1 day'
				WHERE guardian_id = ?""", guardian);

		assertThat(invitations.find(code)).isEmpty();
		assertThat(invitations.use(code)).isEmpty();
	}

	@Test
	void aCodeWorksOnce() throws Exception {
		String code = issue(guardian).get("code").asString();

		assertThat(invitations.use(code)).hasValueSatisfying(i -> {
			assertThat(i.guardianId()).isEqualTo(guardian);
			assertThat(i.schoolId()).isEqualTo(world.schoolX);
		});
		assertThat(invitations.use(code)).isEmpty();
		assertThat(invitations.find(code)).isEmpty();
	}

	@Test
	void aCodeIsReadWithoutCaseSpacesOrDashes() throws Exception {
		String code = issue(guardian).get("code").asString();
		String typed = " " + code.substring(0, 4).toLowerCase() + " " + code.substring(5) + " ";

		assertThat(invitations.find(typed)).isPresent();
		assertThat(invitations.find("")).isEmpty();
		assertThat(invitations.find(null)).isEmpty();
	}

	@Test
	void aNewCodeRevokesThePreviousOne() throws Exception {
		String first = issue(guardian).get("code").asString();
		String second = issue(guardian).get("code").asString();

		assertThat(invitations.find(first)).isEmpty();
		assertThat(invitations.find(second)).isPresent();
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM invitation_code WHERE guardian_id = ? AND revoked_at IS NOT NULL", Integer.class,
				guardian)).isOne();
	}

	@Test
	void aGuardianWithAnAccountGetsNoCode() throws Exception { // D7
		perform(ADMIN_X, post("/api/v1/guardians/{id}/invitations", guardianWithAccount))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("invitation.guardian_has_account"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM invitation_code", Integer.class)).isZero();
	}

	@Test
	void anUnknownGuardianOrClassIsNotFound() throws Exception {
		perform(ADMIN_X, post("/api/v1/guardians/{id}/invitations", UUID.randomUUID())).andExpect(status().isNotFound());
		perform(ADMIN_X, post("/api/v1/classes/{id}/invitations", world.classC)).andExpect(status().isNotFound());
	}

	@Test
	void aClassGetsCodesForItsGuardiansWithoutAccount() throws Exception {
		String body = perform(ADMIN_X, post("/api/v1/classes/{id}/invitations", world.classA))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode result = json.readTree(body);

		assertThat(result.get("skippedWithAccount").asInt()).isOne();
		assertThat(result.get("issued")).hasSize(1);
		assertThat(result.get("issued").get(0).get("guardianId").asString()).isEqualTo(guardian.toString());
		assertThat(invitations.find(result.get("issued").get(0).get("code").asString())).isPresent();
	}

	@Test
	void anEmptyClassGetsNoCode() throws Exception {
		perform(ADMIN_X, post("/api/v1/classes/{id}/invitations", world.classB)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.issued").isEmpty())
			.andExpect(jsonPath("$.skippedWithAccount").value(0));
	}

	private JsonNode issue(UUID guardianId) throws Exception {
		String body = perform(ADMIN_X, post("/api/v1/guardians/{id}/invitations", guardianId))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(body);
	}

	private ResultActions perform(Actor actor, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

}
