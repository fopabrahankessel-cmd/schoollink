package com.schoollink.detection.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.detection.RuleType;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D2, BR-18, task 3.5: the DM in charge reviews risk alerts and decides on each, once, with
 * who, when and why (NFR-11). Class 6e A is DM_1's (block 1), 5e B is DM_2's (block 2). Tuesday 3
 * November 2026. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class AlertTests {

	static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

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
	Detection detection;

	TestWorld world;

	/** In 5e B: DM_2's. */
	UUID studentB;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		studentB = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0002', 'Autre', 'Fictif', 'M', 2011) RETURNING id""", UUID.class, world.schoolX,
				world.classB);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (BR-18, NFR-8, D17)

	@TestFactory
	Stream<DynamicTest> onlyADmListsAlerts() {
		return PermissionMatrix.row("GET /alerts", w -> get("/api/v1/alerts"))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheDmInChargeDecides() {
		UUID alert = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE);
		return PermissionMatrix.row("POST /alerts/{id}/decision",
				w -> decision(alert, Map.of("decision", "DISMISS", "note", "Malade, vu avec la famille")))
			.expect(200, DM_1)
			.expect(404, DM_2) // D17: outside the scope
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	// Listing (UC-D2)

	@Test
	void eachDmSeesTheAlertsOfTheirClassesOnly() throws Exception { // BR-18
		UUID mine = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE);
		UUID theirs = alert(studentB, world.classB, RuleType.CONSECUTIVE_LATENESS);

		perform(DM_1, get("/api/v1/alerts")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(mine.toString()))
			.andExpect(jsonPath("$[0].studentId").value(world.studentA.toString()))
			.andExpect(jsonPath("$[0].firstName").value("Élève"))
			.andExpect(jsonPath("$[0].lastName").value("Fictif"))
			.andExpect(jsonPath("$[0].matricule").value("TW-0001"))
			.andExpect(jsonPath("$[0].className").value("6e A"))
			.andExpect(jsonPath("$[0].ruleType").value("FULL_DAY_ABSENCE"))
			.andExpect(jsonPath("$[0].threshold").value(1))
			.andExpect(jsonPath("$[0].asOf").value("2026-11-02"))
			.andExpect(jsonPath("$[0].evidence.dates[0]").value("2026-11-02"))
			.andExpect(jsonPath("$[0].status").value("OPEN"))
			.andExpect(jsonPath("$[0].decision").isEmpty());
		perform(DM_2, get("/api/v1/alerts")).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(theirs.toString()));
	}

	@Test
	void openAndClosedAlertsAreListedApart() throws Exception {
		UUID older = alert(world.studentA, world.classA, RuleType.CONSECUTIVE_LATENESS, NOW.minusSeconds(7_200));
		UUID newer = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE, NOW.minusSeconds(3_600));
		UUID decided = alert(world.studentA, world.classA, RuleType.UNJUSTIFIED_ABSENCES);
		decide(DM_1, decided, Map.of("decision", "REFER")).andExpect(status().isOk());

		// the open ones oldest first: the longest waiting on top
		perform(DM_1, get("/api/v1/alerts?status=open")).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(older.toString()))
			.andExpect(jsonPath("$[1].id").value(newer.toString()));
		perform(DM_1, get("/api/v1/alerts?status=closed")).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(decided.toString()))
			.andExpect(jsonPath("$[0].decision").value("REFER"));
		perform(DM_1, get("/api/v1/alerts?status=pending")).andExpect(status().isBadRequest());
	}

	// Deciding (BR-18, NFR-11)

	@Test
	void aDismissalKeepsWhoWhenAndWhy() throws Exception { // BR-18, NFR-11
		UUID alert = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE);

		decide(DM_1, alert, Map.of("decision", "DISMISS", "note", "  Malade, vu avec la famille  "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.decision").value("DISMISS"))
			.andExpect(jsonPath("$.decisionNote").value("Malade, vu avec la famille"))
			.andExpect(jsonPath("$.decidedBy").value(world.user(DM_1).toString()))
			.andExpect(jsonPath("$.decidedAt").value("2026-11-03T10:00:00Z"))
			.andExpect(jsonPath("$.outcomeId").isEmpty())
			.andExpect(jsonPath("$.firstName").value("Élève"));

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT status, decision, decision_note, closed_by, closed_at FROM risk_alert WHERE id = ?", alert);
		assertThat(row).containsEntry("status", "CLOSED")
			.containsEntry("decision", "DISMISS")
			.containsEntry("decision_note", "Malade, vu avec la famille")
			.containsEntry("closed_by", world.user(DM_1))
			.containsEntry("closed_at", Timestamp.from(NOW));
		Map<String, Object> audit = jdbc.queryForMap("""
				SELECT actor_id, before::text AS before, after::text AS after FROM audit_log
				WHERE action = 'DECIDE' AND entity = 'risk_alert' AND entity_id = ?""", alert.toString());
		assertThat(audit).containsEntry("actor_id", world.user(DM_1));
		assertThat((String) audit.get("before")).contains("\"status\": \"OPEN\"");
		assertThat((String) audit.get("after")).contains("\"decision\": \"DISMISS\"",
				"\"decisionNote\": \"Malade, vu avec la famille\"");
	}

	@Test
	void aDisciplinaryIssueIsRecorded() throws Exception { // D4
		UUID alert = alert(world.studentA, world.classA, RuleType.CONSECUTIVE_LATENESS);

		String outcome = json.readTree(decide(DM_1, alert, Map.of("decision", "DISCIPLINARY", "note",
				"Retards répétés malgré l'avertissement"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("outcomeId").asString();

		Map<String, Object> record = jdbc.queryForMap("""
				SELECT student_id, alert_id, description, status, recorded_by FROM disciplinary_record WHERE id = ?::uuid""",
				outcome);
		assertThat(record).containsEntry("student_id", world.studentA)
			.containsEntry("alert_id", alert)
			.containsEntry("description", "Retards répétés malgré l'avertissement")
			.containsEntry("status", "OPEN")
			.containsEntry("recorded_by", world.user(DM_1));
		assertThat(jdbc.queryForObject("""
				SELECT actor_id FROM audit_log WHERE action = 'CREATE' AND entity = 'disciplinary_record' AND entity_id = ?""",
				UUID.class, outcome)).isEqualTo(world.user(DM_1));
	}

	@Test
	void aReferralOpensOneCaseForTheStudent() throws Exception { // D4, D30
		UUID first = alert(world.studentA, world.classA, RuleType.UNJUSTIFIED_ABSENCES);
		UUID second = alert(world.studentA, world.classA, RuleType.CONSECUTIVE_LATENESS);

		String caseId = json.readTree(decide(DM_1, first, Map.of("decision", "REFER", "note", "Semble isolé"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("outcomeId").asString();
		decide(DM_1, second, Map.of("decision", "REFER")).andExpect(status().isOk())
			.andExpect(jsonPath("$.outcomeId").value(caseId));

		Map<String, Object> opened = jdbc.queryForMap("""
				SELECT student_id, referred_by, alert_id, referral_note, counsellor_id, status FROM counselling_case
				WHERE id = ?::uuid""", caseId);
		assertThat(opened).containsEntry("student_id", world.studentA)
			.containsEntry("referred_by", world.user(DM_1))
			.containsEntry("alert_id", first)
			.containsEntry("referral_note", "Semble isolé")
			.containsEntry("counsellor_id", null)
			.containsEntry("status", "OPEN");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM counselling_case WHERE student_id = ?", Integer.class,
				world.studentA)).isOne();
	}

	@Test
	void anAlertIsDecidedOnce() throws Exception { // BR-18
		UUID alert = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE);
		decide(DM_1, alert, Map.of("decision", "DISMISS", "note", "Sortie scolaire")).andExpect(status().isOk());

		decide(DM_1, alert, Map.of("decision", "REFER")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("alert.already_decided"));

		assertThat(jdbc.queryForObject("SELECT decision FROM risk_alert WHERE id = ?", String.class, alert))
			.isEqualTo("DISMISS");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM counselling_case WHERE student_id = ?", Integer.class,
				world.studentA)).isZero();
	}

	@Test
	void theDecisionIsValidated() throws Exception {
		UUID alert = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE);

		// BR-18: a dismissal says why
		decide(DM_1, alert, Map.of("decision", "DISMISS")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("alert.note_required"))
			.andExpect(jsonPath("$.field").value("note"));
		decide(DM_1, alert, Map.of("decision", "DISCIPLINARY", "note", "   ")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("alert.note_required"));
		decide(DM_1, alert, Map.of("decision", "DISMISS", "note", "x".repeat(501))).andExpect(status().isBadRequest());
		decide(DM_1, alert, Map.of("decision", "EXPEL", "note", "Non")).andExpect(status().isBadRequest());
		decide(DM_1, alert, Map.of("note", "Sans décision")).andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("SELECT status FROM risk_alert WHERE id = ?", String.class, alert))
			.isEqualTo("OPEN");
	}

	@Test
	void anAlertOfAnotherSchoolOrUnknownIsNotFound() throws Exception { // NFR-8, D17
		UUID studentY = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-Y01', 'Student', 'Fictional', 'F', 2012) RETURNING id""", UUID.class, world.schoolY,
				world.classC);
		UUID theirs = alert(world.schoolY, studentY, world.classC, RuleType.FULL_DAY_ABSENCE, NOW);

		decide(DM_1, theirs, Map.of("decision", "REFER")).andExpect(status().isNotFound());
		decide(DM_1, UUID.randomUUID(), Map.of("decision", "REFER")).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT status FROM risk_alert WHERE id = ?", String.class, theirs))
			.isEqualTo("OPEN");
	}

	// Guards (BR-18, BR-19)

	@Test
	void detectionNeverDecides() { // BR-18: rules only raise alerts
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, '2026-11-02', ?, ?, ?)""", session, world.schoolX, world.classA, world.period1,
				world.user(TEACHER_A), Timestamp.from(NOW));
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, 'ABSENT')""", UUID.randomUUID(), world.schoolX, session, world.studentA);

		assertThat(detection.evaluate(world.schoolX, world.studentA, LocalDate.parse("2026-11-02"),
				EnumSet.allOf(RuleType.class))).isOne();

		assertThat(jdbc.queryForMap("""
				SELECT status, decision, decision_note, outcome_id, closed_by FROM risk_alert WHERE school_id = ?""",
				world.schoolX)).containsEntry("status", "OPEN")
			.containsEntry("decision", null)
			.containsEntry("decision_note", null)
			.containsEntry("outcome_id", null)
			.containsEntry("closed_by", null);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM disciplinary_record WHERE school_id = ?", Integer.class,
				world.schoolX)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM counselling_case WHERE school_id = ?", Integer.class,
				world.schoolX)).isZero();
	}

	@Test
	void theDatabaseKeepsAlertsAsDecided() throws Exception { // BR-18, NFR-11
		UUID open = alert(world.studentA, world.classA, RuleType.FULL_DAY_ABSENCE);
		UUID decided = alert(world.studentA, world.classA, RuleType.CONSECUTIVE_LATENESS);
		decide(DM_1, decided, Map.of("decision", "DISMISS", "note", "Transport en panne")).andExpect(status().isOk());

		assertThatThrownBy(() -> refused("UPDATE risk_alert SET decision_note = 'Autre' WHERE id = ?", decided))
			.hasMessageContaining("is closed");
		assertThatThrownBy(() -> refused("UPDATE risk_alert SET threshold = 9 WHERE id = ?", open))
			.hasMessageContaining("only changes by a decision");
		assertThatThrownBy(() -> refused("DELETE FROM risk_alert WHERE id = ?", open))
			.hasMessageContaining("cannot be deleted");
		// a closing without its decision is refused
		assertThatThrownBy(() -> refused("""
				UPDATE risk_alert SET status = 'CLOSED', closed_at = now(), closed_by = ? WHERE id = ?""",
				world.user(DM_1), open)).hasMessageContaining("check");
		assertThat(jdbc.queryForObject("SELECT status FROM risk_alert WHERE id = ?", String.class, open))
			.isEqualTo("OPEN");
	}

	/** In a savepoint, so the test's transaction goes on after the database refuses. */
	private void refused(String sql, Object... args) {
		jdbc.execute("SAVEPOINT refused");
		try {
			jdbc.update(sql, args);
		}
		finally {
			jdbc.execute("ROLLBACK TO SAVEPOINT refused");
		}
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private ResultActions decide(Actor actor, UUID alert, Map<String, Object> body) throws Exception {
		return perform(actor, decision(alert, body));
	}

	private MockHttpServletRequestBuilder decision(UUID alert, Map<String, Object> body) {
		return post("/api/v1/alerts/{id}/decision", alert).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(new HashMap<>(body)));
	}

	private UUID alert(UUID student, UUID classId, RuleType type) {
		return alert(student, classId, type, NOW.minusSeconds(60));
	}

	private UUID alert(UUID student, UUID classId, RuleType type, Instant raisedAt) {
		return alert(world.schoolX, student, classId, type, raisedAt);
	}

	/** As detection raises it (task 3.4). */
	private UUID alert(UUID school, UUID student, UUID classId, RuleType type, Instant raisedAt) {
		return jdbc.queryForObject("""
				INSERT INTO risk_alert (school_id, student_id, class_id, rule_type, threshold, as_of, evidence, raised_at)
				VALUES (?, ?, ?, ?, 1, '2026-11-02', '{"dates": ["2026-11-02"]}', ?) RETURNING id""", UUID.class, school,
				student, classId, type.name(), Timestamp.from(raisedAt));
	}

}
