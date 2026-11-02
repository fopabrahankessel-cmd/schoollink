package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.auth.internal.ParentActivationTests.RecordingSms;
import com.schoollink.shared.ratelimit.RateLimiter;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D24: a parent who already activated signs in again with their school, phone and an SMS one-time
 * code (NFR-7, NFR-9). Phones are made up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, ParentActivationTests.Sms.class })
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class ParentSignInTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	static final String PHONE = "+237690000401";

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
	void aParentSignsInAgainWithTheirPhoneAndAnSmsCode(CapturedOutput output) throws Exception {
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
		// NFR-9
		assertThat(output.getAll()).doesNotContain("690000401", "690 00 04 01", otp);

		// NFR-7: the access token's 15 minutes (and 1 minute of skew) run on the clock it was issued with
		clock.set(NOW.plus(Duration.ofMinutes(17)));
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get("accessToken").asString()))
			.andExpect(status().isUnauthorized());
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
		return send(Map.of("activationId", signInId, "otp", otp), "/api/v1/auth/parent/sign-in/verify");
	}

	private ResultActions send(Map<String, Object> body, String path) throws Exception {
		return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

}
