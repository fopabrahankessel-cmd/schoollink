package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /me/device-token} (task 2.7, BR-21, NFR-8, NFR-9, NFR-11). Tokens are made up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class DeviceTokenTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	static final String TOKEN = "fake-device-1:APA91bFakeTokenForTests_0123456789";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	TestClock clock;

	@Autowired
	DeviceTokens devices;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		clock.set(NOW);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@TestFactory
	Stream<DynamicTest> onlyAppUsersRegisterAPhone() { // NFR-8: the admin and the researcher use the web
		return PermissionMatrix.row("PUT /me/device-token", w -> request(TOKEN + "-" + System.nanoTime(), "ANDROID"))
			.expect(204, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A)
			.expect(403, ADMIN_X, ADMIN_Y, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void aNewTokenIsStoredForTheUserAndAudited() throws Exception { // BR-21, NFR-11
		register(PARENT_A, TOKEN).andExpect(status().isNoContent());

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT id, school_id, user_id, platform, created_at, last_seen FROM device_token WHERE token = ?", TOKEN);
		assertThat(row).containsEntry("school_id", world.schoolX)
			.containsEntry("user_id", world.user(PARENT_A))
			.containsEntry("platform", "ANDROID")
			.containsEntry("created_at", Timestamp.from(NOW))
			.containsEntry("last_seen", Timestamp.from(NOW));
		assertThat(audit(row.get("id"))).singleElement().satisfies(entry -> {
			assertThat(entry).containsEntry("action", "CREATE").containsEntry("actor_id", world.user(PARENT_A));
			assertThat((String) entry.get("after")).contains("ANDROID").doesNotContain(TOKEN, "APA91b"); // NFR-9
		});
	}

	@Test
	void registeringAgainOnlyRefreshesLastSeen() throws Exception { // idempotent: each app start
		register(PARENT_A, TOKEN).andExpect(status().isNoContent());
		clock.set(NOW.plusSeconds(3600));

		register(PARENT_A, TOKEN).andExpect(status().isNoContent());

		Map<String, Object> row = jdbc.queryForMap("SELECT id, created_at, last_seen FROM device_token WHERE token = ?",
				TOKEN);
		assertThat(row).containsEntry("created_at", Timestamp.from(NOW))
			.containsEntry("last_seen", Timestamp.from(NOW.plusSeconds(3600)));
		assertThat(audit(row.get("id"))).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_token", Integer.class)).isOne();
	}

	@Test
	void aPhoneThatChangesHandsStopsReceivingThePreviousUsersPushes() throws Exception { // NFR-9
		register(PARENT_A, TOKEN).andExpect(status().isNoContent());

		register(TEACHER_A, TOKEN).andExpect(status().isNoContent());

		assertThat(jdbc.queryForList("SELECT user_id FROM device_token WHERE token = ?", Object.class, TOKEN))
			.containsExactly(world.user(TEACHER_A));
		Object id = jdbc.queryForObject("SELECT id FROM device_token WHERE token = ?", Object.class, TOKEN);
		assertThat(audit(id)).extracting(entry -> entry.get("action")).containsExactly("CREATE", "UPDATE");
	}

	@Test
	void aUserMayHaveSeveralPhones() throws Exception {
		register(PARENT_A, TOKEN).andExpect(status().isNoContent());
		register(PARENT_A, TOKEN + "-tablet").andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_token WHERE user_id = ?", Integer.class,
				world.user(PARENT_A))).isEqualTo(2);
	}

	@Test
	void aPasswordResetOrADisabledAccountForgetsTheUsersPhones() throws Exception { // D18, NFR-9
		register(TEACHER_A, TOKEN).andExpect(status().isNoContent());
		register(DM_1, TOKEN + "-dm").andExpect(status().isNoContent());

		perform(ADMIN_X, post("/api/v1/staff/{id}/password-reset", world.user(TEACHER_A))).andExpect(status().isOk());
		assertThat(jdbc.queryForList("SELECT token FROM device_token", String.class)).containsExactly(TOKEN + "-dm");

		perform(ADMIN_X, put("/api/v1/staff/{id}/status", world.user(DM_1)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"status\":\"DISABLED\"}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_token", Integer.class)).isZero();
	}

	@Test
	void aDisabledAccountReceivesNoPushEvenIfATokenRemains() throws Exception { // D18
		register(PARENT_A, TOKEN).andExpect(status().isNoContent());
		assertThat(devices.tokensOf(world.user(PARENT_A))).containsExactly(TOKEN);

		jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", world.user(PARENT_A));

		assertThat(devices.tokensOf(world.user(PARENT_A))).isEmpty();
	}

	@Test
	void theRequestIsValidated() throws Exception { // NFR-12: errors in the user's language
		perform(PARENT_A, request(" ", "ANDROID")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("token"));
		perform(PARENT_A, request("token with spaces", "ANDROID")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("token"));
		perform(PARENT_A, request("x".repeat(4097), "ANDROID")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("token"));
		perform(PARENT_A, request(TOKEN, "IOS")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("platform"));
		perform(PARENT_A, put("/api/v1/me/device-token").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"" + TOKEN + "\"}")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("platform"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM device_token", Integer.class)).isZero();
	}

	private ResultActions register(Actor actor, String token) throws Exception {
		return perform(actor, request(token, "ANDROID"));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private static MockHttpServletRequestBuilder request(String token, String platform) {
		return put("/api/v1/me/device-token").contentType(MediaType.APPLICATION_JSON)
			.content("{\"token\":\"" + token + "\",\"platform\":\"" + platform + "\"}");
	}

	private List<Map<String, Object>> audit(Object deviceTokenId) {
		return jdbc.queryForList("""
				SELECT action, actor_id, after::text AS after FROM audit_log
				WHERE entity = 'device_token' AND entity_id = ? ORDER BY id""",deviceTokenId.toString());
	}

}
