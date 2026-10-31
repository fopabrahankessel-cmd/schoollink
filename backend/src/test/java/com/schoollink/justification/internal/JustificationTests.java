package com.schoollink.justification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.DM_2;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D1, BR-10 to BR-12: the DM in charge justifies one or several absences and lateness at once,
 * and each change is audited (NFR-11). All data is made up.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class JustificationTests {

	static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");

	static final String MONDAY = "2026-11-02";

	static final String TUESDAY = "2026-11-03";

	static final String WEDNESDAY = "2026-11-04";

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

	UUID period2;

	/** In class B: block 2, DM_2's scope. */
	UUID studentB;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, 2, '08:25', '09:20') RETURNING id""",
				UUID.class, world.schoolX);
		studentB = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0002', 'Autre', 'Fictif', 'M', 2011) RETURNING id""", UUID.class, world.schoolX,
				world.classB);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aThreeDayIllnessIsJustifiedAtOnce() throws Exception { // BR-12
		List<UUID> absences = List.of(absent(world.studentA, MONDAY, world.period1), absent(world.studentA, MONDAY, period2),
				absent(world.studentA, TUESDAY, world.period1), absent(world.studentA, WEDNESDAY, period2));
		UUID late = late(world.studentA, TUESDAY);

		JsonNode body = body(justify(DM_1, Map.of("reason", "ILLNESS", "absenceIds", absences, "latenessIds", List.of(late)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.reason").value("ILLNESS"))
			.andExpect(jsonPath("$.recordedAt").value("2026-11-05T09:00:00Z"))
			.andExpect(jsonPath("$.absenceIds.length()").value(4))
			.andExpect(jsonPath("$.latenessIds[0]").value(late.toString())));

		UUID id = UUID.fromString(body.get("id").asString());
		for (UUID absence : absences) {
			assertThat(justificationOf("attendance_record", absence)).isEqualTo(id);
		}
		assertThat(justificationOf("lateness_record", late)).isEqualTo(id);
		Map<String, Object> stored = jdbc.queryForMap("SELECT reason, note, recorded_by FROM justification WHERE id = ?", id);
		assertThat(stored).containsEntry("reason", "ILLNESS").containsEntry("recorded_by", world.user(DM_1));
		assertThat(stored.get("note")).isNull();
	}

	@Test
	void eachRecordIsAudited() throws Exception { // NFR-11
		UUID absence = absent(world.studentA, MONDAY, world.period1);
		UUID late = late(world.studentA, MONDAY);

		JsonNode body = body(justify(DM_1,
				Map.of("reason", "FAMILY", "note", "Deuil", "absenceIds", List.of(absence), "latenessIds", List.of(late))));
		String id = body.get("id").asString();

		assertThat(audit("JUSTIFY", "attendance_record", absence.toString()))
			.containsEntry("actor_id", world.user(DM_1))
			.containsEntry("before", "{\"justificationId\": null}")
			.containsEntry("after", "{\"reason\": \"FAMILY\", \"justificationId\": \"" + id + "\"}");
		assertThat(audit("JUSTIFY", "lateness_record", late.toString())).containsEntry("actor_id", world.user(DM_1));
		assertThat((String) audit("CREATE", "justification", id).get("after")).contains("\"note\": \"Deuil\"",
				absence.toString(), late.toString());
	}

	@Test
	void aNewJustificationReplacesTheOldOneAndTheAuditKeepsIt() throws Exception {
		UUID absence = absent(world.studentA, MONDAY, world.period1);
		String first = body(justify(DM_1, Map.of("reason", "OTHER", "absenceIds", List.of(absence)))).get("id").asString();

		String second = body(justify(DM_1, Map.of("reason", "SCHOOL_ACTIVITY", "absenceIds", List.of(absence)))
			.andExpect(status().isCreated())).get("id").asString();

		assertThat(justificationOf("attendance_record", absence)).hasToString(second);
		assertThat(jdbc.queryForObject("""
				SELECT before->>'justificationId' FROM audit_log
				WHERE action = 'JUSTIFY' AND entity_id = ? ORDER BY id DESC LIMIT 1""", String.class,
				absence.toString())).isEqualTo(first);
	}

	@ParameterizedTest
	@EnumSource(value = Actor.class, names = { "ADMIN_X", "TEACHER_A", "ASSISTANT_DM_1", "COUNSELLOR_X", "PARENT_A",
			"RESEARCHER", "ADMIN_Y" })
	void onlyTheDmJustifies(Actor actor) throws Exception { // BR-11
		UUID absence = absent(world.studentA, MONDAY, world.period1);

		justify(actor, Map.of("reason", "FAMILY", "absenceIds", List.of(absence))).andExpect(status().isForbidden());

		assertThat(justificationOf("attendance_record", absence)).isNull();
	}

	@Test
	void anonymousRequestsAreRefused() throws Exception {
		UUID absence = absent(world.studentA, MONDAY, world.period1);
		justify(Actor.ANONYMOUS, Map.of("reason", "FAMILY", "absenceIds", List.of(absence)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aDmOutsideTheStudentsBlockGets403() throws Exception { // BR-11, NFR-8
		UUID absence = absent(world.studentA, MONDAY, world.period1);
		UUID late = late(world.studentA, MONDAY);

		justify(DM_2, Map.of("reason", "FAMILY", "absenceIds", List.of(absence))).andExpect(status().isForbidden());
		justify(DM_2, Map.of("reason", "FAMILY", "latenessIds", List.of(late))).andExpect(status().isForbidden());

		assertThat(justificationOf("attendance_record", absence)).isNull();
		assertThat(justificationOf("lateness_record", late)).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM justification", Integer.class)).isZero();
	}

	@Test
	void oneRecordOutOfScopeFailsTheWholeRequest() throws Exception {
		UUID mine = absent(world.studentA, MONDAY, world.period1);
		UUID other = late(studentB, MONDAY);

		justify(DM_1, Map.of("reason", "FAMILY", "absenceIds", List.of(mine), "latenessIds", List.of(other)))
			.andExpect(status().isForbidden());

		assertThat(justificationOf("attendance_record", mine)).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'JUSTIFY'", Integer.class)).isZero();
	}

	@Test
	void theDmOfTheOtherBlockJustifiesTheirOwnStudents() throws Exception {
		UUID late = late(studentB, MONDAY);
		justify(DM_2, Map.of("reason", "FAMILY", "latenessIds", List.of(late))).andExpect(status().isCreated());
		assertThat(justificationOf("lateness_record", late)).isNotNull();
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
				world.user(Actor.ADMIN_Y), Timestamp.from(NOW));
		UUID theirs = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, 'ABSENT')""", theirs, world.schoolY, session, studentY);

		justify(DM_1, Map.of("reason", "FAMILY", "absenceIds", List.of(theirs))).andExpect(status().isNotFound());
		justify(DM_1, Map.of("reason", "FAMILY", "latenessIds", List.of(UUID.randomUUID())))
			.andExpect(status().isNotFound());
		assertThat(justificationOf("attendance_record", theirs)).isNull();
	}

	@Test
	void aCancelledRecordHasNothingToJustify() throws Exception { // BR-15
		UUID live = absent(world.studentA, MONDAY, world.period1);
		UUID cancelled = absent(world.studentA, MONDAY, period2);
		jdbc.update("""
				UPDATE attendance_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
				WHERE id = ?""", world.user(DM_1), cancelled);

		justify(DM_1, Map.of("reason", "FAMILY", "absenceIds", List.of(live, cancelled))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("justification.record_cancelled"));

		assertThat(justificationOf("attendance_record", live)).isNull();
	}

	@Test
	void aStudentWhoWasPresentHasNothingToJustify() throws Exception {
		UUID present = record(world.studentA, MONDAY, world.period1, "PRESENT");

		justify(DM_1, Map.of("reason", "FAMILY", "absenceIds", List.of(present))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("justification.not_an_absence"));
	}

	@Test
	void anIllnessTakesNoNote() throws Exception { // NFR-10: no health detail beyond "illness"
		UUID absence = absent(world.studentA, MONDAY, world.period1);

		justify(DM_1, Map.of("reason", "ILLNESS", "note", "Paludisme", "absenceIds", List.of(absence)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("justification.note_not_allowed"))
			.andExpect(jsonPath("$.field").value("note"));
		// an empty note is no note
		justify(DM_1, Map.of("reason", "ILLNESS", "note", "  ", "absenceIds", List.of(absence)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.note").doesNotExist());
	}

	@Test
	void theRequestIsValidated() throws Exception {
		UUID absence = absent(world.studentA, MONDAY, world.period1);

		justify(DM_1, Map.of("absenceIds", List.of(absence))).andExpect(status().isBadRequest());
		justify(DM_1, Map.of("reason", "HOLIDAY", "absenceIds", List.of(absence))).andExpect(status().isBadRequest());
		justify(DM_1, Map.of("reason", "FAMILY")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("justification.no_records"));
		justify(DM_1, Map.of("reason", "FAMILY", "note", "x".repeat(301), "absenceIds", List.of(absence)))
			.andExpect(status().isBadRequest());
		List<UUID> many = new ArrayList<>();
		for (int i = 0; i < 101; i++) {
			many.add(UUID.randomUUID());
		}
		justify(DM_1, Map.of("reason", "FAMILY", "absenceIds", many)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("justification.too_many_records"));
		assertThat(justificationOf("attendance_record", absence)).isNull();
	}

	@Test
	void theSameIdTwiceCountsOnce() throws Exception {
		UUID absence = absent(world.studentA, MONDAY, world.period1);
		justify(DM_1, Map.of("reason", "FAMILY", "absenceIds", List.of(absence, absence))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.absenceIds.length()").value(1));
	}

	private ResultActions justify(Actor actor, Map<String, Object> body) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/justifications").contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body));
		if (actor != Actor.ANONYMOUS) {
			request.header("Authorization", "Bearer " + world.token(actor));
		}
		return mockMvc.perform(request);
	}

	private JsonNode body(ResultActions result) throws Exception {
		return json.readTree(result.andReturn().getResponse().getContentAsString());
	}

	private UUID justificationOf(String table, UUID record) {
		return jdbc.queryForObject("SELECT justification_id FROM " + table + " WHERE id = ?", UUID.class, record);
	}

	private Map<String, Object> audit(String action, String entity, String entityId) {
		return new HashMap<>(jdbc.queryForMap("""
				SELECT actor_id, before::text AS before, after::text AS after FROM audit_log
				WHERE action = ? AND entity = ? AND entity_id = ?""", action, entity, entityId));
	}

	private UUID absent(UUID student, String date, UUID period) {
		return record(student, date, period, "ABSENT");
	}

	/** A row in the session of the student's class for that date and period, made if missing. */
	private UUID record(UUID student, String date, UUID period, String status) {
		UUID classId = jdbc.queryForObject("SELECT class_id FROM student WHERE id = ?", UUID.class, student);
		UUID session = jdbc.queryForList("""
				SELECT id FROM attendance_session WHERE class_id = ? AND date = ? AND period_id = ?""", UUID.class, classId,
				LocalDate.parse(date), period).stream().findFirst().orElseGet(() -> {
					UUID id = UUID.randomUUID();
					jdbc.update("""
							INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
							VALUES (?, ?, ?, ?, ?, ?, ?)""", id, world.schoolX, classId, LocalDate.parse(date), period,
							world.user(TEACHER_A), Timestamp.from(NOW.minusSeconds(86_400)));
					return id;
				});
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, ?)""", id, world.schoolX, session, student, status);
		return id;
	}

	private UUID late(UUID student, String date) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, '07:48', ?, ?)""", id, world.schoolX, student, LocalDate.parse(date),
				world.user(ASSISTANT_DM_1), Timestamp.from(NOW));
		return id;
	}

}
