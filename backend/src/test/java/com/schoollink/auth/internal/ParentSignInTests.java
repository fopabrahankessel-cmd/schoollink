package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.notification.ActivationSms;
import com.schoollink.shared.ratelimit.RateLimiter;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-P1, D24, D45: a parent signs in with their school, phone and password. An SMS code is sent only
 * the first time, which makes the account, and when the password is forgotten; with it the parent
 * chooses their password (NFR-7, NFR-9, NFR-11). Phones and passwords are made up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, ParentSignInTests.Sms.class })
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class ParentSignInTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	static final String PHONE = "+237690000401";

	static final String FIRST_PHONE = "+237690000450";

	static final String PASSWORD = "mot-de-passe-parent";

	static final String AUDITS = "SELECT entity || ' ' || action FROM audit_log WHERE entity_id IN (?, ?)";

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

	@Autowired
	RateLimiter limiter;

	@Autowired
	RecordingSms sms;

	@Autowired
	PasswordEncoder passwords;

	TestWorld world;

	UUID parent;

	@BeforeEach
	void createWorld() {
		limiter.clear();
		sms.reset();
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		parent = user(world.schoolX, PHONE, null, "EN", "PARENT");
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aParentWhoForgotTheirPasswordChoosesANewOneWithAnSmsCode(CapturedOutput output) throws Exception { // D45
		jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", passwords.encode("ancien-mot-de-passe"), parent);
		String oldRefresh = body(login(PHONE, "ancien-mot-de-passe").andExpect(status().isOk())).get("refreshToken")
			.asString();

		JsonNode started = body(signIn(world.schoolX, "690 00 04 01").andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresAt").value("2026-11-02T07:50:00Z")));
		assertThat(sms.sent).singleElement().satisfies(sent -> {
			assertThat(sent.phone()).isEqualTo(PHONE);
			assertThat(sent.language()).isEqualTo("EN");
			assertThat(sent.code()).matches("[0-9]{6}");
		});
		String otp = sms.sent.getFirst().code();
		assertThat(jdbc.queryForObject("SELECT otp_hash FROM parent_sign_in", String.class)).doesNotContain(otp);

		JsonNode tokens = body(verify(started.get("activationId").asString(), otp).andExpect(status().isOk()));

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get("accessToken").asString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(parent.toString()))
			.andExpect(jsonPath("$.roles[0]").value("PARENT"));
		assertThat(tokens.get("refreshToken").asString()).isNotBlank();
		// the code works once
		verify(started.get("activationId").asString(), otp).andExpect(status().isBadRequest());
		// the new password replaces the old one, and the other sessions end
		login(PHONE, "ancien-mot-de-passe").andExpect(status().isUnauthorized());
		login(PHONE, PASSWORD).andExpect(status().isOk());
		send(Map.of("refreshToken", oldRefresh), "/api/v1/auth/refresh").andExpect(status().isUnauthorized());
		// NFR-9, NFR-11: audited, and neither the phone, the code nor the password is written anywhere
		assertThat(jdbc.queryForList(AUDITS, String.class, parent.toString(), parent.toString())).contains("app_user UPDATE");
		assertThat(output.getAll()).doesNotContain("690000401", "690 00 04 01", otp, PASSWORD);

		// NFR-7: the access token's 15 minutes (and 1 minute of skew) run on the clock it was issued with
		clock.set(NOW.plus(Duration.ofMinutes(17)));
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get("accessToken").asString()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aGuardianSignsInForTheFirstTimeWithOneCode(CapturedOutput output) throws Exception { // UC-P1
		UUID guardian = guardianOfStudentA("Parent Nouveau", FIRST_PHONE, "EN");

		String signInId = body(signIn(world.schoolX, "690 00 04 50").andExpect(status().isOk())).get("activationId")
			.asString();

		assertThat(sms.sent).singleElement().satisfies(sent -> {
			assertThat(sent.phone()).isEqualTo(FIRST_PHONE);
			assertThat(sent.language()).isEqualTo("EN");
		});
		// the account waits for the code: a number alone proves nothing
		assertThat(accountsWith(FIRST_PHONE)).isZero();
		String otp = sms.sent.getFirst().code();

		JsonNode tokens = body(verify(signInId, otp).andExpect(status().isOk()));

		UUID user = UUID.fromString(tokens.get("user").get("id").asString());
		assertThat(tokens.get("user").get("roles").toString()).isEqualTo("[\"PARENT\"]");
		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian)).isEqualTo(user);
		assertThat(jdbc.queryForMap("SELECT first_name, last_name, language, password_hash FROM app_user WHERE id = ?", user))
			.containsEntry("first_name", "Parent")
			.containsEntry("last_name", "Nouveau")
			.containsEntry("language", "EN");
		// D45: the password the parent chose, hashed
		String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, user);
		assertThat(passwords.matches(PASSWORD, hash)).isTrue();
		// NFR-11, NFR-9: audited, without the phone or the password
		assertThat(audits(user, guardian)).containsExactlyInAnyOrder("app_user CREATE", "app_user UPDATE",
				"guardian UPDATE");
		assertThat(output.getAll()).doesNotContain("690000450", otp, PASSWORD);

		// D45: from now on the parent signs in with phone and password, without an SMS
		JsonNode again = body(login("690 00 04 50", PASSWORD).andExpect(status().isOk()));
		assertThat(again.get("user").get("id").asString()).isEqualTo(user.toString());
		assertThat(again.get("user").get("roles").toString()).isEqualTo("[\"PARENT\"]");
		assertThat(sms.sent).hasSize(1);
		assertThat(accountsWith(FIRST_PHONE)).isOne();
	}

	@Test
	void aPasswordIsChosenWithTheCode() throws Exception { // D45
		String signInId = body(signIn(world.schoolX, PHONE)).get("activationId").asString();
		String otp = sms.sent.getFirst().code();

		send(Map.of("activationId", signInId, "otp", otp), "/api/v1/auth/parent/sign-in/verify")
			.andExpect(status().isBadRequest());
		verify(signInId, otp, "court").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("password"));

		// a refused password does not use up the code
		verify(signInId, otp).andExpect(status().isOk());
	}

	@Test
	void aWrongCodeMakesNoAccount() throws Exception { // NFR-7
		UUID guardian = guardianOfStudentA("Parent Nouveau", FIRST_PHONE, "FR");
		String signInId = body(signIn(world.schoolX, FIRST_PHONE)).get("activationId").asString();
		String wrong = sms.sent.getFirst().code().equals("000000") ? "111111" : "000000";

		verify(signInId, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.invalid_otp"));

		assertThat(accountsWith(FIRST_PHONE)).isZero();
		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian)).isNull();
	}

	@Test
	void theFirstSignInHasTheSameLimits() throws Exception { // NFR-7: SMS cost, no flooding, no guessing
		guardianOfStudentA("Parent Nouveau", FIRST_PHONE, "FR");
		String first = body(signIn(world.schoolX, FIRST_PHONE)).get("activationId").asString();
		String second = body(signIn(world.schoolX, FIRST_PHONE)).get("activationId").asString();
		// only the newest code works
		verify(first, sms.sent.get(0).code()).andExpect(status().isBadRequest());

		String wrong = sms.sent.get(1).code().equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 5; i++) {
			verify(second, wrong).andExpect(status().isBadRequest());
		}
		verify(second, sms.sent.get(1).code()).andExpect(status().isTooManyRequests());

		signIn(world.schoolX, FIRST_PHONE).andExpect(status().isOk());
		signIn(world.schoolX, FIRST_PHONE).andExpect(status().isTooManyRequests());
		assertThat(sms.sent).hasSize(3);
	}

	@Test
	void aCodeExpiresBeforeTheFirstSignIn() throws Exception {
		guardianOfStudentA("Parent Nouveau", FIRST_PHONE, "FR");
		String signInId = body(signIn(world.schoolX, FIRST_PHONE)).get("activationId").asString();

		clock.set(NOW.plus(Duration.ofMinutes(10)));

		verify(signInId, sms.sent.getFirst().code()).andExpect(status().isBadRequest());
		assertThat(accountsWith(FIRST_PHONE)).isZero();
	}

	@Test
	void aGuardianOfNoStudentGetsNoCode() throws Exception { // only the school's parents
		jdbc.update("INSERT INTO guardian (school_id, name, phone) VALUES (?, 'Sans Enfant', ?)", world.schoolX, FIRST_PHONE);

		signIn(world.schoolX, FIRST_PHONE).andExpect(status().isOk());

		assertThat(sms.sent).isEmpty();
	}

	@Test
	void aGuardianIsLookedUpInTheChosenSchool() throws Exception { // D15, NFR-8
		guardianOfStudentA("Parent Nouveau", FIRST_PHONE, "FR");

		signIn(world.schoolY, FIRST_PHONE).andExpect(status().isOk());

		assertThat(sms.sent).isEmpty();
	}

	@Test
	void anSmsCodeNeverOpensAStaffAccount() throws Exception { // D21: staff prove themselves with their password
		guardianOfStudentA("Parent Prof", FIRST_PHONE, "FR");
		jdbc.update("UPDATE app_user SET phone = ? WHERE id = ?", FIRST_PHONE, world.user(TEACHER_A));

		signIn(world.schoolX, FIRST_PHONE).andExpect(status().isOk());

		assertThat(sms.sent).isEmpty();
	}

	@Test
	void aDisabledAccountBlocksTheGuardiansSignIn() throws Exception { // D18: a code never brings it back
		guardianOfStudentA("Parent Bloque", FIRST_PHONE, "FR");
		jdbc.update("UPDATE app_user SET phone = ?, status = 'DISABLED' WHERE id = ?", FIRST_PHONE, world.user(TEACHER_A));

		signIn(world.schoolX, FIRST_PHONE).andExpect(status().isOk());

		assertThat(sms.sent).isEmpty();
	}

	@Test
	void aStaffAccountMadeAfterTheCodeWasSentIsNotOpenedByIt() throws Exception { // D21
		guardianOfStudentA("Parent Prof", FIRST_PHONE, "FR");
		String signInId = body(signIn(world.schoolX, FIRST_PHONE)).get("activationId").asString();
		jdbc.update("UPDATE app_user SET phone = ? WHERE id = ?", FIRST_PHONE, world.user(TEACHER_A));

		verify(signInId, sms.sent.getFirst().code()).andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM role_assignment WHERE user_id = ? AND role = 'PARENT'",
				Integer.class, world.user(TEACHER_A))).isZero();
	}

	@Test
	void aStaffMemberWhoIsAlsoAParentIsLinkedWhenSigningInWithTheirPassword() throws Exception { // D21
		UUID guardian = guardianOfStudentA("Parent Prof", FIRST_PHONE, "FR");
		UUID teacher = world.user(TEACHER_A);
		jdbc.update("UPDATE app_user SET phone = ?, password_hash = ? WHERE id = ?", FIRST_PHONE,
				passwords.encode("mot-de-passe"), teacher);

		for (int i = 0; i < 2; i++) {
			JsonNode tokens = body(send(Map.of("schoolId", world.schoolX.toString(), "login", FIRST_PHONE, "password",
					"mot-de-passe"), "/api/v1/auth/login").andExpect(status().isOk()));
			assertThat(tokens.get("user").get("id").asString()).isEqualTo(teacher.toString());
			assertThat(tokens.get("user").get("roles").toString()).contains("PARENT", "TEACHER");
		}

		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian)).isEqualTo(teacher);
		assertThat(jdbc.queryForList("SELECT role FROM role_assignment WHERE user_id = ? ORDER BY role", String.class, teacher))
			.containsExactly("PARENT", "TEACHER");
		assertThat(accountsWith(FIRST_PHONE)).isOne();
		assertThat(audits(teacher, guardian)).containsExactly("guardian UPDATE"); // once, NFR-11
	}

	@Test
	void anUnknownNumberGetsTheSameAnswerAndNoSms() throws Exception { // NFR-7: nothing tells which numbers exist
		JsonNode started = body(signIn(world.schoolX, "+237690000499").andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresAt").value("2026-11-02T07:50:00Z")));

		assertThat(sms.sent).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_sign_in", Integer.class)).isZero();
		verify(started.get("activationId").asString(), "123456").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.invalid_otp"));
	}

	@Test
	void onlyParentAccountsSignInThisWay() throws Exception { // D21: staff use their password; D18
		user(world.schoolX, "+237690000402", "not-a-real-hash", "FR", "TEACHER");
		UUID both = user(world.schoolX, "+237690000403", "not-a-real-hash", "FR", "TEACHER");
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'PARENT')", world.schoolX, both);
		UUID disabled = user(world.schoolX, "+237690000404", null, "FR", "PARENT");
		jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", disabled);

		signIn(world.schoolX, "+237690000402").andExpect(status().isOk());
		signIn(world.schoolX, "+237690000403").andExpect(status().isOk());
		signIn(world.schoolX, "+237690000404").andExpect(status().isOk());

		assertThat(sms.sent).isEmpty();
	}

	@Test
	void theAccountIsLookedUpInTheChosenSchool() throws Exception { // D15
		signIn(world.schoolY, PHONE).andExpect(status().isOk());
		assertThat(sms.sent).isEmpty();

		UUID inY = user(world.schoolY, PHONE, null, "FR", "PARENT");
		String signInId = body(signIn(world.schoolY, PHONE)).get("activationId").asString();
		JsonNode tokens = body(verify(signInId, sms.sent.getFirst().code()).andExpect(status().isOk()));
		assertThat(tokens.get("user").get("id").asString()).isEqualTo(inY.toString());
	}

	@Test
	void guessingTheCodeIsStoppedAfterFiveTries() throws Exception { // NFR-7
		String signInId = body(signIn(world.schoolX, PHONE)).get("activationId").asString();
		String otp = sms.sent.getFirst().code();
		String wrong = otp.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			verify(signInId, wrong).andExpect(status().isBadRequest());
		}
		verify(signInId, otp).andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
		limiter.clear(); // counted in the database
		verify(signInId, otp).andExpect(status().isTooManyRequests());
	}

	@Test
	void aNewCodeReplacesThePreviousOneAndCodesExpire() throws Exception {
		String first = body(signIn(world.schoolX, PHONE)).get("activationId").asString();
		String second = body(signIn(world.schoolX, PHONE)).get("activationId").asString();
		verify(first, sms.sent.get(0).code()).andExpect(status().isBadRequest());

		clock.set(NOW.plus(Duration.ofMinutes(10)));
		verify(second, sms.sent.get(1).code()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.invalid_otp"));
	}

	@Test
	void atMostThreeCodesPerAccountPerHour() throws Exception { // NFR-7: SMS cost, no flooding
		for (int i = 0; i < 3; i++) {
			signIn(world.schoolX, PHONE).andExpect(status().isOk());
		}
		signIn(world.schoolX, PHONE).andExpect(status().isTooManyRequests());
		assertThat(sms.sent).hasSize(3);

		clock.set(NOW.plus(Duration.ofHours(1)).plusSeconds(1));
		signIn(world.schoolX, PHONE).andExpect(status().isOk());
	}

	@Test
	void atMostFiveCodesPerAccountPerDay() throws Exception { // NFR-7: no SMS flooding over a day either
		for (int i = 0; i < 5; i++) {
			clock.set(NOW.plus(Duration.ofHours(2L * i)));
			limiter.clear();
			signIn(world.schoolX, PHONE).andExpect(status().isOk());
		}
		clock.set(NOW.plus(Duration.ofHours(10)));
		signIn(world.schoolX, PHONE).andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", String.valueOf(Duration.ofHours(14).toSeconds())));
		assertThat(sms.sent).hasSize(5);

		clock.set(NOW.plus(Duration.ofDays(1)).plusSeconds(1));
		signIn(world.schoolX, PHONE).andExpect(status().isOk());
	}

	@Test
	void anAddressIsLimitedToTenRequestsPerQuarterHour() throws Exception { // NFR-7
		for (int i = 0; i < 10; i++) {
			signIn(world.schoolX, "+2376900005" + "%02d".formatted(i)).andExpect(status().isOk());
		}
		signIn(world.schoolX, PHONE).andExpect(status().isTooManyRequests());
	}

	@Test
	void anSmsThatCannotBeSentIsReported() throws Exception {
		sms.accept = false;
		signIn(world.schoolX, PHONE).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("auth.otp_not_sent"));
	}

	@Test
	void theRequestsAreValidated() throws Exception {
		send(Map.of("phone", PHONE), "/api/v1/auth/parent/sign-in").andExpect(status().isBadRequest());
		send(Map.of("schoolId", world.schoolX.toString()), "/api/v1/auth/parent/sign-in")
			.andExpect(status().isBadRequest());
		send(Map.of("otp", "123456"), "/api/v1/auth/parent/sign-in/verify").andExpect(status().isBadRequest());
	}

	private UUID guardianOfStudentA(String name, String phone, String language) {
		UUID guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, ?, ?, ?) RETURNING id""", UUID.class,
				world.schoolX, name, phone, language);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', false)""", world.schoolX, world.studentA, guardian);
		return guardian;
	}

	private int accountsWith(String phone) {
		return jdbc.queryForObject("SELECT count(*) FROM app_user WHERE phone = ?", Integer.class, phone);
	}

	private List<String> audits(UUID user, UUID guardian) {
		return jdbc.queryForList(AUDITS, String.class, user.toString(), guardian.toString());
	}

	private UUID user(UUID school, String phone, String passwordHash, String language, String role) {
		UUID id = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, password_hash, language)
				VALUES (?, 'Parent', 'Fictif', ?, ?, ?) RETURNING id""", UUID.class, school, phone, passwordHash,
				language);
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, ?)", school, id, role);
		return id;
	}

	private ResultActions signIn(UUID school, String phone) throws Exception {
		return send(Map.of("schoolId", school.toString(), "phone", phone), "/api/v1/auth/parent/sign-in");
	}

	private ResultActions verify(String signInId, String otp) throws Exception {
		return verify(signInId, otp, PASSWORD);
	}

	private ResultActions verify(String signInId, String otp, String password) throws Exception {
		return send(Map.of("activationId", signInId, "otp", otp, "password", password),
				"/api/v1/auth/parent/sign-in/verify");
	}

	private ResultActions login(String phone, String password) throws Exception {
		return send(Map.of("schoolId", world.schoolX.toString(), "login", phone, "password", password),
				"/api/v1/auth/login");
	}

	private ResultActions send(Map<String, Object> body, String path) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	record SentCode(String phone, String language, String code, Duration validity) {
	}

	/** Records the one-time codes instead of sending them. */
	static class RecordingSms implements ActivationSms {

		final List<SentCode> sent = new ArrayList<>();

		boolean accept = true;

		void reset() {
			sent.clear();
			accept = true;
		}

		@Override
		public boolean send(UUID schoolId, UUID messageId, String phone, String language, String code,
				Duration validity) {
			if (accept) {
				sent.add(new SentCode(phone, language, code, validity));
			}
			return accept;
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Sms {

		@Bean
		@Primary
		RecordingSms recordingSms() {
			return new RecordingSms();
		}

	}

}
