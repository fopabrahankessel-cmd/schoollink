package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.notification.ActivationSms;
import com.schoollink.shared.ratelimit.RateLimiter;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
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
 * UC-P1 parent activation (task 2.9, D7, D15, D21, NFR-7, NFR-9, NFR-11). The guardian "Parent
 * Activé" of student A has no account and a live invitation code. Codes and phones are made up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, ParentActivationTests.Sms.class })
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class ParentActivationTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	static final String CODE = "K7QM-3XPA";

	static final String PHONE = "+237690000301";

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

	TestWorld world;

	UUID guardian;

	UUID invitation;

	@BeforeEach
	void createWorld() {
		limiter.clear();
		sms.reset();
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		guardian = guardian(world.schoolX, "Parent Activé", PHONE, "EN");
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', false)""", world.schoolX, world.studentA, guardian);
		invitation = invitation(world.schoolX, guardian, CODE, NOW.plus(Duration.ofDays(30)));
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aParentActivatesWithTheCodeThePhoneAndTheSmsCode(CapturedOutput output) throws Exception { // UC-P1
		JsonNode started = body(activate("k7qm 3xpa", "690 00 03 01").andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresAt").value("2026-11-02T07:50:00Z")));
		assertThat(sms.sent).singleElement().satisfies(sent -> {
			assertThat(sent.phone()).isEqualTo(PHONE);
			assertThat(sent.language()).isEqualTo("EN");
			assertThat(sent.code()).matches("[0-9]{6}");
			assertThat(sent.validity()).isEqualTo(Duration.ofMinutes(10));
		});
		String otp = sms.sent.getFirst().code();
		assertThat(jdbc.queryForObject("SELECT otp_hash FROM parent_activation", String.class)).doesNotContain(otp);

		JsonNode tokens = body(verify(started.get("activationId").asString(), otp).andExpect(status().isOk()));

		UUID user = jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian);
		assertThat(jdbc.queryForMap("SELECT school_id, first_name, last_name, phone, language, password_hash FROM app_user WHERE id = ?",
				user))
			.containsEntry("school_id", world.schoolX) // D15
			.containsEntry("first_name", "Parent")
			.containsEntry("last_name", "Activé")
			.containsEntry("phone", PHONE)
			.containsEntry("language", "EN")
			.containsEntry("password_hash", null);
		assertThat(jdbc.queryForList("SELECT role FROM role_assignment WHERE user_id = ?", String.class, user))
			.containsExactly("PARENT");
		assertThat(jdbc.queryForObject("SELECT used_at FROM invitation_code WHERE id = ?", Timestamp.class, invitation))
			.isEqualTo(Timestamp.from(NOW));
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get("accessToken").asString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.roles[0]").value("PARENT"));
		assertThat(tokens.get("refreshToken").asString()).isNotBlank();
		// NFR-11: the account and the link are audited, without the phone
		List<Map<String, Object>> audit = jdbc.queryForList(
				"SELECT entity, action, actor_id, after::text AS after FROM audit_log WHERE actor_id = ? ORDER BY id", user);
		assertThat(audit).extracting(a -> a.get("entity") + " " + a.get("action"))
			.containsExactly("app_user CREATE", "guardian UPDATE");
		assertThat(audit).allSatisfy(a -> assertThat((String) a.get("after")).doesNotContain("690000301"));
		// NFR-9: neither the phone nor the codes reach the logs
		assertThat(output.getAll()).doesNotContain("690000301", "690 00 03 01", otp, "K7QM");
	}

	@Test
	void everyWrongActivationGetsTheSameAnswer() throws Exception { // NFR-7: nothing tells which codes exist
		expectInvalidActivation(activate(CODE, "+237690000399")); // not the guardian's phone
		expectInvalidActivation(activate("ZZZZ-ZZZZ", PHONE)); // unknown
		expectInvalidActivation(activate(CODE, "0033612345678")); // not a Cameroon number

		jdbc.update("UPDATE invitation_code SET used_at = ? WHERE id = ?", Timestamp.from(NOW), invitation);
		expectInvalidActivation(activate(CODE, PHONE)); // used

		UUID expired = invitation(world.schoolX, guardian("Parent Expiré", "+237690000302"), "EXPI-RED2",
				NOW.minusSeconds(1), NOW.minus(Duration.ofDays(31)));
		expectInvalidActivation(activate("EXPI-RED2", "+237690000302")); // expired

		UUID revoked = invitation(world.schoolX, guardian("Parent Révoqué", "+237690000303"), "REVO-KED3",
				NOW.plus(Duration.ofDays(1)));
		jdbc.update("UPDATE invitation_code SET revoked_at = ? WHERE id = ?", Timestamp.from(NOW), revoked);
		expectInvalidActivation(activate("REVO-KED3", "+237690000303")); // revoked

		assertThat(expired).isNotNull();
		assertThat(sms.sent).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_activation", Integer.class)).isZero();
	}

	@Test
	void guessingTheSmsCodeIsStoppedAfterFiveTries() throws Exception { // NFR-7 (plan check)
		String activation = start();
		String otp = sms.sent.getFirst().code();
		String wrong = otp.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 5; i++) {
			verify(activation, wrong).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("auth.invalid_otp"));
		}
		verify(activation, otp).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(header().exists("Retry-After"));

		// counted in the database: a restart (an empty rate limiter) does not reset it
		limiter.clear();
		verify(activation, otp).andExpect(status().isTooManyRequests());
		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian)).isNull();
	}

	@Test
	void aNewSmsCodeReplacesThePreviousOne() throws Exception {
		String first = start();
		String firstOtp = sms.sent.getFirst().code();
		String second = start();

		verify(first, firstOtp).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.invalid_otp"));
		verify(second, sms.sent.get(1).code()).andExpect(status().isOk());
	}

	@Test
	void anSmsCodeExpiresAfterTenMinutesAndWorksOnce() throws Exception {
		String activation = start();
		String otp = sms.sent.getFirst().code();
		clock.set(NOW.plus(Duration.ofMinutes(10)));
		verify(activation, otp).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.invalid_otp"));

		clock.set(NOW);
		String again = start();
		verify(again, sms.sent.get(1).code()).andExpect(status().isOk());
		verify(again, sms.sent.get(1).code()).andExpect(status().isBadRequest());
		verify(UUID.randomUUID().toString(), "123456").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.invalid_otp"));
	}

	@Test
	void aCodeRevokedAfterTheSmsCannotBeVerified() throws Exception { // UC-A4: the admin issued a new code
		String activation = start();
		jdbc.update("UPDATE invitation_code SET revoked_at = ? WHERE id = ?", Timestamp.from(NOW), invitation);

		verify(activation, sms.sent.getFirst().code()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.invalid_activation"));
		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian)).isNull();
		assertThat(jdbc.queryForObject("SELECT verified_at FROM parent_activation", Timestamp.class)).isNull();
	}

	@Test
	void atMostThreeSmsCodesPerGuardianPerHour() throws Exception { // NFR-7: SMS cost, no flooding
		for (int i = 0; i < 3; i++) {
			activate(CODE, PHONE).andExpect(status().isOk());
		}
		activate(CODE, PHONE).andExpect(status().isTooManyRequests());
		assertThat(sms.sent).hasSize(3);

		clock.set(NOW.plus(Duration.ofMinutes(20)));
		activate(CODE, PHONE).andExpect(status().isOk());
	}

	@Test
	void anAddressIsLimitedToTenActivationRequestsPerQuarterHour() throws Exception { // NFR-7
		for (int i = 0; i < 10; i++) {
			expectInvalidActivation(activate("WRON-G00" + i, PHONE, "10.9.0.1"));
		}
		activate(CODE, PHONE, "10.9.0.1").andExpect(status().isTooManyRequests());
		activate(CODE, PHONE, "10.9.0.2").andExpect(status().isOk());
	}

	@Test
	void aStaffMemberWhoIsAlsoAParentKeepsOneAccount() throws Exception { // D21
		UUID teacher = world.user(TEACHER_A);
		jdbc.update("UPDATE app_user SET phone = ? WHERE id = ?", PHONE, teacher);

		verify(start(), sms.sent.getFirst().code()).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.id").value(teacher.toString()));

		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian))
			.isEqualTo(teacher);
		assertThat(jdbc.queryForList("SELECT role FROM role_assignment WHERE user_id = ? ORDER BY role", String.class,
				teacher)).containsExactly("PARENT", "TEACHER");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE phone = ?", Integer.class, PHONE)).isOne();
	}

	@Test
	void aDisabledStaffAccountWithThatPhoneBlocksTheActivation() throws Exception { // D18, D21
		jdbc.update("UPDATE app_user SET phone = ?, status = 'DISABLED' WHERE id = ?", PHONE, world.user(TEACHER_A));

		verify(start(), sms.sent.getFirst().code()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.invalid_activation"));
		assertThat(jdbc.queryForObject("SELECT used_at FROM invitation_code WHERE id = ?", Timestamp.class, invitation))
			.isNull();
	}

	@Test
	void theAccountIsMadeInTheGuardiansSchool() throws Exception { // D15: one account per school
		UUID guardianY = guardian(world.schoolY, "Parent Y", PHONE, "EN");
		invitation(world.schoolY, guardianY, "SCHO-OLY4", NOW.plus(Duration.ofDays(30)));
		verify(start(), sms.sent.getFirst().code()).andExpect(status().isOk());

		JsonNode started = body(activate("SCHO-OLY4", PHONE).andExpect(status().isOk()));
		verify(started.get("activationId").asString(), sms.sent.get(1).code()).andExpect(status().isOk());

		assertThat(jdbc.queryForList("SELECT school_id FROM app_user WHERE phone = ?", UUID.class, PHONE))
			.containsExactlyInAnyOrder(world.schoolX, world.schoolY);
	}

	@Test
	void anSmsThatCannotBeSentIsReported() throws Exception {
		sms.accept = false;

		activate(CODE, PHONE).andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("auth.otp_not_sent"));
	}

	@Test
	void theRequestsAreValidated() throws Exception {
		send(Map.of("phone", PHONE), "/api/v1/auth/parent/activate", "127.0.0.1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("code"));
		send(Map.of("activationId", "not-a-uuid", "otp", "123456"), "/api/v1/auth/parent/verify", "127.0.0.1")
			.andExpect(status().isBadRequest());
		verify(start(), "12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.invalid_otp"));
	}

	private String start() throws Exception {
		return body(activate(CODE, PHONE).andExpect(status().isOk())).get("activationId").asString();
	}

	private ResultActions activate(String code, String phone) throws Exception {
		return activate(code, phone, "127.0.0.1");
	}

	private ResultActions activate(String code, String phone, String ip) throws Exception {
		return send(Map.of("code", code, "phone", phone), "/api/v1/auth/parent/activate", ip);
	}

	private ResultActions verify(String activationId, String otp) throws Exception {
		return send(Map.of("activationId", activationId, "otp", otp), "/api/v1/auth/parent/verify", "127.0.0.1");
	}

	private ResultActions send(Map<String, Object> body, String path, String ip) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body))
			.with(request -> {
				request.setRemoteAddr(ip);
				return request;
			}));
	}

	private void expectInvalidActivation(ResultActions result) throws Exception {
		result.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.invalid_activation"))
			.andExpect(jsonPath("$.message").value(
					"Ce code et ce numéro ne correspondent à aucune invitation valide. Vérifiez-les ou demandez un nouveau code à l'établissement."));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private UUID guardian(String name, String phone) {
		return guardian(world.schoolX, name, phone, "FR");
	}

	private UUID guardian(UUID school, String name, String phone, String language) {
		return jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, ?, ?, ?) RETURNING id""", UUID.class,
				school, name, phone, language);
	}

	private UUID invitation(UUID school, UUID guardianId, String code, Instant expiresAt) {
		return invitation(school, guardianId, code, expiresAt, NOW.minus(Duration.ofDays(1)));
	}

	private UUID invitation(UUID school, UUID guardianId, String code, Instant expiresAt, Instant createdAt) {
		return jdbc.queryForObject("""
				INSERT INTO invitation_code (school_id, guardian_id, code_hash, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?) RETURNING id""", UUID.class, school, guardianId, sha256(code),
				Timestamp.from(createdAt), Timestamp.from(expiresAt));
	}

	/** As the people module stores codes: SHA-256 of the code without spaces or dashes, in capitals. */
	private static String sha256(String code) throws IllegalStateException {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256")
					.digest(code.replace("-", "").toUpperCase().getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
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
