package com.schoollink.reporting.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Function;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The parent API (task 2.10, UC-P2, UC-P3, BR-8, BR-9, BR-15, BR-24, D17, NFR-8). PARENT_A is the
 * guardian of student A ("Élève", 6e A) and of Awa (5e B); Brice (5e B) belongs to another family.
 * Monday 2 November 2026, 08:40 in Douala. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class ParentApiTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	TestClock clock;

	TestWorld world;

	UUID guardianA;

	/** PARENT_A's second child, in 5e B. */
	UUID awa;

	/** Another family's child. */
	UUID brice;

	UUID otherGuardian;

	UUID period2;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		clock.set(NOW);
		guardianA = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class, world.user(PARENT_A));
		awa = student(world.classB, "TW-0102", "Awa");
		link(awa, guardianA);
		brice = student(world.classB, "TW-0103", "Brice");
		otherGuardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone) VALUES (?, 'Autre Famille', '+237690000401') RETURNING id""",
				UUID.class, world.schoolX);
		link(brice, otherGuardian);
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, 2, '08:25', '09:20') RETURNING id""",
				UUID.class, world.schoolX);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (NFR-8, D17)

	@TestFactory
	Stream<DynamicTest> onlyAParentListsTheirChildren() {
		return parentOnly("GET /me/children", w -> get("/api/v1/me/children"), 200);
	}

	@TestFactory
	Stream<DynamicTest> onlyAParentReadsTheirChildsHistory() {
		return parentOnly("GET /children/{own}/history", w -> get("/api/v1/children/{id}/history", w.studentA), 200);
	}

	@TestFactory
	Stream<DynamicTest> aParentCannotReadAnotherFamilysChild() { // plan check
		return parentOnly("GET /children/{other}/history", w -> get("/api/v1/children/{id}/history", brice), 404);
	}

	@TestFactory
	Stream<DynamicTest> onlyAParentReadsTheirInbox() {
		return parentOnly("GET /me/notifications", w -> get("/api/v1/me/notifications"), 200);
	}

	@TestFactory
	Stream<DynamicTest> onlyAParentOpensTheirNotification() {
		UUID row = outbox(guardianA, world.studentA, "ABSENCE", "PUSH", "SENT", NOW);
		return parentOnly("POST /notifications/{own}/opened", w -> post("/api/v1/notifications/{id}/opened", row), 204);
	}

	@TestFactory
	Stream<DynamicTest> aParentCannotOpenAnotherFamilysNotification() {
		UUID row = outbox(otherGuardian, brice, "ABSENCE", "SMS", "HELD", NOW);
		return parentOnly("POST /notifications/{other}/opened", w -> post("/api/v1/notifications/{id}/opened", row), 404);
	}

	// Children (UC-P3)

	@Test
	void aParentSeesTheirChildrenAndTheirClasses() throws Exception {
		perform(get("/api/v1/me/children")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(awa.toString()))
			.andExpect(jsonPath("$[0].className").value("5e B"))
			.andExpect(jsonPath("$[1].id").value(world.studentA.toString()))
			.andExpect(jsonPath("$[1].firstName").value("Élève"))
			.andExpect(jsonPath("$[1].className").value("6e A"))
			.andExpect(jsonPath("$[1].birthYear").doesNotExist());
	}

	// History (UC-P3)

	@Test
	void theHistoryShowsAbsencesAndLatenessDayByDay() throws Exception { // BR-8, BR-9, BR-15
		UUID justification = jdbc.queryForObject("""
				INSERT INTO justification (school_id, reason, recorded_by) VALUES (?, 'ILLNESS', ?) RETURNING id""",
				UUID.class, world.schoolX, world.user(DM_1));
		absent(world.studentA, "2026-11-02", world.period1, null, false);
		absent(world.studentA, "2026-11-02", period2, null, false);
		absent(world.studentA, "2026-10-30", world.period1, justification, false);
		absent(world.studentA, "2026-10-29", world.period1, null, true); // cancelled (BR-15)
		late(world.studentA, "2026-11-02", "08:30", false);
		late(world.studentA, "2026-10-28", "07:45", false);
		late(world.studentA, "2026-10-27", "07:50", true); // cancelled
		absent(brice, "2026-11-02", world.period1, null, false); // another family

		perform(get("/api/v1/children/{id}/history", world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Élève"))
			.andExpect(jsonPath("$.from").value("2026-08-04"))
			.andExpect(jsonPath("$.to").value("2026-11-02"))
			.andExpect(jsonPath("$.totals.absentPeriods").value(3))
			.andExpect(jsonPath("$.totals.unjustifiedAbsentPeriods").value(2))
			.andExpect(jsonPath("$.totals.lateness").value(2))
			.andExpect(jsonPath("$.totals.unjustifiedLateness").value(2))
			.andExpect(jsonPath("$.days.length()").value(3))
			// absent in periods 1 and 2 and late at the gate: both shown, neither overrides the other
			.andExpect(jsonPath("$.days[0].date").value("2026-11-02"))
			.andExpect(jsonPath("$.days[0].absences.length()").value(2))
			.andExpect(jsonPath("$.days[0].absences[0].periodNumber").value(1))
			.andExpect(jsonPath("$.days[0].absences[0].startTime").value("07:30:00"))
			.andExpect(jsonPath("$.days[0].absences[1].periodNumber").value(2))
			.andExpect(jsonPath("$.days[0].lateness.arrivalTime").value("08:30:00"))
			.andExpect(jsonPath("$.days[1].date").value("2026-10-30"))
			.andExpect(jsonPath("$.days[1].absences[0].justified").value(true))
			.andExpect(jsonPath("$.days[1].lateness").isEmpty())
			.andExpect(jsonPath("$.days[2].date").value("2026-10-28"))
			.andExpect(jsonPath("$.days[2].absences.length()").value(0))
			.andExpect(jsonPath("$.days[2].lateness.arrivalTime").value("07:45:00"));

		perform(get("/api/v1/children/{id}/history", world.studentA).param("from", "2026-10-31").param("to", "2026-11-02"))
			.andExpect(jsonPath("$.days.length()").value(1))
			.andExpect(jsonPath("$.totals.absentPeriods").value(2));
		perform(get("/api/v1/children/{id}/history", awa)).andExpect(status().isOk())
			.andExpect(jsonPath("$.days.length()").value(0));
	}

	@Test
	void theHistoryRangeIsChecked() throws Exception {
		perform(get("/api/v1/children/{id}/history", world.studentA).param("from", "2026-11-03").param("to", "2026-11-02"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("history.range_invalid"));
		perform(get("/api/v1/children/{id}/history", world.studentA).param("from", "2025-01-01").param("to", "2026-11-02"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("history.range_invalid"));
		perform(get("/api/v1/children/{id}/history", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	// Inbox (UC-P2)

	@Test
	void theInboxShowsEachEventOnceInTheParentsLanguage() throws Exception {
		UUID absence = outbox(guardianA, world.studentA, "ABSENCE", "PUSH", "SENT", NOW.minusSeconds(600));
		UUID summonsEvent = UUID.randomUUID();
		UUID summonsPush = outbox(guardianA, awa, "SUMMONS", "PUSH", "SENT", NOW, summonsEvent);
		outbox(guardianA, awa, "SUMMONS", "SMS", "SENT", NOW, summonsEvent); // BR-23: same event by SMS
		outbox(guardianA, null, "INVITATION", "SMS", "SENT", NOW.minusSeconds(86_400)); // never shown (D13)
		outbox(otherGuardian, brice, "ABSENCE", "SMS", "HELD", NOW); // another family

		perform(get("/api/v1/me/notifications")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(summonsPush.toString()))
			.andExpect(jsonPath("$[0].eventType").value("SUMMONS"))
			.andExpect(jsonPath("$[0].studentId").value(awa.toString()))
			.andExpect(jsonPath("$[0].title").value("Convocation : Awa"))
			.andExpect(jsonPath("$[1].id").value(absence.toString()))
			.andExpect(jsonPath("$[1].title").value("Absence : Awa"))
			.andExpect(jsonPath("$[1].body").value("Absence signalée pour Awa en période 1 (07:30), le 02/11."))
			.andExpect(jsonPath("$[1].openedAt").isEmpty())
			.andExpect(jsonPath("$[0].payload").doesNotExist());

		// the app follows the parent's language (NFR-12)
		jdbc.update("UPDATE app_user SET language = 'EN' WHERE id = ?", world.user(PARENT_A));
		perform(get("/api/v1/me/notifications")).andExpect(jsonPath("$[1].title").value("Absence: Awa"));
	}

	@Test
	void theInboxPagesBackwards() throws Exception {
		UUID[] rows = new UUID[5];
		for (int i = 0; i < rows.length; i++) {
			rows[i] = outbox(guardianA, world.studentA, "ABSENCE", "PUSH", "SENT", NOW); // same time: one session
		}
		String first = body(perform(get("/api/v1/me/notifications").param("limit", "3")));
		String last = idAt(first, 2);

		String second = body(perform(get("/api/v1/me/notifications").param("limit", "3").param("before", last)));

		assertThat(first.split("\"id\"").length - 1).isEqualTo(3);
		assertThat(second.split("\"id\"").length - 1).isEqualTo(2);
		for (UUID row : rows) {
			assertThat(first.contains(row.toString()) ^ second.contains(row.toString())).as(row.toString()).isTrue();
		}
		perform(get("/api/v1/me/notifications").param("limit", "101")).andExpect(status().isBadRequest());
	}

	// Open receipt (BR-24)

	@Test
	void openingSetsOpenedAtOnce() throws Exception { // plan check
		UUID sent = outbox(guardianA, world.studentA, "ABSENCE", "PUSH", "SENT", NOW.minusSeconds(60));
		UUID queued = outbox(guardianA, world.studentA, "LATENESS", "PUSH", "QUEUED", NOW.minusSeconds(60));

		perform(post("/api/v1/notifications/{id}/opened", sent)).andExpect(status().isNoContent());
		clock.set(NOW.plusSeconds(300));
		perform(post("/api/v1/notifications/{id}/opened", sent)).andExpect(status().isNoContent());
		perform(post("/api/v1/notifications/{id}/opened", queued)).andExpect(status().isNoContent());

		assertThat(jdbc.queryForMap("SELECT status, opened_at FROM notification_outbox WHERE id = ?", sent))
			.containsEntry("status", "OPENED")
			.containsEntry("opened_at", Timestamp.from(NOW));
		// a push still in flight keeps its delivery status; the receipt is kept
		assertThat(jdbc.queryForMap("SELECT status, opened_at FROM notification_outbox WHERE id = ?", queued))
			.containsEntry("status", "QUEUED")
			.containsEntry("opened_at", Timestamp.from(NOW.plusSeconds(300)));
		perform(get("/api/v1/me/notifications")).andExpect(jsonPath("$[?(@.id == '" + sent + "')].openedAt")
			.value("2026-11-02T07:40:00Z"));
	}

	@Test
	void anInvitationOrAnUnknownNotificationCannotBeOpened() throws Exception {
		UUID invitation = outbox(guardianA, null, "INVITATION", "SMS", "SENT", NOW);

		perform(post("/api/v1/notifications/{id}/opened", invitation)).andExpect(status().isNotFound());
		perform(post("/api/v1/notifications/{id}/opened", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	private Stream<DynamicTest> parentOnly(String name,
			Function<TestWorld, MockHttpServletRequestBuilder> request, int parentStatus) {
		return PermissionMatrix.row(name, request::apply)
			.expect(parentStatus, PARENT_A)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(Actor.PARENT_A)));
	}

	private static String body(ResultActions result) throws Exception {
		return result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private static String idAt(String json, int index) {
		String[] parts = json.split("\"id\":\"");
		return parts[index + 1].substring(0, 36);
	}

	private UUID student(UUID classId, String matricule, String firstName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, 'Fictif', 'F', 2013) RETURNING id""", UUID.class, world.schoolX, classId, matricule,
				firstName);
	}

	private void link(UUID student, UUID guardian) {
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'MOTHER', false)""", world.schoolX, student, guardian);
	}

	/** An absence in its own session (any class teacher, the student's class). */
	private void absent(UUID student, String date, UUID period, UUID justification, boolean cancelled) {
		UUID classId = jdbc.queryForObject("SELECT class_id FROM student WHERE id = ?", UUID.class, student);
		UUID session = jdbc.queryForList("""
				SELECT id FROM attendance_session WHERE class_id = ? AND date = ? AND period_id = ?""", UUID.class, classId,
				LocalDate.parse(date), period).stream().findFirst().orElseGet(() -> {
					UUID id = UUID.randomUUID();
					jdbc.update("""
							INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
							VALUES (?, ?, ?, ?, ?, ?, ?)""", id, world.schoolX, classId, LocalDate.parse(date), period,
							world.user(TEACHER_A), Timestamp.from(NOW.minusSeconds(3600)));
					return id;
				});
		UUID record = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status, justification_id)
				VALUES (?, ?, ?, ?, 'ABSENT', ?)""", record, world.schoolX, session, student, justification);
		if (cancelled) {
			jdbc.update("""
					UPDATE attendance_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
					WHERE id = ?""", world.user(DM_1), record);
		}
	}

	private void late(UUID student, String date, String arrival, boolean cancelled) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, ?::time, ?, ?)""", id, world.schoolX, student, LocalDate.parse(date), arrival,
				world.user(ASSISTANT_DM_1), Timestamp.from(NOW));
		if (cancelled) {
			jdbc.update("""
					UPDATE lateness_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
					WHERE id = ?""", world.user(DM_1), id);
		}
	}

	private UUID outbox(UUID guardian, UUID student, String type, String channel, String status, Instant createdAt) {
		return outbox(guardian, student, type, channel, status, createdAt, UUID.randomUUID());
	}

	private UUID outbox(UUID guardian, UUID student, String type, String channel, String status, Instant createdAt,
			UUID eventId) {
		String payload = switch (type) {
			case "SUMMONS" -> "{\"firstName\":\"Awa\",\"meetingDate\":\"2026-11-05\",\"meetingTime\":\"10:00\"}";
			case "LATENESS" -> "{\"firstName\":\"Awa\",\"date\":\"2026-11-02\",\"arrivalTime\":\"08:12\"}";
			case "INVITATION" -> "{\"expiresAt\":\"2026-12-01T00:00:00Z\"}";
			default -> "{\"firstName\":\"Awa\",\"date\":\"2026-11-02\",\"periodNumber\":1,\"periodStart\":\"07:30\"}";
		};
		return jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				                                 payload, status, created_at, sent_at)
				VALUES (?, ?, ?, ?, ?, ?, 'FR', CAST(? AS jsonb), ?, ?, ?) RETURNING id""", UUID.class, world.schoolX,
				guardian, student, type, eventId, channel, payload, status, Timestamp.from(createdAt),
				"SENT".equals(status) ? Timestamp.from(createdAt) : null);
	}

}
