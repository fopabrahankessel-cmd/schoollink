package com.schoollink.attendance.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.attendance.AbsenceCancelled;
import com.schoollink.lateness.LatenessCancelled;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D6, task 3.2: the DM cancels an absence or a lateness recorded by mistake (BR-13), the row
 * stays and no longer counts (BR-15), and the parents who were told get a correction (BR-14, D11).
 * Monday 2 November 2026, 08:40 in Douala. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@RecordApplicationEvents
@Transactional
class CancellationTests {

	static final String MONDAY = "2026-11-02";

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	static final Map<String, Object> REASON = Map.of("reason", "Erreur de saisie");

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

	/** PARENT_A's guardian record: the app, by push. */
	UUID parentGuardian;

	/** A second guardian of student A, without the app: by SMS. */
	UUID smsGuardian;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		parentGuardian = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class,
				world.user(PARENT_A));
		smsGuardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, 'Tuteur Fictif', '+237690000098', 'EN')
				RETURNING id""", UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', false)""", world.schoolX, world.studentA, smsGuardian);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (BR-13, NFR-8)

	@TestFactory
	Stream<DynamicTest> onlyTheDmCancelsAnAbsence() {
		UUID absence = record(world.studentA, "ABSENT");
		return PermissionMatrix.row("POST /records/{id}/cancel", w -> cancelRequest("records", absence, REASON))
			.expect(200, DM_1)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheDmCancelsALateness() { // D11: not the assistant DM who recorded it
		UUID late = lateness(world.studentA);
		return PermissionMatrix.row("POST /lateness/{id}/cancel", w -> cancelRequest("lateness", late, REASON))
			.expect(200, DM_1)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void aDmOutsideTheBlockChangesNothing() throws Exception { // BR-13, NFR-8
		UUID absence = record(world.studentA, "ABSENT");
		UUID late = lateness(world.studentA);

		cancel(DM_2, "records", absence, REASON).andExpect(status().isForbidden());
		cancel(DM_2, "lateness", late, REASON).andExpect(status().isForbidden());

		assertThat(cancelledAt("attendance_record", absence)).isNull();
		assertThat(cancelledAt("lateness_record", late)).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'CANCEL'", Integer.class)).isZero();
	}

	@Test
	void aRecordOfAnotherSchoolOrUnknownIsNotFound() throws Exception { // NFR-8
		UUID periodY = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, 1, '07:30', '08:25') RETURNING id""",
				UUID.class, world.schoolY);
		UUID studentY = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-Y01', 'Student', 'Fictional', 'F', 2012) RETURNING id""", UUID.class, world.schoolY,
				world.classC);
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", session, world.schoolY, world.classC, LocalDate.parse(MONDAY), periodY,
				world.user(ADMIN_Y), Timestamp.from(NOW));
		UUID theirs = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, 'ABSENT')""", theirs, world.schoolY, session, studentY);

		cancel(DM_1, "records", theirs, REASON).andExpect(status().isNotFound());
		cancel(DM_1, "records", UUID.randomUUID(), REASON).andExpect(status().isNotFound());
		cancel(DM_1, "lateness", UUID.randomUUID(), REASON).andExpect(status().isNotFound());
		assertThat(cancelledAt("attendance_record", theirs)).isNull();
	}

	// Cancelling (BR-13, BR-15, NFR-11)

	@Test
	void anAbsenceIsCancelledKeptAuditedAndPublished() throws Exception { // BR-13, BR-15, NFR-11
		UUID absence = record(world.studentA, "ABSENT");

		cancel(DM_1, "records", absence, Map.of("reason", "  Élève présent, mauvaise ligne cochée  "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(absence.toString()))
			.andExpect(jsonPath("$.cancelledAt").value("2026-11-02T07:40:00Z"))
			.andExpect(jsonPath("$.cancelledBy").value(world.user(DM_1).toString()))
			.andExpect(jsonPath("$.reason").value("Élève présent, mauvaise ligne cochée"));

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT status, cancelled_at, cancelled_by, cancel_reason FROM attendance_record WHERE id = ?", absence);
		assertThat(row).containsEntry("status", "ABSENT")
			.containsEntry("cancelled_at", Timestamp.from(NOW))
			.containsEntry("cancelled_by", world.user(DM_1))
			.containsEntry("cancel_reason", "Élève présent, mauvaise ligne cochée");
		assertThat(events.stream(AbsenceCancelled.class)).containsExactly(new AbsenceCancelled(absence, world.schoolX,
				world.studentA, LocalDate.parse(MONDAY), world.user(DM_1), NOW));
		Map<String, Object> audit = audit("attendance_record", absence);
		assertThat(audit).containsEntry("actor_id", world.user(DM_1));
		assertThat((String) audit.get("before")).contains("\"cancelledAt\": null", "\"status\": \"ABSENT\"");
		assertThat((String) audit.get("after")).contains("\"cancelledAt\": \"2026-11-02T07:40:00Z\"",
				"\"cancelReason\": \"Élève présent, mauvaise ligne cochée\"", world.user(DM_1).toString());
	}

	@Test
	void aCancelledAbsenceNoLongerCounts() throws Exception { // BR-15
		UUID absence = record(world.studentA, "ABSENT");
		history().andExpect(jsonPath("$.totals.absentPeriods").value(1));

		cancel(DM_1, "records", absence, REASON).andExpect(status().isOk());

		history().andExpect(jsonPath("$.totals.absentPeriods").value(0));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE id = ?", Integer.class, absence))
			.isOne();
	}

	@Test
	void onlyAnAbsenceIsCancelledAndOnlyOnce() throws Exception { // BR-13, D20
		UUID classmate = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0003', 'Camarade', 'Fictif', 'F', 2012) RETURNING id""", UUID.class, world.schoolX,
				world.classA);
		UUID present = record(classmate, "PRESENT");
		cancel(DM_1, "records", present, REASON).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("cancel.not_an_absence"));

		UUID absence = record(world.studentA, "ABSENT");
		cancel(DM_1, "records", absence, REASON).andExpect(status().isOk());
		cancel(DM_1, "records", absence, Map.of("reason", "Encore")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("cancel.already_cancelled"));
		assertThat(jdbc.queryForObject("SELECT cancel_reason FROM attendance_record WHERE id = ?", String.class, absence))
			.isEqualTo("Erreur de saisie");
	}

	@Test
	void theReasonIsMandatory() throws Exception { // BR-13
		UUID absence = record(world.studentA, "ABSENT");
		UUID late = lateness(world.studentA);

		cancel(DM_1, "records", absence, Map.of()).andExpect(status().isBadRequest());
		cancel(DM_1, "records", absence, Map.of("reason", "   ")).andExpect(status().isBadRequest());
		cancel(DM_1, "records", absence, Map.of("reason", "x".repeat(301))).andExpect(status().isBadRequest());
		cancel(DM_1, "lateness", late, Map.of("reason", "")).andExpect(status().isBadRequest());

		assertThat(cancelledAt("attendance_record", absence)).isNull();
		assertThat(cancelledAt("lateness_record", late)).isNull();
	}

	@Test
	void aCancelledLatenessCanBeRecordedAgain() throws Exception { // BR-15, D20
		recordLateness(UUID.randomUUID(), "07:48").andExpect(status().isCreated());
		UUID late = lastLateness();

		cancel(DM_1, "lateness", late, REASON).andExpect(status().isOk())
			.andExpect(jsonPath("$.reason").value("Erreur de saisie"));

		assertThat(events.stream(LatenessCancelled.class)).containsExactly(new LatenessCancelled(late, world.schoolX,
				world.studentA, LocalDate.parse(MONDAY), world.user(DM_1), NOW));
		assertThat((String) audit("lateness_record", late).get("after")).contains("\"cancelReason\": \"Erreur de saisie\"");
		cancel(DM_1, "lateness", late, REASON).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("cancel.already_cancelled"));
		// the right arrival time, under a new id
		recordLateness(UUID.randomUUID(), "08:05").andExpect(status().isCreated());
		perform(DM_1, get("/api/v1/lateness")).andExpect(jsonPath("$.lateness.length()").value(1))
			.andExpect(jsonPath("$.lateness[0].arrivalTime").value("08:05:00"));
		history().andExpect(jsonPath("$.totals.lateness").value(1));
	}

	@Test
	void attendanceIsNotTouchedByALatenessCancellation() throws Exception { // BR-8, BR-9
		UUID absence = record(world.studentA, "ABSENT");
		UUID late = lateness(world.studentA);

		cancel(DM_1, "lateness", late, REASON).andExpect(status().isOk());

		assertThat(cancelledAt("attendance_record", absence)).isNull();
	}

	// Corrections (BR-14, D11, NFR-6)

	@Test
	void notificationsNotSentYetAreWithdrawnAndNotCorrected() throws Exception { // BR-14
		UUID absence = record(world.studentA, "ABSENT");
		UUID push = outbox(parentGuardian, "ABSENCE", absence, "PUSH", "QUEUED");
		UUID sms = outbox(smsGuardian, "ABSENCE", absence, "SMS", "HELD");

		cancel(DM_1, "records", absence, REASON).andExpect(status().isOk());

		assertThat(statusOf(push)).isEqualTo("WITHDRAWN");
		assertThat(statusOf(sms)).isEqualTo("WITHDRAWN");
		assertThat(corrections(absence)).isEmpty();
		// the parent never hears of it
		perform(PARENT_A, get("/api/v1/me/notifications")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void notificationsAlreadySentGetACorrectionOnTheSameChannel() throws Exception { // BR-14, D11
		UUID absence = record(world.studentA, "ABSENT");
		outbox(parentGuardian, "ABSENCE", absence, "PUSH", "OPENED");
		outbox(smsGuardian, "ABSENCE", absence, "SMS", "SENT");
		// BR-20: the correction goes in the language the guardian has now
		jdbc.update("UPDATE guardian SET language = 'EN' WHERE id = ?", parentGuardian);

		cancel(DM_1, "records", absence, REASON).andExpect(status().isOk());

		List<Map<String, Object>> rows = corrections(absence);
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0)).containsEntry("guardian_id", parentGuardian)
			.containsEntry("student_id", world.studentA)
			.containsEntry("channel", "PUSH")
			.containsEntry("language", "EN")
			.containsEntry("status", "QUEUED");
		// D11: the SMS goes at once, not in the digest
		assertThat(rows.get(1)).containsEntry("guardian_id", smsGuardian)
			.containsEntry("channel", "SMS")
			.containsEntry("language", "EN")
			.containsEntry("status", "QUEUED");
		JsonNode payload = json.readTree((String) rows.get(0).get("payload"));
		assertThat(payload.get("kind").asString()).isEqualTo("ABSENCE");
		assertThat(payload.get("firstName").asString()).isEqualTo("Awa");
		assertThat(payload.get("date").asString()).isEqualTo(MONDAY);
		assertThat(payload.get("periodNumber").asInt()).isEqualTo(1);
		assertThat(payload.get("studentId").asString()).isEqualTo(world.studentA.toString());
		// the original stays as it was, for the research metrics
		assertThat(jdbc.queryForObject("""
				SELECT status FROM notification_outbox WHERE event_type = 'ABSENCE' AND event_id = ? AND channel = 'PUSH'""",
				String.class, absence)).isEqualTo("OPENED");
	}

	@Test
	void aNotificationBeingSentIsCorrectedAndAFailedOneIsNot() throws Exception { // BR-14
		UUID absence = record(world.studentA, "ABSENT");
		outbox(parentGuardian, "ABSENCE", absence, "PUSH", "SENDING");
		UUID failed = outbox(smsGuardian, "ABSENCE", absence, "SMS", "FAILED");

		cancel(DM_1, "records", absence, REASON).andExpect(status().isOk());

		assertThat(corrections(absence)).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("guardian_id", parentGuardian));
		assertThat(statusOf(failed)).isEqualTo("FAILED");
	}

	@Test
	void aLatenessFromTheGateIsWithdrawnOrCorrected() throws Exception { // BR-14, D11, NFR-6, end to end
		recordLateness(UUID.randomUUID(), "07:48").andExpect(status().isCreated());
		UUID late = lastLateness();
		// the push has gone; the SMS waits for the evening digest
		jdbc.update("""
				UPDATE notification_outbox SET status = 'SENT', sent_at = now()
				WHERE event_type = 'LATENESS' AND event_id = ? AND channel = 'PUSH'""", late);

		cancel(DM_1, "lateness", late, REASON).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("""
				SELECT status FROM notification_outbox WHERE event_type = 'LATENESS' AND event_id = ? AND channel = 'SMS'""",
				String.class, late)).isEqualTo("WITHDRAWN");
		List<Map<String, Object>> rows = corrections(late);
		assertThat(rows).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("guardian_id", parentGuardian)
				.containsEntry("channel", "PUSH")
				.containsEntry("language", "FR"));
		JsonNode payload = json.readTree((String) rows.get(0).get("payload"));
		assertThat(payload.get("kind").asString()).isEqualTo("LATENESS");
		assertThat(payload.has("periodNumber")).isFalse();
		// the inbox shows the lateness and its correction (one transaction here, so no order between them)
		perform(PARENT_A, get("/api/v1/me/notifications")).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[*].eventType").value(containsInAnyOrder("LATENESS", "CORRECTION")));
	}

	private ResultActions cancel(Actor actor, String kind, UUID id, Map<String, Object> body) throws Exception {
		MockHttpServletRequestBuilder request = cancelRequest(kind, id, body);
		if (actor != ANONYMOUS) {
			request.header("Authorization", "Bearer " + world.token(actor));
		}
		return mockMvc.perform(request);
	}

	private MockHttpServletRequestBuilder cancelRequest(String kind, UUID id, Map<String, Object> body) {
		return post("/api/v1/" + kind + "/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private ResultActions history() throws Exception {
		return perform(PARENT_A, get("/api/v1/children/{id}/history", world.studentA)).andExpect(status().isOk());
	}

	/** Through the gate endpoint, so the outbox rows are the real ones. */
	private ResultActions recordLateness(UUID id, String arrival) throws Exception {
		Map<String, Object> body = Map.of("studentId", world.studentA, "date", MONDAY, "arrivalTime", arrival,
				"recordedAt", "2026-11-02T07:13:00Z");
		return perform(ASSISTANT_DM_1, put("/api/v1/lateness/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body)));
	}

	private UUID lastLateness() {
		return jdbc.queryForObject("""
				SELECT id FROM lateness_record WHERE student_id = ? AND cancelled_at IS NULL""", UUID.class,
				world.studentA);
	}

	private Object cancelledAt(String table, UUID id) {
		return jdbc.queryForObject("SELECT cancelled_at FROM " + table + " WHERE id = ?", Timestamp.class, id);
	}

	private Map<String, Object> audit(String entity, UUID id) {
		return new HashMap<>(jdbc.queryForMap("""
				SELECT actor_id, before::text AS before, after::text AS after FROM audit_log
				WHERE action = 'CANCEL' AND entity = ? AND entity_id = ?""", entity, id.toString()));
	}

	private String statusOf(UUID outboxId) {
		return jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, outboxId);
	}

	private List<Map<String, Object>> corrections(UUID eventId) {
		return jdbc.queryForList("""
				SELECT guardian_id, student_id, channel, language, status, payload::text AS payload FROM notification_outbox
				WHERE event_type = 'CORRECTION' AND event_id = ? ORDER BY channel""", eventId);
	}

	private UUID outbox(UUID guardian, String type, UUID eventId, String channel, String status) {
		String payload = type.equals("ABSENCE")
				? "{\"firstName\":\"Awa\",\"date\":\"2026-11-02\",\"periodNumber\":1,\"periodStart\":\"07:30\"}"
				: "{\"firstName\":\"Awa\",\"date\":\"2026-11-02\",\"arrivalTime\":\"07:48\"}";
		return jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				                                 payload, status, claimed_at)
				VALUES (?, ?, ?, ?, ?, ?, 'FR', CAST(? AS jsonb), ?, ?) RETURNING id""", UUID.class, world.schoolX,
				guardian, world.studentA, type, eventId, channel, payload, status,
				// the dispatcher holds a SENDING row
				"SENDING".equals(status) ? Timestamp.from(NOW) : null);
	}

	/** A row of student A in a confirmed session of Monday, first period. */
	private UUID record(UUID student, String status) {
		UUID session = jdbc.queryForList("""
				SELECT id FROM attendance_session WHERE class_id = ? AND date = ? AND period_id = ?""", UUID.class,
				world.classA, LocalDate.parse(MONDAY), world.period1).stream().findFirst().orElseGet(() -> {
					UUID id = UUID.randomUUID();
					jdbc.update("""
							INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
							VALUES (?, ?, ?, ?, ?, ?, ?)""", id, world.schoolX, world.classA, LocalDate.parse(MONDAY),
							world.period1, world.user(TEACHER_A), Timestamp.from(NOW.minusSeconds(3_600)));
					return id;
				});
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, ?)""", id, world.schoolX, session, student, status);
		return id;
	}

	private UUID lateness(UUID student) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, '07:48', ?, ?)""", id, world.schoolX, student, LocalDate.parse(MONDAY),
				world.user(ASSISTANT_DM_1), Timestamp.from(NOW));
		return id;
	}

}
