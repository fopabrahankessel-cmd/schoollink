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
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboards and the DM's student history (task 3.9, UC-A6, UC-D8, BR-8, BR-9, BR-15, BR-24,
 * BR-25, D5, D17, D34, NFR-8). Class 6e A (block 1, DM_1) has student A ("Élève") and Ada; class
 * 5e B (block 2, DM_2) has Bob. Monday 2 November 2026, 08:40 in Douala. Generated data only.
 *
 * <pre>
 * Fri 30/10  6e A P1: Élève absent, Ada present       6e A P2: Élève absent justified, Ada absent cancelled
 *            lateness: Élève; Bob cancelled
 * Thu 29/10  lateness: Bob
 * Mon 02/11  5e B P1: Bob absent                     lateness: Ada justified
 * Fri 16/10  6e A P1: Élève absent (before the range)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class DashboardTests {

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

	UUID ada;

	UUID bob;

	UUID period2;

	UUID justification;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		clock.set(NOW);
		ada = student(world.classA, "TW-0201", "Ada");
		bob = student(world.classB, "TW-0202", "Bob");
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, 2, '08:25', '09:20') RETURNING id""",
				UUID.class, world.schoolX);
		justification = jdbc.queryForObject("""
				INSERT INTO justification (school_id, reason, recorded_by) VALUES (?, 'ILLNESS', ?) RETURNING id""",
				UUID.class, world.schoolX, world.user(DM_1));
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (NFR-8, D17)

	@TestFactory
	Stream<DynamicTest> onlyAnAdminReadsTheSchoolDashboard() {
		return PermissionMatrix.row("GET /dashboard/school", w -> get("/api/v1/dashboard/school"))
			.expect(200, ADMIN_X, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyADmReadsTheScopeDashboard() {
		return PermissionMatrix.row("GET /dashboard/scope", w -> get("/api/v1/dashboard/scope"))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> theDmOfTheClassOrTheAdminReadsAStudentsHistory() {
		return PermissionMatrix.row("GET /students/{A}/history", w -> get("/api/v1/students/{id}/history", w.studentA))
			.expect(200, ADMIN_X, DM_1)
			.expect(404, DM_2, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	// School dashboard (UC-A6)

	@Test
	void theSchoolDashboardMatchesTheFixture() throws Exception { // BR-8, BR-9, BR-15, BR-24, BR-25
		records();
		alerts();
		notifications();

		perform(ADMIN_X, get("/api/v1/dashboard/school").param("from", "2026-10-26").param("to", "2026-11-02"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2026-10-26"))
			.andExpect(jsonPath("$.to").value("2026-11-02"))
			// the cancelled absence is a student period, not an absence (BR-15)
			.andExpect(jsonPath("$.totals.sessions").value(3))
			.andExpect(jsonPath("$.totals.studentPeriods").value(5))
			.andExpect(jsonPath("$.totals.absentPeriods").value(3))
			.andExpect(jsonPath("$.totals.unjustifiedAbsentPeriods").value(2))
			.andExpect(jsonPath("$.totals.lateness").value(3))
			.andExpect(jsonPath("$.totals.unjustifiedLateness").value(2))
			// oldest first, only the days with a record
			.andExpect(jsonPath("$.days.length()").value(3))
			.andExpect(jsonPath("$.days[0].date").value("2026-10-29"))
			.andExpect(jsonPath("$.days[0].counts.sessions").value(0))
			.andExpect(jsonPath("$.days[0].counts.lateness").value(1))
			.andExpect(jsonPath("$.days[1].date").value("2026-10-30"))
			.andExpect(jsonPath("$.days[1].counts.sessions").value(2))
			.andExpect(jsonPath("$.days[1].counts.studentPeriods").value(4))
			.andExpect(jsonPath("$.days[1].counts.absentPeriods").value(2))
			.andExpect(jsonPath("$.days[1].counts.unjustifiedAbsentPeriods").value(1))
			.andExpect(jsonPath("$.days[1].counts.lateness").value(1))
			.andExpect(jsonPath("$.days[2].date").value("2026-11-02"))
			// absent in period 1 and a lateness the same day: both counted, neither overrides (BR-8, BR-9)
			.andExpect(jsonPath("$.days[2].counts.absentPeriods").value(1))
			.andExpect(jsonPath("$.days[2].counts.lateness").value(1))
			.andExpect(jsonPath("$.days[2].counts.unjustifiedLateness").value(0))
			.andExpect(jsonPath("$.classes.length()").value(2))
			.andExpect(jsonPath("$.classes[0].name").value("5e B"))
			.andExpect(jsonPath("$.classes[0].counts.absentPeriods").value(1))
			.andExpect(jsonPath("$.classes[0].counts.lateness").value(1))
			.andExpect(jsonPath("$.classes[1].name").value("6e A"))
			.andExpect(jsonPath("$.classes[1].counts.sessions").value(2))
			.andExpect(jsonPath("$.classes[1].counts.absentPeriods").value(2))
			.andExpect(jsonPath("$.classes[1].counts.lateness").value(2))
			.andExpect(jsonPath("$.alerts.length()").value(3))
			.andExpect(jsonPath("$.alerts[0].rule").value("FULL_DAY_ABSENCE"))
			.andExpect(jsonPath("$.alerts[0].open").value(1))
			.andExpect(jsonPath("$.alerts[0].raised").value(1))
			.andExpect(jsonPath("$.alerts[1].rule").value("CONSECUTIVE_LATENESS"))
			.andExpect(jsonPath("$.alerts[1].open").value(1))
			.andExpect(jsonPath("$.alerts[2].rule").value("UNJUSTIFIED_ABSENCES"))
			.andExpect(jsonPath("$.alerts[2].open").value(0))
			.andExpect(jsonPath("$.alerts[2].raised").value(0))
			.andExpect(jsonPath("$.alerts[2].closed").value(1))
			// BR-24: held, skipped and withdrawn rows are not sends
			.andExpect(jsonPath("$.delivery.push.sent").value(2))
			.andExpect(jsonPath("$.delivery.push.opened").value(1))
			.andExpect(jsonPath("$.delivery.push.failed").value(1))
			.andExpect(jsonPath("$.delivery.push.deliveryRate").value(0.75))
			.andExpect(jsonPath("$.delivery.sms.pending").value(1))
			.andExpect(jsonPath("$.delivery.sms.sent").value(1))
			.andExpect(jsonPath("$.delivery.sms.delivered").value(1))
			.andExpect(jsonPath("$.delivery.sms.failed").value(1))
			.andExpect(jsonPath("$.delivery.sms.deliveryRate").value(closeTo(2.0 / 3, 1e-9)))
			// BR-25: this month only, the October SMS is not counted
			.andExpect(jsonPath("$.sms.sentThisMonth").value(1))
			.andExpect(jsonPath("$.sms.monthlyCap").value(2000))
			.andExpect(jsonPath("$.sms.capReached").value(false));
	}

	@Test
	void anotherSchoolsAdminSeesNoneOfIt() throws Exception { // NFR-8
		records();
		alerts();
		notifications();

		perform(ADMIN_Y, get("/api/v1/dashboard/school").param("from", "2026-10-26").param("to", "2026-11-02"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totals.sessions").value(0))
			.andExpect(jsonPath("$.totals.lateness").value(0))
			.andExpect(jsonPath("$.days.length()").value(0))
			.andExpect(jsonPath("$.classes.length()").value(1))
			.andExpect(jsonPath("$.classes[0].name").value("Form 1 C"))
			.andExpect(jsonPath("$.alerts[0].open").value(0))
			.andExpect(jsonPath("$.delivery.push.sent").value(0))
			.andExpect(jsonPath("$.delivery.push.deliveryRate").isEmpty())
			.andExpect(jsonPath("$.sms.sentThisMonth").value(0));
	}

	@Test
	void theSmsCounterShowsTheCapReached() throws Exception { // BR-25
		notifications();
		jdbc.update("UPDATE school SET sms_monthly_cap = 1 WHERE id = ?", world.schoolX);

		perform(ADMIN_X, get("/api/v1/dashboard/school"))
			.andExpect(jsonPath("$.sms.sentThisMonth").value(1))
			.andExpect(jsonPath("$.sms.monthlyCap").value(1))
			.andExpect(jsonPath("$.sms.capReached").value(true));
	}

	@Test
	void theRangeDefaultsToTheLast30DaysAndIsChecked() throws Exception { // D34
		records();

		perform(ADMIN_X, get("/api/v1/dashboard/school")).andExpect(status().isOk())
			.andExpect(jsonPath("$.from").value("2026-10-03"))
			.andExpect(jsonPath("$.to").value("2026-11-02"))
			.andExpect(jsonPath("$.totals.sessions").value(4));
		perform(DM_1, get("/api/v1/dashboard/scope").param("from", "2026-11-03").param("to", "2026-11-02"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("history.range_invalid"));
		perform(ADMIN_X, get("/api/v1/dashboard/school").param("from", "2025-01-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("history.range_invalid"));
	}

	// Scope dashboard (UC-D8)

	@Test
	void theScopeDashboardHasTheDmsClassesOnly() throws Exception { // D5, NFR-8
		records();
		alerts();
		notifications();

		perform(DM_1, get("/api/v1/dashboard/scope").param("from", "2026-10-26").param("to", "2026-11-02"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totals.sessions").value(2))
			.andExpect(jsonPath("$.totals.studentPeriods").value(4))
			.andExpect(jsonPath("$.totals.absentPeriods").value(2))
			.andExpect(jsonPath("$.totals.unjustifiedAbsentPeriods").value(1))
			.andExpect(jsonPath("$.totals.lateness").value(2))
			.andExpect(jsonPath("$.totals.unjustifiedLateness").value(1))
			.andExpect(jsonPath("$.days.length()").value(2))
			.andExpect(jsonPath("$.days[0].date").value("2026-10-30"))
			.andExpect(jsonPath("$.classes.length()").value(1))
			.andExpect(jsonPath("$.classes[0].name").value("6e A"))
			.andExpect(jsonPath("$.alerts[0].open").value(1))
			.andExpect(jsonPath("$.alerts[1].open").value(0))
			.andExpect(jsonPath("$.alerts[2].closed").value(1))
			// delivery and SMS usage are the admin's (UC-A6)
			.andExpect(jsonPath("$.delivery").doesNotExist())
			.andExpect(jsonPath("$.sms").doesNotExist());

		perform(DM_2, get("/api/v1/dashboard/scope").param("from", "2026-10-26").param("to", "2026-11-02"))
			.andExpect(jsonPath("$.totals.sessions").value(1))
			.andExpect(jsonPath("$.totals.lateness").value(1))
			.andExpect(jsonPath("$.classes[0].name").value("5e B"))
			.andExpect(jsonPath("$.alerts[0].open").value(0))
			.andExpect(jsonPath("$.alerts[1].open").value(1));
	}

	// Student history (UC-D8)

	@Test
	void theDmSeesTheStudentsHistoryWithItsRecords() throws Exception { // BR-8, BR-9, BR-15
		records();
		UUID absence = jdbc.queryForObject("""
				SELECT r.id FROM attendance_record r JOIN attendance_session s ON s.id = r.session_id
				WHERE r.student_id = ? AND s.date = '2026-10-30' AND s.period_id = ?""", UUID.class, world.studentA,
				world.period1);

		perform(DM_1, get("/api/v1/students/{id}/history", world.studentA).param("from", "2026-10-26"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.matricule").value("TW-0001"))
			.andExpect(jsonPath("$.firstName").value("Élève"))
			.andExpect(jsonPath("$.lastName").value("Fictif"))
			.andExpect(jsonPath("$.classId").value(world.classA.toString()))
			.andExpect(jsonPath("$.className").value("6e A"))
			.andExpect(jsonPath("$.birthYear").doesNotExist())
			.andExpect(jsonPath("$.to").value("2026-11-02"))
			.andExpect(jsonPath("$.totals.absentPeriods").value(2))
			.andExpect(jsonPath("$.totals.unjustifiedAbsentPeriods").value(1))
			.andExpect(jsonPath("$.totals.lateness").value(1))
			.andExpect(jsonPath("$.days.length()").value(1))
			.andExpect(jsonPath("$.days[0].date").value("2026-10-30"))
			.andExpect(jsonPath("$.days[0].absences[0].recordId").value(absence.toString()))
			.andExpect(jsonPath("$.days[0].absences[1].justified").value(true))
			.andExpect(jsonPath("$.days[0].lateness.arrivalTime").value("07:50:00"));

		// the cancelled absence is left out (BR-15)
		perform(DM_1, get("/api/v1/students/{id}/history", ada).param("from", "2026-10-26"))
			.andExpect(jsonPath("$.totals.absentPeriods").value(0))
			.andExpect(jsonPath("$.totals.lateness").value(1))
			.andExpect(jsonPath("$.days[0].lateness.justified").value(true));
		perform(DM_1, get("/api/v1/students/{id}/history", bob)).andExpect(status().isNotFound());
		perform(DM_1, get("/api/v1/students/{id}/history", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	// Fixture

	private void records() {
		UUID p1 = session(world.classA, "2026-10-30", world.period1);
		record(p1, world.studentA, "ABSENT", null, false);
		record(p1, ada, "PRESENT", null, false);
		UUID p2 = session(world.classA, "2026-10-30", period2);
		record(p2, world.studentA, "ABSENT", justification, false);
		record(p2, ada, "ABSENT", null, true);
		UUID b = session(world.classB, "2026-11-02", world.period1);
		record(b, bob, "ABSENT", null, false);
		UUID before = session(world.classA, "2026-10-16", world.period1);
		record(before, world.studentA, "ABSENT", null, false);

		late(world.studentA, "2026-10-30", "07:50", null, false);
		late(bob, "2026-10-30", "07:55", null, true);
		late(bob, "2026-10-29", "07:40", null, false);
		late(ada, "2026-11-02", "08:30", justification, false);
	}

	private void alerts() {
		alert(world.studentA, world.classA, "FULL_DAY_ABSENCE", Instant.parse("2026-10-30T15:00:00Z"), null);
		alert(world.studentA, world.classA, "UNJUSTIFIED_ABSENCES", Instant.parse("2026-10-20T15:00:00Z"),
				Instant.parse("2026-10-28T09:00:00Z"));
		alert(bob, world.classB, "CONSECUTIVE_LATENESS", Instant.parse("2026-11-02T07:00:00Z"), null);
	}

	private void notifications() {
		UUID guardian = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class,
				world.user(PARENT_A));
		Instant oct30 = Instant.parse("2026-10-30T12:00:00Z");
		outbox(world.schoolX, guardian, "PUSH", "SENT", NOW, NOW);
		outbox(world.schoolX, guardian, "PUSH", "SENT", oct30, oct30);
		outbox(world.schoolX, guardian, "PUSH", "OPENED", oct30, oct30);
		outbox(world.schoolX, guardian, "PUSH", "FAILED", oct30, null);
		outbox(world.schoolX, guardian, "SMS", "DELIVERED", NOW, NOW);
		outbox(world.schoolX, guardian, "SMS", "SENT", oct30, oct30); // October: not this month's counter
		outbox(world.schoolX, guardian, "SMS", "FAILED", oct30, null);
		outbox(world.schoolX, guardian, "SMS", "QUEUED", NOW, null);
		outbox(world.schoolX, guardian, "SMS", "HELD", NOW, null);
		outbox(world.schoolX, guardian, "SMS", "SKIPPED", oct30, null);
		outbox(world.schoolX, guardian, "PUSH", "WITHDRAWN", oct30, null);
		Instant before = Instant.parse("2026-10-20T12:00:00Z");
		outbox(world.schoolX, guardian, "PUSH", "FAILED", before, null); // before the range
	}

	private UUID student(UUID classId, String matricule, String firstName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, 'Fictif', 'F', 2013) RETURNING id""", UUID.class, world.schoolX, classId, matricule,
				firstName);
	}

	private UUID session(UUID classId, String date, UUID period) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", id, world.schoolX, classId, LocalDate.parse(date), period,
				world.user(TEACHER_A), Timestamp.from(NOW.minusSeconds(3600)));
		return id;
	}

	private void record(UUID session, UUID student, String status, UUID justificationId, boolean cancelled) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status, justification_id)
				VALUES (?, ?, ?, ?, ?, ?)""", id, world.schoolX, session, student, status, justificationId);
		if (cancelled) {
			jdbc.update("""
					UPDATE attendance_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
					WHERE id = ?""", world.user(DM_1), id);
		}
	}

	private void late(UUID student, String date, String arrival, UUID justificationId, boolean cancelled) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at,
				                             justification_id)
				VALUES (?, ?, ?, ?, ?::time, ?, ?, ?)""", id, world.schoolX, student, LocalDate.parse(date), arrival,
				world.user(ASSISTANT_DM_1), Timestamp.from(NOW), justificationId);
		if (cancelled) {
			jdbc.update("""
					UPDATE lateness_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
					WHERE id = ?""", world.user(DM_1), id);
		}
	}

	private void alert(UUID student, UUID classId, String rule, Instant raisedAt, Instant closedAt) {
		jdbc.update("""
				INSERT INTO risk_alert (school_id, student_id, class_id, rule_type, threshold, as_of, evidence, status,
				                        raised_at, closed_at, closed_by, decision)
				VALUES (?, ?, ?, ?, 1, ?, '{}'::jsonb, ?, ?, ?, ?, ?)""", world.schoolX, student, classId, rule,
				LocalDate.ofInstant(raisedAt, java.time.ZoneOffset.UTC), closedAt == null ? "OPEN" : "CLOSED",
				Timestamp.from(raisedAt), closedAt == null ? null : Timestamp.from(closedAt),
				closedAt == null ? null : world.user(DM_1), closedAt == null ? null : "SUMMON");
	}

	private void outbox(UUID school, UUID guardian, String channel, String status, Instant createdAt, Instant sentAt) {
		jdbc.update("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				                                 payload, status, created_at, sent_at)
				VALUES (?, ?, ?, 'ABSENCE', ?, ?, 'FR', '{}'::jsonb, ?, ?, ?)""", school, guardian, world.studentA,
				UUID.randomUUID(), channel, status, Timestamp.from(createdAt),
				sentAt == null ? null : Timestamp.from(sentAt));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

}
