package com.schoollink.discipline.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.discipline.SummonsIssued;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D3, BR-23, D12, D31, task 3.6: the DM summons a parent, on their own or from an alert; the
 * summons goes at once by push to the app and by SMS to guardians without it. Tuesday 3 November
 * 2026, 10:00 in Douala. Student A has PARENT_A (the app) and a second guardian without it.
 * Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@RecordApplicationEvents
@Transactional
class SummonsTests {

	/** 10:00 in Douala, Tuesday. */
	static final Instant NOW = Instant.parse("2026-11-03T09:00:00Z");

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
	ApplicationEvents events;

	TestWorld world;

	UUID parentGuardian;

	UUID smsGuardian;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		parentGuardian = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class,
				world.user(PARENT_A));
		smsGuardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, 'Tuteur Fictif', '+237690000097', 'EN')
				RETURNING id""", UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', false)""", world.schoolX, world.studentA, smsGuardian);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (BR-18, NFR-8, D17)

	@TestFactory
	Stream<DynamicTest> onlyTheDmInChargeSummons() {
		return PermissionMatrix.row("POST /summons", w -> request(body(Map.of())))
			.expect(201, DM_1)
			.expect(404, DM_2) // D17: outside the scope
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void aStudentOfAnotherSchoolOrUnknownIsNotFound() throws Exception { // NFR-8, D17
		UUID studentY = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-Y01', 'Student', 'Fictional', 'F', 2012) RETURNING id""", UUID.class, world.schoolY,
				world.classC);

		summon(body(Map.of("studentId", studentY))).andExpect(status().isNotFound());
		summon(body(Map.of("studentId", UUID.randomUUID()))).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM summons", Integer.class)).isZero();
	}

	// Sending (BR-23, D12)

	@Test
	void aSummonsIsStoredAuditedAndSentAtOnce() throws Exception { // BR-23, D12, NFR-6, NFR-11
		JsonNode body = json.readTree(summon(body(Map.of())).andExpect(status().isCreated())
			.andExpect(jsonPath("$.studentId").value(world.studentA.toString()))
			.andExpect(jsonPath("$.alertId").isEmpty())
			.andExpect(jsonPath("$.meetingDate").value("2026-11-04"))
			.andExpect(jsonPath("$.meetingTime").value("14:00:00"))
			.andExpect(jsonPath("$.reason").value("Comportement en classe"))
			.andExpect(jsonPath("$.sentBy").value(world.user(DM_1).toString()))
			.andExpect(jsonPath("$.sentAt").value("2026-11-03T09:00:00Z"))
			.andReturn()
			.getResponse()
			.getContentAsString());
		UUID id = UUID.fromString(body.get("id").asString());

		assertThat(events.stream(SummonsIssued.class)).containsExactly(new SummonsIssued(id, world.schoolX,
				world.studentA, null, LocalDate.parse("2026-11-04"), LocalTime.of(14, 0), world.user(DM_1), NOW));
		assertThat((String) jdbc.queryForObject("""
				SELECT after::text FROM audit_log WHERE action = 'CREATE' AND entity = 'summons' AND entity_id = ?""",
				String.class, id.toString())).contains("Comportement en classe", "2026-11-04", "14:00");

		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT guardian_id, event_type, channel, language, status, payload::text AS payload FROM notification_outbox
				WHERE event_id = ? ORDER BY channel""", id);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsEntry("guardian_id", parentGuardian)
			.containsEntry("event_type", "SUMMONS")
			.containsEntry("channel", "PUSH")
			.containsEntry("language", "FR")
			.containsEntry("status", "QUEUED");
		// D12: the SMS goes now, not in the evening digest
		assertThat(rows.get(1)).containsEntry("guardian_id", smsGuardian)
			.containsEntry("channel", "SMS")
			.containsEntry("language", "EN")
			.containsEntry("status", "QUEUED");
		JsonNode payload = json.readTree((String) rows.get(1).get("payload"));
		assertThat(payload.get("firstName").asString()).isEqualTo("Élève");
		assertThat(payload.get("meetingDate").asString()).isEqualTo("2026-11-04");
		assertThat(payload.get("meetingTime").asString()).isEqualTo("14:00");
		// D31: the reason stays with the DM
		assertThat((String) rows.get(1).get("payload")).doesNotContain("Comportement");
	}

	@Test
	void theParentFindsItInTheInbox() throws Exception { // UC-P2
		summon(body(Map.of())).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/me/notifications").header("Authorization", "Bearer " + world.token(PARENT_A)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].eventType").value("SUMMONS"))
			.andExpect(jsonPath("$[0].studentId").value(world.studentA.toString()));
	}

	@Test
	void oneTapMeetsOnTheNextSchoolDayAtTheStart() throws Exception { // UC-D3, D31
		Map<String, Object> oneTap = new HashMap<>(Map.of("studentId", world.studentA, "reason", "Absent toute la journée"));
		summon(oneTap).andExpect(status().isCreated())
			.andExpect(jsonPath("$.meetingDate").value("2026-11-04"))
			.andExpect(jsonPath("$.meetingTime").value("07:30:00"));

		// on Friday: Monday is a holiday, so Tuesday
		clock.set(Instant.parse("2026-11-06T09:00:00Z"));
		jdbc.update("""
				INSERT INTO holiday (school_id, term_id, label, start_date, end_date)
				VALUES (?, ?, 'Pont', '2026-11-09', '2026-11-09')""", world.schoolX, world.termX);
		summon(oneTap).andExpect(status().isCreated()).andExpect(jsonPath("$.meetingDate").value("2026-11-10"));
	}

	@Test
	void theMeetingIsValidated() throws Exception { // D31
		summon(body(Map.of("meetingDate", "2026-11-02"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.meeting_date"));
		// today, but already past
		summon(body(Map.of("meetingDate", "2026-11-03", "meetingTime", "08:00"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.meeting_date"));
		summon(body(Map.of("meetingDate", "2026-12-04"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.meeting_date"));
		summon(body(Map.of("meetingDate", "2026-11-07"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.not_a_school_day"));
		summon(body(Map.of("meetingTime", "19:00"))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.meeting_time"))
			.andExpect(jsonPath("$.field").value("meetingTime"));
		Map<String, Object> noReason = body(Map.of());
		noReason.remove("reason");
		summon(noReason).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.reason_required"));
		summon(body(Map.of("reason", "x".repeat(301)))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.reason_too_long"));
		Map<String, Object> noStudent = body(Map.of());
		noStudent.remove("studentId");
		summon(noStudent).andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM summons", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE event_type = 'SUMMONS'",
				Integer.class)).isZero();
	}

	// From an alert (BR-18, UC-D3)

	@Test
	void summoningFromAnAlertClosesItAndSendsAtOnce() throws Exception { // BR-18, BR-23, UC-D3
		UUID alert = alert();

		// one tap: no note, no meeting
		String outcome = json.readTree(decide(alert, Map.of("decision", "SUMMON")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"))
			.andExpect(jsonPath("$.decision").value("SUMMON"))
			.andReturn()
			.getResponse()
			.getContentAsString()).get("outcomeId").asString();

		Map<String, Object> summons = jdbc.queryForMap("""
				SELECT student_id, alert_id, reason, meeting_date::text AS date, meeting_time::text AS time, sent_by
				FROM summons WHERE id = ?::uuid""", outcome);
		assertThat(summons).containsEntry("student_id", world.studentA)
			.containsEntry("alert_id", alert)
			.containsEntry("reason", null)
			.containsEntry("date", "2026-11-04")
			.containsEntry("time", "07:30:00")
			.containsEntry("sent_by", world.user(DM_1));
		assertThat(jdbc.queryForList("""
				SELECT status FROM notification_outbox WHERE event_type = 'SUMMONS' AND event_id = ?::uuid""",
				String.class, outcome)).containsExactly("QUEUED", "QUEUED");
	}

	@Test
	void aWrongMeetingLeavesTheAlertOpen() throws Exception { // all or nothing
		UUID alert = alert();

		decide(alert, Map.of("decision", "SUMMON", "note", "Absences", "meetingDate", "2026-11-07"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("summons.not_a_school_day"));

		assertThat(jdbc.queryForObject("SELECT status FROM risk_alert WHERE id = ?", String.class, alert))
			.isEqualTo("OPEN");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM summons", Integer.class)).isZero();
	}

	@Test
	void aSummonsNeverChanges() throws Exception { // NFR-11
		UUID id = UUID.fromString(json.readTree(summon(body(Map.of())).andReturn().getResponse().getContentAsString())
			.get("id")
			.asString());

		jdbc.execute("SAVEPOINT refused");
		assertThatThrownBy(() -> jdbc.update("UPDATE summons SET meeting_time = '15:00' WHERE id = ?", id))
			.hasMessageContaining("cannot be changed");
		jdbc.execute("ROLLBACK TO SAVEPOINT refused");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM summons WHERE id = ?", id))
			.hasMessageContaining("cannot be changed or deleted");
	}

	/** Student A, Wednesday 14:00, with a reason; {@code overrides} replace fields. */
	private Map<String, Object> body(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(Map.of("studentId", world.studentA, "meetingDate", "2026-11-04",
				"meetingTime", "14:00", "reason", "Comportement en classe"));
		body.putAll(overrides);
		return body;
	}

	private MockHttpServletRequestBuilder request(Map<String, Object> body) {
		return post("/api/v1/summons").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
	}

	private ResultActions summon(Map<String, Object> body) throws Exception {
		return mockMvc.perform(request(body).header("Authorization", "Bearer " + world.token(DM_1)));
	}

	private ResultActions decide(UUID alert, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/v1/alerts/{id}/decision", alert)
			.header("Authorization", "Bearer " + world.token(DM_1))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body)));
	}

	/** A full-day absence alert of student A, as detection raises it (task 3.4). */
	private UUID alert() {
		return jdbc.queryForObject("""
				INSERT INTO risk_alert (school_id, student_id, class_id, rule_type, threshold, as_of, evidence, raised_at)
				VALUES (?, ?, ?, 'FULL_DAY_ABSENCE', 1, '2026-11-02', '{"dates": ["2026-11-02"]}', ?) RETURNING id""",
				UUID.class, world.schoolX, world.studentA, world.classA, Timestamp.from(NOW));
	}

}
