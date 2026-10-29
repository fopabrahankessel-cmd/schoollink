package com.schoollink.attendance.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.attendance.AttendanceConfirmed;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-T1, UC-T2 {@code PUT /sessions/{uuid}} (task 2.3, BR-1 to BR-6, NFR-8, NFR-11). TEACHER_A
 * teaches 6e A (students A, B, C) on Monday 2 November 2026, period 1. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@RecordApplicationEvents
@Transactional
class SessionRecordTests {

	static final String MONDAY = "2026-11-02";

	/** 08:40 in Douala, Monday. */
	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	/** Confirmed on the device at 08:20 in Douala. */
	static final String CONFIRMED_AT = "2026-11-02T07:20:00.123456Z";

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

	/** Tokens of the extra teachers made by {@link #otherTeacherOf}. */
	final Map<UUID, String> otherTokens = new HashMap<>();

	UUID studentB;

	UUID studentC;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		studentB = student(world.classA, "TW-0102");
		studentC = student(world.classA, "TW-0103");
		clock.set(NOW);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@TestFactory
	Stream<DynamicTest> onlyTeachersRecordSessions() { // NFR-8, D17
		return PermissionMatrix
			.row("PUT session", w -> put("/api/v1/sessions/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(body(Map.of())))
			.expect(201, TEACHER_A)
			.expect(403, ADMIN_X, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void everyStudentGetsARecordAndOnlyAbsenteesAreAbsent() throws Exception { // BR-2
		UUID id = UUID.randomUUID();

		save(id, Map.of("absentStudentIds", List.of(world.studentA))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.studentCount").value(3))
			.andExpect(jsonPath("$.absentStudentIds[0]").value(world.studentA.toString()));

		assertThat(jdbc.queryForList("SELECT status FROM attendance_record WHERE session_id = ? ORDER BY status",
				String.class, id))
			.containsExactly("ABSENT", "PRESENT", "PRESENT");
		var session = jdbc.queryForMap("SELECT teacher_id, confirmed_at, synced_at FROM attendance_session WHERE id = ?", id);
		assertThat(session.get("teacher_id")).isEqualTo(world.user(TEACHER_A));
		// BR-6: the lock time is the device's confirmation, the sync time the server's
		assertThat(((Timestamp) session.get("confirmed_at")).toInstant()).isEqualTo(Instant.parse(CONFIRMED_AT));
		assertThat(((Timestamp) session.get("synced_at")).toInstant()).isEqualTo(NOW);
	}

	@Test
	void aSessionWithoutAbsenteesIsStored() throws Exception { // BR-5
		UUID id = UUID.randomUUID();

		save(id, Map.of()).andExpect(status().isCreated()).andExpect(jsonPath("$.absentStudentIds").isEmpty());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE session_id = ? AND status = 'PRESENT'",
				Integer.class, id))
			.isEqualTo(3);
	}

	@Test
	void replayingTheSameUuidStoresNothingTwice() throws Exception { // BR-6, UC-T2
		UUID id = UUID.randomUUID();
		save(id, Map.of("absentStudentIds", List.of(world.studentA))).andExpect(status().isCreated());

		save(id, Map.of("absentStudentIds", List.of(world.studentA))).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.confirmedAt").value(CONFIRMED_AT));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_session WHERE id = ?", Integer.class, id)).isOne();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE session_id = ?", Integer.class, id))
			.isEqualTo(3);
		assertThat(events.stream(AttendanceConfirmed.class)).hasSize(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity = 'attendance_session' AND entity_id = ?",
				Integer.class, id.toString()))
			.isOne();
	}

	@Test
	void anotherUuidForTheSameClassDateAndPeriodIsAConflict() throws Exception { // BR-1
		save(UUID.randomUUID(), Map.of()).andExpect(status().isCreated());

		UUID second = UUID.randomUUID();
		save(second, Map.of("absentStudentIds", List.of(studentB))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("attendance.session_exists"));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_session WHERE id = ?", Integer.class, second))
			.isZero();
	}

	@Test
	void aLockedSessionCannotBeChanged() throws Exception { // BR-4
		UUID id = UUID.randomUUID();
		save(id, Map.of("absentStudentIds", List.of(world.studentA))).andExpect(status().isCreated());

		save(id, Map.of("absentStudentIds", List.of(world.studentA, studentB))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("attendance.session_locked"));
		save(id, Map.of("absentStudentIds", List.of())).andExpect(status().isConflict());
		save(id, Map.of("absentStudentIds", List.of(world.studentA), "confirmedAt", "2026-11-02T07:25:00Z"))
			.andExpect(status().isConflict());

		assertThat(jdbc.queryForList("SELECT student_id FROM attendance_record WHERE session_id = ? AND status = 'ABSENT'",
				UUID.class, id))
			.containsExactly(world.studentA);
	}

	@Test
	void aTeacherNotAssignedToTheClassIsForbidden() throws Exception { // BR-1
		save(UUID.randomUUID(), Map.of("classId", world.classB)).andExpect(status().isForbidden());

		UUID other = otherTeacherOf(world.classB);
		save(UUID.randomUUID(), Map.of(), other).andExpect(status().isForbidden());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_session", Integer.class)).isZero();
	}

	@Test
	void anotherSchoolsClassIsNotFound() throws Exception { // NFR-8, D17
		save(UUID.randomUUID(), Map.of("classId", world.classC)).andExpect(status().isNotFound());
	}

	@Test
	void anotherTeachersSessionIsNotFoundUnderItsUuid() throws Exception { // NFR-8
		UUID id = UUID.randomUUID();
		save(id, Map.of()).andExpect(status().isCreated());
		UUID colleague = otherTeacherOf(world.classA);

		save(id, Map.of(), colleague).andExpect(status().isNotFound());
	}

	@Test
	void theEventCarriesTheAbsenteesForTheParents() throws Exception { // D2, BR-20 (outbox in task 2.4)
		UUID id = UUID.randomUUID();
		save(id, Map.of("absentStudentIds", List.of(world.studentA, studentC))).andExpect(status().isCreated());

		assertThat(events.stream(AttendanceConfirmed.class)).singleElement().satisfies(event -> {
			assertThat(event.sessionId()).isEqualTo(id);
			assertThat(event.schoolId()).isEqualTo(world.schoolX);
			assertThat(event.classId()).isEqualTo(world.classA);
			assertThat(event.date()).isEqualTo(LocalDate.parse(MONDAY));
			assertThat(event.confirmedAt()).isEqualTo(Instant.parse(CONFIRMED_AT));
			assertThat(event.absentStudentIds()).containsExactlyInAnyOrder(world.studentA, studentC);
		});
	}

	@Test
	void theSessionIsAudited() throws Exception { // NFR-11
		UUID id = UUID.randomUUID();
		save(id, Map.of("absentStudentIds", List.of(studentB))).andExpect(status().isCreated());

		var row = jdbc.queryForMap(
				"SELECT actor_id, action, after::text AS after FROM audit_log WHERE entity = 'attendance_session' AND entity_id = ?",
				id.toString());
		assertThat(row.get("actor_id")).isEqualTo(world.user(TEACHER_A));
		assertThat(row.get("action")).isEqualTo("CREATE");
		assertThat((String) row.get("after")).contains(studentB.toString()).doesNotContain("Élève");
	}

	@Test
	void invalidSessionsAreRejectedWithTheField() throws Exception {
		expectInvalid(Map.of("absentStudentIds", List.of(UUID.randomUUID())), "attendance.student_not_in_class",
				"absentStudentIds");
		UUID studentOfB = student(world.classB, "TW-0201");
		expectInvalid(Map.of("absentStudentIds", List.of(studentOfB)), "attendance.student_not_in_class",
				"absentStudentIds");
		expectInvalid(Map.of("periodId", UUID.randomUUID()), "reference.unknown", "periodId");
		// D3, D10: Saturday, before the term, a holiday
		expectInvalid(Map.of("date", "2026-10-31", "confirmedAt", "2026-11-02T07:00:00Z"), "attendance.not_a_school_day",
				"date");
		expectInvalid(Map.of("date", "2026-09-04", "confirmedAt", "2026-11-02T07:00:00Z"), "attendance.not_a_school_day",
				"date");
		jdbc.update("""
				INSERT INTO holiday (school_id, term_id, label, start_date, end_date)
				VALUES (?, ?, 'Congé fictif', '2026-10-26', '2026-10-30')""", world.schoolX, world.termX);
		expectInvalid(Map.of("date", "2026-10-26", "confirmedAt", "2026-11-02T07:00:00Z"), "attendance.not_a_school_day",
				"date");
		expectInvalid(Map.of("date", "2026-11-03"), "attendance.date_in_future", "date");
		// confirmed ahead of the server clock, or before the day of the session
		expectInvalid(Map.of("confirmedAt", "2026-11-02T08:00:00Z"), "attendance.confirmed_at_invalid", "confirmedAt");
		expectInvalid(Map.of("date", "2026-10-23", "confirmedAt", "2026-10-22T15:00:00Z"),
				"attendance.confirmed_at_invalid", "confirmedAt");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_session", Integer.class)).isZero();
	}

	@Test
	void aSessionSyncedDaysLaterIsAccepted() throws Exception { // BR-6: offline, synced afterwards
		save(UUID.randomUUID(), Map.of("date", "2026-10-26", "confirmedAt", "2026-10-26T07:20:00Z"))
			.andExpect(status().isCreated());
	}

	@Test
	void missingFieldsAreRejected() throws Exception {
		mockMvc.perform(put("/api/v1/sessions/{id}", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"classId\":\"" + world.classA + "\"}")
			.header("Authorization", "Bearer " + world.token(TEACHER_A))).andExpect(status().isBadRequest());
	}

	@Test
	void errorsAreInTheUsersLanguage() throws Exception { // NFR-12
		save(UUID.randomUUID(), Map.of()).andExpect(status().isCreated());

		save(UUID.randomUUID(), Map.of()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("L'appel de cette classe pour cette période a déjà été enregistré."));
	}

	private void expectInvalid(Map<String, Object> overrides, String code, String field) throws Exception {
		save(UUID.randomUUID(), overrides).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.field").value(field));
	}

	private ResultActions save(UUID id, Map<String, Object> overrides) throws Exception {
		return save(id, overrides, world.user(TEACHER_A));
	}

	private ResultActions save(UUID id, Map<String, Object> overrides, UUID teacher) throws Exception {
		MockHttpServletRequestBuilder request = put("/api/v1/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content(body(overrides))
			.header("Authorization", "Bearer " + token(teacher));
		return mockMvc.perform(request);
	}

	/** Class A, Monday, period 1, confirmed at 08:20, nobody absent; {@code overrides} replace fields. */
	private String body(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(Map.of("classId", world.classA, "date", MONDAY, "periodId",
				world.period1, "confirmedAt", CONFIRMED_AT, "absentStudentIds", List.of()));
		body.putAll(overrides);
		return json.writeValueAsString(body);
	}

	private String token(UUID teacher) {
		return teacher.equals(world.user(TEACHER_A)) ? world.token(TEACHER_A) : otherTokens.get(teacher);
	}

	/** Another teacher, assigned to {@code classId} only. */
	private UUID otherTeacherOf(UUID classId) {
		UUID teacher = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, email, password_hash)
				VALUES (?, 'Autre', 'Enseignant', ?, 'not-a-real-hash') RETURNING id""", UUID.class, world.schoolX,
				"teacher-" + UUID.randomUUID() + "@example.test");
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'TEACHER')", world.schoolX,
				teacher);
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Physique')",
				world.schoolX, teacher, classId);
		otherTokens.put(teacher, world.tokenFor(teacher, "TEACHER"));
		return teacher;
	}

	private UUID student(UUID classId, String matricule) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, 'Élève', 'Fictif', 'F', 2013) RETURNING id""", UUID.class, world.schoolX, classId,
				matricule);
	}

}
