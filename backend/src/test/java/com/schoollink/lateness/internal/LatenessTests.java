package com.schoollink.lateness.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.lateness.LatenessRecorded;
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
 * UC-AD1, UC-AD2: lateness at the gate (task 2.8, BR-7 to BR-9, D17, D20, NFR-3, NFR-8, NFR-11).
 * ASSISTANT_DM_1 covers block 1, so class 6e A (student A); 5e B is in block 2 (DM_2). Monday
 * 2 November 2026, 08:40 in Douala; school start time 07:30. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@RecordApplicationEvents
@Transactional
class LatenessTests {

	static final String MONDAY = "2026-11-02";

	/** 08:40 in Douala, Monday. */
	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	/** Saved on the device at 08:13 in Douala. */
	static final String RECORDED_AT = "2026-11-02T07:13:00.123456Z";

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

	/** 5e B, block 2: outside ASSISTANT_DM_1's scope. */
	UUID studentB;

	/** School Y. */
	UUID studentY;

	/** A second guardian of student A, without an app account. */
	UUID smsGuardian;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		studentB = student(world.schoolX, world.classB, "TW-0101", "Brice", "Fictif");
		studentY = student(world.schoolY, world.classC, "TW-0201", "Yann", "Fictif");
		smsGuardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, 'Tuteur Fictif', '+237690000099', 'EN')
				RETURNING id""", UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'FATHER', false)""", world.schoolX, world.studentA, smsGuardian);
		clock.set(NOW);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (NFR-8)

	@TestFactory
	Stream<DynamicTest> onlyAnAssistantDmRecordsLateness() {
		return PermissionMatrix.row("PUT /lateness/{id}", w -> request(UUID.randomUUID(), Map.of()))
			.expect(201, ASSISTANT_DM_1)
			.expect(403, ADMIN_X, TEACHER_A, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyAnAssistantDmSearchesTheGateList() {
		return PermissionMatrix.row("GET /lateness/students", w -> get("/api/v1/lateness/students"))
			.expect(200, ASSISTANT_DM_1)
			.expect(403, ADMIN_X, TEACHER_A, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> theDayListIsForTheAdminAssistantDmsAndDms() {
		return PermissionMatrix.row("GET /lateness", w -> get("/api/v1/lateness"))
			.expect(200, ADMIN_X, ASSISTANT_DM_1, DM_1, DM_2, ADMIN_Y)
			.expect(403, TEACHER_A, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	// Recording (BR-7, NFR-3)

	@Test
	void aLatenessIsStoredAuditedAndPublished() throws Exception { // BR-7, NFR-11
		UUID id = UUID.randomUUID();

		save(id, Map.of()).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.studentId").value(world.studentA.toString()))
			.andExpect(jsonPath("$.date").value(MONDAY))
			.andExpect(jsonPath("$.arrivalTime").value("08:12:00"))
			.andExpect(jsonPath("$.recordedAt").value(RECORDED_AT))
			.andExpect(jsonPath("$.syncedAt").value("2026-11-02T07:40:00Z"));

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM lateness_record WHERE id = ?", id);
		assertThat(row).containsEntry("school_id", world.schoolX)
			.containsEntry("student_id", world.studentA)
			.containsEntry("recorded_by", world.user(ASSISTANT_DM_1))
			.containsEntry("recorded_at", Timestamp.from(Instant.parse(RECORDED_AT)))
			.containsEntry("synced_at", Timestamp.from(NOW))
			.containsEntry("justification_id", null);
		assertThat(row.get("arrival_time").toString()).isEqualTo("08:12:00");
		assertThat(events.stream(LatenessRecorded.class)).containsExactly(new LatenessRecorded(id, world.schoolX,
				world.studentA, world.classA, LocalDate.parse(MONDAY), LocalTime.of(8, 12), world.user(ASSISTANT_DM_1),
				Instant.parse(RECORDED_AT)));
		Map<String, Object> audit = jdbc.queryForMap(
				"SELECT actor_id, action, after::text AS after FROM audit_log WHERE entity = 'lateness_record' AND entity_id = ?",
				id.toString());
		assertThat(audit).containsEntry("actor_id", world.user(ASSISTANT_DM_1)).containsEntry("action", "CREATE");
		assertThat((String) audit.get("after")).contains(world.studentA.toString(), "08:12").doesNotContain("Fictif");
	}

	@Test
	void eachGuardianIsNotifiedInTheSameTransaction() throws Exception { // BR-20, BR-21, BR-22, NFR-6, D33
		UUID id = UUID.randomUUID();
		save(id, Map.of()).andExpect(status().isCreated());

		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT o.guardian_id, o.student_id, o.event_type, o.event_id, o.channel, o.language, o.status,
				       o.payload::text AS payload
				FROM notification_outbox o JOIN guardian g ON g.id = o.guardian_id
				WHERE o.event_id = ? ORDER BY o.channel, g.user_id IS NULL""", id);
		UUID parentGuardian = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class,
				world.user(PARENT_A));
		assertThat(rows).hasSize(3);
		// D33: the app user's SMS is held too, in case the push is not opened
		assertThat(rows.get(1)).containsEntry("guardian_id", parentGuardian)
			.containsEntry("channel", "SMS")
			.containsEntry("status", "HELD");
		assertThat(rows.get(0)).containsEntry("guardian_id", parentGuardian)
			.containsEntry("student_id", world.studentA)
			.containsEntry("event_type", "LATENESS")
			.containsEntry("channel", "PUSH")
			.containsEntry("language", "FR")
			.containsEntry("status", "QUEUED");
		assertThat(rows.get(2)).containsEntry("guardian_id", smsGuardian)
			.containsEntry("channel", "SMS")
			.containsEntry("language", "EN")
			.containsEntry("status", "HELD");
		assertThat(json.readTree((String) rows.get(0).get("payload")).get("arrivalTime").asString()).isEqualTo("08:12");
		assertThat(json.readTree((String) rows.get(0).get("payload")).get("firstName").asString()).isEqualTo("Élève");
	}

	@Test
	void aReplayReturnsTheStoredRecordAndNotifiesNobodyAgain() throws Exception { // NFR-3
		UUID id = UUID.randomUUID();
		save(id, Map.of()).andExpect(status().isCreated());
		clock.set(NOW.plusSeconds(600));

		save(id, Map.of()).andExpect(status().isOk())
			.andExpect(jsonPath("$.syncedAt").value("2026-11-02T07:40:00Z"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM lateness_record", Integer.class)).isOne();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE event_id = ?", Integer.class, id))
			.isEqualTo(3);
		assertThat(events.stream(LatenessRecorded.class)).hasSize(1);
	}

	@Test
	void theSameIdWithOtherContentIsRefused() throws Exception { // D20: a record never changes
		UUID id = UUID.randomUUID();
		save(id, Map.of()).andExpect(status().isCreated());

		save(id, Map.of("arrivalTime", "08:20")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("lateness.record_locked"));
		assertThat(jdbc.queryForObject("SELECT arrival_time FROM lateness_record WHERE id = ?", LocalTime.class, id))
			.isEqualTo(LocalTime.of(8, 12));
	}

	@Test
	void oneLatenessPerStudentPerDay() throws Exception { // BR-7, D20
		UUID first = UUID.randomUUID();
		save(first, Map.of()).andExpect(status().isCreated());

		save(UUID.randomUUID(), Map.of("arrivalTime", "08:30", "recordedAt", "2026-11-02T07:31:00Z")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("lateness.already_recorded"))
			.andExpect(jsonPath("$.message").value("Le retard de cet élève a déjà été enregistré aujourd'hui."));

		// a cancelled record no longer counts (BR-15): the student can be recorded again
		jdbc.update("""
				UPDATE lateness_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
				WHERE id = ?""", world.user(DM_1), first);
		save(UUID.randomUUID(), Map.of("arrivalTime", "08:30", "recordedAt", "2026-11-02T07:31:00Z")).andExpect(status().isCreated());
	}

	@Test
	void aStudentOutsideTheScopeIsNotFound() throws Exception { // NFR-8, D17
		save(UUID.randomUUID(), Map.of("studentId", studentB)).andExpect(status().isNotFound());
		save(UUID.randomUUID(), Map.of("studentId", studentY)).andExpect(status().isNotFound());
		save(UUID.randomUUID(), Map.of("studentId", UUID.randomUUID())).andExpect(status().isNotFound());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lateness_record", Integer.class)).isZero();
	}

	@Test
	void attendanceIsNeitherReadNorChanged() throws Exception { // BR-8, BR-9
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", session, world.schoolX, world.classA, LocalDate.parse(MONDAY),
				world.period1, world.user(TEACHER_A), Timestamp.from(Instant.parse("2026-11-02T06:45:00Z")));
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, 'ABSENT')""", UUID.randomUUID(), world.schoolX, session, world.studentA);
		List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM attendance_record ORDER BY id");

		save(UUID.randomUUID(), Map.of()).andExpect(status().isCreated());

		// absent for period 1 and late: both stay, nothing reconciles them
		assertThat(jdbc.queryForList("SELECT * FROM attendance_record ORDER BY id")).isEqualTo(before);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_session", Integer.class)).isOne();
	}

	// Validation

	@Test
	void theRequestIsValidated() throws Exception {
		// BR-7: at or before the start time is not late
		expectInvalid(Map.of("arrivalTime", "07:30"), "lateness.not_late", "arrivalTime")
			.andExpect(jsonPath("$.message").value("L'heure d'arrivée doit être après l'heure de début des cours (07:30)."));
		expectInvalid(Map.of("arrivalTime", "07:10"), "lateness.not_late", "arrivalTime");
		expectInvalid(Map.of("date", "2026-11-03"), "lateness.date_in_future", "date");
		// D3, D10: a Saturday, outside the term
		expectInvalid(Map.of("date", "2026-10-31", "recordedAt", "2026-10-31T08:00:00Z"), "lateness.not_a_school_day",
				"date");
		expectInvalid(Map.of("date", "2026-09-04", "recordedAt", "2026-09-04T08:00:00Z"), "lateness.not_a_school_day",
				"date");
		// it is 08:40: an arrival at 09:00 has not happened yet
		expectInvalid(Map.of("arrivalTime", "09:00", "recordedAt", "2026-11-02T07:39:00Z"), "lateness.arrival_in_future",
				"arrivalTime");
		// saved before the student arrived, or in the future
		expectInvalid(Map.of("recordedAt", "2026-11-02T06:50:00Z"), "lateness.recorded_at_invalid", "recordedAt");
		expectInvalid(Map.of("recordedAt", "2026-11-02T08:00:00Z"), "lateness.recorded_at_invalid", "recordedAt");
		perform(ASSISTANT_DM_1, put("/api/v1/lateness/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + world.studentA + "\",\"date\":\"" + MONDAY + "\",\"recordedAt\":\"" + RECORDED_AT
					+ "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("arrivalTime"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM lateness_record", Integer.class)).isZero();
	}

	@Test
	void anEarlierSchoolDayCanBeSyncedLater() throws Exception { // NFR-3: recorded offline on Friday
		save(UUID.randomUUID(), Map.of("date", "2026-10-30", "arrivalTime", "07:52", "recordedAt", "2026-10-30T06:53:00Z"))
			.andExpect(status().isCreated());
	}

	// Gate search (UC-AD1)

	@Test
	void theGateListHoldsTheWholeScopeForOfflineSearch() throws Exception { // NFR-3, NFR-8
		UUID amina = student(world.schoolX, world.classA, "TW-0002", "Amina", "Abena");

		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(amina.toString()))
			.andExpect(jsonPath("$[0].matricule").value("TW-0002"))
			.andExpect(jsonPath("$[0].className").value("6e A"))
			.andExpect(jsonPath("$[1].id").value(world.studentA.toString()))
			.andExpect(jsonPath("$[0].birthYear").doesNotExist());
	}

	@Test
	void theGateSearchFindsANameOrAMatriculeInTheScopeOnly() throws Exception {
		student(world.schoolX, world.classA, "TW-0002", "Amina", "Abena");
		student(world.schoolX, world.classB, "TW-0003", "Amina", "Hors-Bloc");

		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students").param("q", "amina")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].lastName").value("Abena"));
		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students").param("q", "abena AMINA"))
			.andExpect(jsonPath("$.length()").value(1));
		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students").param("q", "tw-0001"))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(world.studentA.toString()));
		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students").param("q", "TW-0003"))
			.andExpect(jsonPath("$.length()").value(0));
		// wildcards are plain characters
		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students").param("q", "%")).andExpect(jsonPath("$.length()").value(0));
		perform(ASSISTANT_DM_1, get("/api/v1/lateness/students").param("q", "x".repeat(101)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.field").value("q"));
	}

	// The day's list (UC-AD2)

	@Test
	void theDayListShowsLiveLatenessInTheCallersScope() throws Exception { // BR-15, NFR-8
		save(UUID.randomUUID(), Map.of()).andExpect(status().isCreated());
		UUID inBlock2 = lateness(studentB, "08:05", null);
		lateness(student(world.schoolX, world.classA, "TW-0002", "Amina", "Abena"), "07:45", world.user(DM_1));

		perform(ASSISTANT_DM_1, get("/api/v1/lateness")).andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value(MONDAY))
			.andExpect(jsonPath("$.startTime").value("07:30:00"))
			.andExpect(jsonPath("$.lateness.length()").value(1))
			.andExpect(jsonPath("$.lateness[0].studentId").value(world.studentA.toString()))
			.andExpect(jsonPath("$.lateness[0].firstName").value("Élève"))
			.andExpect(jsonPath("$.lateness[0].className").value("6e A"))
			.andExpect(jsonPath("$.lateness[0].arrivalTime").value("08:12:00"))
			.andExpect(jsonPath("$.lateness[0].justified").value(false));
		perform(DM_2, get("/api/v1/lateness")).andExpect(jsonPath("$.lateness.length()").value(1))
			.andExpect(jsonPath("$.lateness[0].id").value(inBlock2.toString()));
		perform(ADMIN_X, get("/api/v1/lateness")).andExpect(jsonPath("$.lateness.length()").value(2))
			.andExpect(jsonPath("$.lateness[0].arrivalTime").value("08:12:00"))
			.andExpect(jsonPath("$.lateness[1].arrivalTime").value("08:05:00"));
		perform(ADMIN_Y, get("/api/v1/lateness")).andExpect(jsonPath("$.lateness.length()").value(0));
		perform(ADMIN_X, get("/api/v1/lateness").param("date", "2026-10-30"))
			.andExpect(jsonPath("$.date").value("2026-10-30"))
			.andExpect(jsonPath("$.lateness.length()").value(0));
	}

	private ResultActions save(UUID id, Map<String, Object> overrides) throws Exception {
		return perform(ASSISTANT_DM_1, request(id, overrides));
	}

	private ResultActions expectInvalid(Map<String, Object> overrides, String code, String field) throws Exception {
		return save(UUID.randomUUID(), overrides).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.field").value(field));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	/** Student A, Monday, arrived 08:12, saved at 08:13; {@code overrides} replace fields. */
	private MockHttpServletRequestBuilder request(UUID id, Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(Map.of("studentId", world.studentA, "date", MONDAY, "arrivalTime",
				"08:12", "recordedAt", RECORDED_AT));
		body.putAll(overrides);
		return put("/api/v1/lateness/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body));
	}

	private UUID student(UUID school, UUID classId, String matricule, String firstName, String lastName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, ?, 'M', 2012) RETURNING id""", UUID.class, school, classId, matricule, firstName,
				lastName);
	}

	/** A lateness of Monday stored directly; cancelled by {@code cancelledBy} when not null. */
	private UUID lateness(UUID student, String arrival, UUID cancelledBy) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, ?::time, ?, ?)""", id, world.schoolX, student, LocalDate.parse(MONDAY), arrival,
				world.user(ASSISTANT_DM_1), Timestamp.from(NOW));
		if (cancelledBy != null) {
			jdbc.update("""
					UPDATE lateness_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
					WHERE id = ?""", cancelledBy, id);
		}
		return id;
	}

}
