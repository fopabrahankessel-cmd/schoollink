package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.attendance.AttendanceConfirmed;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ADMIN_X;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The notification outbox (task 2.4, BR-20 to BR-22, D2, D7, D13, NFR-6): rows written by the event
 * listeners, in the event's transaction. TEACHER_A records 6e A on Monday 2 November 2026, period 1.
 * Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class OutboxTests {

	static final String MONDAY = "2026-11-02";

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
	ApplicationEventPublisher events;

	TestWorld world;

	/** Two students of 6e A, each with two guardians who have the app. */
	UUID awa;

	UUID paul;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		awa = student("TW-0201", "Awa");
		paul = student("TW-0202", "Paul");
		clock.set(Instant.parse("2026-11-02T07:40:00Z"));
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void twoAbsenteesWithTwoGuardiansEachGiveFourPushRows() throws Exception { // BR-20, BR-21, D7
		guardian(awa, "+237690000101", "FR", true);
		guardian(awa, "+237690000102", "EN", true);
		guardian(paul, "+237690000103", "FR", true);
		guardian(paul, "+237690000104", "FR", true);

		saveSession(List.of(awa, paul)).andExpect(status().isCreated());

		var rows = jdbc.queryForList("""
				SELECT o.channel, o.status, o.event_type, o.language, o.student_id, o.attempts,
				       o.event_id = r.id AS event_is_the_absence
				FROM notification_outbox o JOIN attendance_record r ON r.student_id = o.student_id
				WHERE o.school_id = ?""", world.schoolX);
		assertThat(rows).hasSize(4).allSatisfy(row -> {
			assertThat(row.get("channel")).isEqualTo("PUSH");
			assertThat(row.get("status")).isEqualTo("QUEUED");
			assertThat(row.get("event_type")).isEqualTo("ABSENCE");
			assertThat(((Number) row.get("attempts")).intValue()).isZero();
			assertThat(row.get("event_is_the_absence")).isEqualTo(true);
		});
		// BR-20: each in the guardian's own language
		assertThat(rows).extracting(row -> row.get("language")).containsExactlyInAnyOrder("FR", "EN", "FR", "FR");
	}

	@Test
	void guardiansWithoutTheAppGetAnSmsHeldForTheDigest() throws Exception { // D7, BR-22
		UUID withApp = guardian(awa, "+237690000101", "FR", true);
		UUID withoutApp = guardian(awa, "+237690000102", "FR", false);

		saveSession(List.of(awa)).andExpect(status().isCreated());

		assertThat(outboxOf(withApp)).containsExactly("PUSH QUEUED");
		assertThat(outboxOf(withoutApp)).containsExactly("SMS HELD");
	}

	@Test
	void thePayloadHoldsWhatTheMessageNeedsButNoPhone() throws Exception { // task 2.5, NFR-9
		guardian(awa, "+237690000101", "FR", true);

		saveSession(List.of(awa)).andExpect(status().isCreated());

		JsonNode payload = json.readTree(
				jdbc.queryForObject("SELECT payload::text FROM notification_outbox WHERE student_id = ?", String.class, awa));
		assertThat(payload.get("firstName").asString()).isEqualTo("Awa");
		assertThat(payload.get("date").asString()).isEqualTo(MONDAY);
		assertThat(payload.get("periodNumber").asInt()).isEqualTo(1);
		assertThat(payload.get("periodStart").asString()).isEqualTo("07:30");
		assertThat(payload.toString()).doesNotContain("690000101");
	}

	@Test
	void presentStudentsAndSessionsWithoutAbsenteesNotifyNobody() throws Exception { // D2, BR-5
		guardian(awa, "+237690000101", "FR", true);
		guardian(paul, "+237690000102", "FR", false);

		saveSession(List.of()).andExpect(status().isCreated());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isZero();
	}

	@Test
	void aReplayedSessionAddsNoRow() throws Exception { // BR-6, BR-20
		guardian(awa, "+237690000101", "FR", true);
		UUID id = UUID.randomUUID();
		saveSession(id, List.of(awa)).andExpect(status().isCreated());

		saveSession(id, List.of(awa)).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isOne();
	}

	@Test
	void theSameEventWrittenTwiceKeepsOneRowPerGuardianAndChannel() { // BR-20
		guardian(awa, "+237690000101", "FR", true);
		AttendanceConfirmed event = event(List.of(new AttendanceConfirmed.Absence(UUID.randomUUID(), awa)));

		events.publishEvent(event);
		events.publishEvent(event);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isOne();
	}

	@Test
	void aRolledBackSessionLeavesNoRow() throws Exception { // NFR-6: same transaction as the event
		guardian(awa, "+237690000101", "FR", true);
		guardian(paul, "+237690000102", "FR", false);
		UUID id = UUID.randomUUID();
		// the request joins the test transaction; rolling back to the savepoint undoes all it wrote
		jdbc.execute("SAVEPOINT before_session");
		saveSession(id, List.of(awa, paul)).andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isEqualTo(2);

		jdbc.execute("ROLLBACK TO SAVEPOINT before_session");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_session WHERE id = ?", Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class)).isZero();
	}

	@Test
	void anInvitationCodeGoesBySmsAtOnce() throws Exception { // D13, UC-A4
		UUID guardian = guardian(awa, "+237690000101", "EN", false);

		String code = json.readTree(mockMvc
			.perform(post("/api/v1/guardians/{id}/invitations", guardian).header("Authorization",
					"Bearer " + world.token(ADMIN_X)))
			.andExpect(status().is2xxSuccessful())
			.andReturn()
			.getResponse()
			.getContentAsString()).get("code").asString();

		var row = jdbc.queryForMap("""
				SELECT o.channel, o.status, o.language, o.student_id, o.payload->>'code' AS code, o.event_id = c.id AS is_code
				FROM notification_outbox o JOIN invitation_code c ON c.guardian_id = o.guardian_id
				WHERE o.guardian_id = ? AND o.event_type = 'INVITATION'""", guardian);
		assertThat(row.get("channel")).isEqualTo("SMS");
		assertThat(row.get("status")).isEqualTo("QUEUED");
		assertThat(row.get("language")).isEqualTo("EN");
		assertThat(row.get("student_id")).isNull();
		assertThat(row.get("code")).isEqualTo(code);
		assertThat(row.get("is_code")).isEqualTo(true);
	}

	@Test
	void invitingAClassQueuesOneSmsPerGuardianWithoutAnAccount() throws Exception { // D7, D13: task 1.19's check
		guardian(awa, "+237690000101", "FR", false);
		guardian(paul, "+237690000102", "FR", false);
		guardian(paul, "+237690000103", "FR", true);

		mockMvc.perform(post("/api/v1/classes/{id}/invitations", world.classA).header("Authorization",
				"Bearer " + world.token(ADMIN_X))).andExpect(status().is2xxSuccessful());

		// TestWorld's PARENT_A has an account too: only the two guardians without one are invited
		assertThat(jdbc.queryForList(
				"SELECT channel || ' ' || status FROM notification_outbox WHERE event_type = 'INVITATION'", String.class))
			.containsExactly("SMS QUEUED", "SMS QUEUED");
	}

	private AttendanceConfirmed event(List<AttendanceConfirmed.Absence> absences) {
		return new AttendanceConfirmed(UUID.randomUUID(), world.schoolX, world.classA, LocalDate.parse(MONDAY),
				world.period1, world.user(TEACHER_A), Instant.parse("2026-11-02T07:20:00Z"), absences);
	}

	private ResultActions saveSession(List<UUID> absent) throws Exception {
		return saveSession(UUID.randomUUID(), absent);
	}

	private ResultActions saveSession(UUID id, List<UUID> absent) throws Exception {
		String body = json.writeValueAsString(Map.of("classId", world.classA, "date", MONDAY, "periodId", world.period1,
				"confirmedAt", "2026-11-02T07:20:00Z", "absentStudentIds", absent));
		return mockMvc.perform(put("/api/v1/sessions/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.header("Authorization", "Bearer " + world.token(TEACHER_A)));
	}

	/** "PUSH QUEUED" for each of the guardian's rows. */
	private List<String> outboxOf(UUID guardian) {
		return jdbc.queryForList("SELECT channel || ' ' || status FROM notification_outbox WHERE guardian_id = ?",
				String.class, guardian);
	}

	private UUID student(String matricule, String firstName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, 'Fictif', 'F', 2013) RETURNING id""", UUID.class, world.schoolX, world.classA,
				matricule, firstName);
	}

	/** A guardian of the student; {@code withApp}: with an activated app account (D7). */
	private UUID guardian(UUID student, String phone, String language, boolean withApp) {
		UUID account = withApp ? jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, language)
				VALUES (?, 'Parent', 'Fictif', ?, ?) RETURNING id""", UUID.class, world.schoolX, phone, language) : null;
		UUID guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, user_id, name, phone, language) VALUES (?, ?, 'Parent Fictif', ?, ?) RETURNING id""",
				UUID.class, world.schoolX, account, phone, language);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'GUARDIAN', false)""", world.schoolX, student, guardian);
		return guardian;
	}

}
