package com.schoollink.detection.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D7, task 3.3: the DM's settings: threshold rules (BR-16, D9, D28), school start time (BR-7)
 * and SMS digest times (BR-22, D6). Only a DM writes, and each change is audited (NFR-11).
 * Monday 2 November 2026, 08:40 in Douala. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class SettingsTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

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

	TestWorld world;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (UC-D7, NFR-8)

	@TestFactory
	Stream<DynamicTest> onlyADmReadsTheSettings() {
		return PermissionMatrix.row("GET /settings/thresholds", w -> get("/api/v1/settings/thresholds"))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyADmWritesTheSettings() {
		return PermissionMatrix.row("PUT /settings/thresholds", w -> request(settings(8, 15)))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void forbiddenWritesChangeNothing() throws Exception {
		perform(ADMIN_X, request(settings(8, 15))).andExpect(status().isForbidden());
		perform(ASSISTANT_DM_1, request(settings(8, 15))).andExpect(status().isForbidden());

		assertThat(count("CONSECUTIVE_LATENESS", world.schoolX)).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity = 'settings'", Integer.class))
			.isZero();
	}

	// Defaults (BR-16, BR-22, D9)

	@Test
	void eachSchoolStartsWithThePilotDefaults() throws Exception { // BR-16, D9
		perform(DM_1, get("/api/v1/settings/thresholds")).andExpect(status().isOk())
			.andExpect(jsonPath("$.startTime").value("07:30:00"))
			.andExpect(jsonPath("$.digestTimes.length()").value(2))
			.andExpect(jsonPath("$.digestTimes[0]").value("13:00:00"))
			.andExpect(jsonPath("$.digestTimes[1]").value("17:00:00"))
			.andExpect(jsonPath("$.rules.length()").value(3))
			.andExpect(jsonPath("$.rules[0].type").value("FULL_DAY_ABSENCE"))
			.andExpect(jsonPath("$.rules[0].count").value(1))
			.andExpect(jsonPath("$.rules[0].unit").value("FULL_DAYS"))
			.andExpect(jsonPath("$.rules[0].window").value("CONSECUTIVE"))
			.andExpect(jsonPath("$.rules[0].active").value(true))
			.andExpect(jsonPath("$.rules[1].type").value("CONSECUTIVE_LATENESS"))
			.andExpect(jsonPath("$.rules[1].count").value(5))
			.andExpect(jsonPath("$.rules[1].unit").value("SCHOOL_DAYS"))
			.andExpect(jsonPath("$.rules[1].min").value(2))
			.andExpect(jsonPath("$.rules[1].max").value(20))
			.andExpect(jsonPath("$.rules[2].type").value("UNJUSTIFIED_ABSENCES"))
			.andExpect(jsonPath("$.rules[2].count").value(15))
			.andExpect(jsonPath("$.rules[2].unit").value("PERIODS"))
			.andExpect(jsonPath("$.rules[2].window").value("CURRENT_TERM"));
		// seeded by the database for every new school, whatever creates it
		assertThat(jdbc.queryForObject("SELECT count(*) FROM threshold_rule WHERE school_id = ?", Integer.class,
				world.schoolY)).isEqualTo(3);
	}

	// Changes (UC-D7, NFR-11)

	@Test
	void aChangeIsStoredAndAudited() throws Exception { // UC-D7, NFR-11
		Map<String, Object> body = settings(8, 20);
		body.put("startTime", "07:45");
		body.put("digestTimes", List.of("12:30", "16:30"));

		perform(DM_1, request(body)).andExpect(status().isOk())
			.andExpect(jsonPath("$.startTime").value("07:45:00"))
			.andExpect(jsonPath("$.digestTimes[0]").value("12:30:00"))
			.andExpect(jsonPath("$.rules[1].count").value(8))
			.andExpect(jsonPath("$.rules[1].updatedBy").value(world.user(DM_1).toString()))
			.andExpect(jsonPath("$.rules[1].updatedAt").value("2026-11-02T07:40:00Z"))
			.andExpect(jsonPath("$.rules[2].count").value(20));

		Map<String, Object> school = jdbc.queryForMap(
				"SELECT start_time::text AS start, digest_first::text AS first, digest_second::text AS second FROM school WHERE id = ?",
				world.schoolX);
		assertThat(school).containsEntry("start", "07:45:00")
			.containsEntry("first", "12:30:00")
			.containsEntry("second", "16:30:00");
		Map<String, Object> rule = jdbc.queryForMap(
				"SELECT count, updated_by, updated_at FROM threshold_rule WHERE school_id = ? AND type = 'CONSECUTIVE_LATENESS'",
				world.schoolX);
		assertThat(rule).containsEntry("updated_by", world.user(DM_1)).containsEntry("updated_at", Timestamp.from(NOW));
		// an unchanged rule keeps who set it last
		assertThat(jdbc.queryForObject(
				"SELECT updated_by FROM threshold_rule WHERE school_id = ? AND type = 'FULL_DAY_ABSENCE'", UUID.class,
				world.schoolX)).isNull();

		Map<String, Object> audit = jdbc.queryForMap("""
				SELECT actor_id, action, entity_id, before::text AS before, after::text AS after FROM audit_log
				WHERE entity = 'settings'""");
		assertThat(audit).containsEntry("actor_id", world.user(DM_1))
			.containsEntry("action", "UPDATE")
			.containsEntry("entity_id", world.schoolX.toString());
		assertThat((String) audit.get("before")).contains("\"startTime\": \"07:30\"", "\"13:00\"", "\"count\": 5");
		assertThat((String) audit.get("after")).contains("\"startTime\": \"07:45\"", "\"12:30\"", "\"count\": 8",
				"\"count\": 20");
	}

	@Test
	void settingsAreTheSchoolsNotTheBlocks() throws Exception { // NFR-8: school-wide, never across schools
		perform(DM_1, request(settings(8, 15))).andExpect(status().isOk());

		perform(DM_2, get("/api/v1/settings/thresholds")).andExpect(jsonPath("$.rules[1].count").value(8));
		assertThat(count("CONSECUTIVE_LATENESS", world.schoolY)).isEqualTo(5);
	}

	@Test
	void savingTheSameSettingsRecordsNothing() throws Exception { // NFR-11
		perform(DM_1, request(settings(5, 15))).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity = 'settings'", Integer.class))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM threshold_rule WHERE updated_by IS NOT NULL", Integer.class))
			.isZero();
	}

	@Test
	void aRuleCanBeTurnedOffAndTheSecondDigestDropped() throws Exception { // BR-16, D6
		Map<String, Object> body = settings(5, 15);
		body.put("digestTimes", List.of("13:00"));
		body.put("rules", List.of(rule("FULL_DAY_ABSENCE", 1, true), rule("CONSECUTIVE_LATENESS", 5, false),
				rule("UNJUSTIFIED_ABSENCES", 15, true)));

		perform(DM_1, request(body)).andExpect(status().isOk())
			.andExpect(jsonPath("$.digestTimes.length()").value(1))
			.andExpect(jsonPath("$.rules[1].active").value(false));

		assertThat(jdbc.queryForObject("SELECT digest_second FROM school WHERE id = ?", Object.class, world.schoolX))
			.isNull();
	}

	@Test
	void theStartTimeDecidesWhoIsLate() throws Exception { // BR-7
		Map<String, Object> body = settings(5, 15);
		body.put("startTime", "08:30");
		perform(DM_1, request(body)).andExpect(status().isOk());

		Map<String, Object> lateness = Map.of("studentId", world.studentA, "date", "2026-11-02", "arrivalTime",
				"08:12", "recordedAt", "2026-11-02T07:13:00Z");
		perform(ASSISTANT_DM_1, put("/api/v1/lateness/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(lateness))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("lateness.not_late"));
	}

	// Validation

	@Test
	void theSettingsAreValidated() throws Exception {
		Map<String, Object> early = settings(5, 15);
		early.put("startTime", "04:30");
		perform(DM_1, request(early)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.start_time"))
			.andExpect(jsonPath("$.field").value("startTime"));

		Map<String, Object> unordered = settings(5, 15);
		unordered.put("digestTimes", List.of("17:00", "13:00"));
		perform(DM_1, request(unordered)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.digest_times"));

		Map<String, Object> night = settings(5, 15);
		night.put("digestTimes", List.of("13:00", "21:30"));
		perform(DM_1, request(night)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.digest_times"));

		Map<String, Object> none = settings(5, 15);
		none.put("digestTimes", List.of());
		perform(DM_1, request(none)).andExpect(status().isBadRequest());

		Map<String, Object> missing = settings(5, 15);
		missing.put("rules", List.of(rule("FULL_DAY_ABSENCE", 1, true), rule("UNJUSTIFIED_ABSENCES", 15, true)));
		perform(DM_1, request(missing)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.rules_incomplete"));

		Map<String, Object> twice = settings(5, 15);
		twice.put("rules", List.of(rule("FULL_DAY_ABSENCE", 1, true), rule("FULL_DAY_ABSENCE", 2, true),
				rule("CONSECUTIVE_LATENESS", 5, true), rule("UNJUSTIFIED_ABSENCES", 15, true)));
		perform(DM_1, request(twice)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.rules_incomplete"));

		perform(DM_1, request(settings(1, 15))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.count_out_of_range"))
			.andExpect(jsonPath("$.field").value("rules[1].count"));
		perform(DM_1, request(settings(5, 201))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("settings.count_out_of_range"));

		Map<String, Object> unknown = settings(5, 15);
		unknown.put("rules", List.of(rule("FULL_DAY_ABSENCE", 1, true), rule("CONSECUTIVE_LATENESS", 5, true),
				rule("TRUANCY", 15, true)));
		perform(DM_1, request(unknown)).andExpect(status().isBadRequest());

		assertThat(count("CONSECUTIVE_LATENESS", world.schoolX)).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT start_time::text FROM school WHERE id = ?", String.class, world.schoolX))
			.isEqualTo("07:30:00");
	}

	@Test
	void theMessageGivesTheAllowedRange() throws Exception { // NFR-12: DM_1's account is in French
		perform(DM_1, request(settings(1, 15))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Choisissez un seuil entre 2 et 20."));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private MockHttpServletRequestBuilder request(Map<String, Object> body) {
		return put("/api/v1/settings/thresholds").contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body));
	}

	/** The defaults, with these lateness and unjustified-absence counts. */
	private static Map<String, Object> settings(int lateness, int unjustified) {
		Map<String, Object> body = new HashMap<>();
		body.put("startTime", "07:30");
		body.put("digestTimes", List.of("13:00", "17:00"));
		body.put("rules", List.of(rule("FULL_DAY_ABSENCE", 1, true), rule("CONSECUTIVE_LATENESS", lateness, true),
				rule("UNJUSTIFIED_ABSENCES", unjustified, true)));
		return body;
	}

	private static Map<String, Object> rule(String type, int count, boolean active) {
		return Map.of("type", type, "count", count, "active", active);
	}

	private int count(String type, UUID school) {
		return jdbc.queryForObject("SELECT count FROM threshold_rule WHERE school_id = ? AND type = ?", Integer.class,
				school, type);
	}

}
