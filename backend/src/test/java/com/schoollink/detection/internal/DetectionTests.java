package com.schoollink.detection.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.detection.RuleType;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-16 to BR-19, task 3.4: the detection engine on the database, through the other modules' APIs.
 * Class 6e A has two periods every weekday; the term runs from 7 September to 18 December 2026.
 * Monday 2 November 2026. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class DetectionTests {

	static final Instant NOW = Instant.parse("2026-11-02T19:15:00Z");

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	static final LocalDate TUESDAY = LocalDate.parse("2026-11-03");

	static final Set<RuleType> ALL = EnumSet.allOf(RuleType.class);

	@Autowired
	Detection detection;

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

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, 2, '08:25', '09:20') RETURNING id""",
				UUID.class, world.schoolX);
		for (int weekday = 1; weekday <= 5; weekday++) {
			for (UUID period : List.of(world.period1, period2)) {
				jdbc.update("""
						INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
						VALUES (?, ?, ?, ?, ?, 'Maths') ON CONFLICT DO NOTHING""", world.schoolX, world.classA, weekday,
						period, world.user(TEACHER_A));
			}
		}
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aFullDayRaisesOneOpenAlertAndNothingElse() throws Exception { // D2, BR-17, BR-18
		absent(MONDAY, world.period1);
		absent(MONDAY, period2);
		int outbox = count("notification_outbox");

		assertThat(detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL)).isOne();

		Map<String, Object> alert = jdbc.queryForMap("""
				SELECT student_id, class_id, rule_type, threshold, as_of, evidence::text AS evidence, status, raised_at,
				       closed_at
				FROM risk_alert WHERE school_id = ?""", world.schoolX);
		assertThat(alert).containsEntry("student_id", world.studentA)
			.containsEntry("class_id", world.classA)
			.containsEntry("rule_type", "FULL_DAY_ABSENCE")
			.containsEntry("threshold", 1)
			.containsEntry("as_of", java.sql.Date.valueOf(MONDAY))
			.containsEntry("status", "OPEN")
			.containsEntry("raised_at", Timestamp.from(NOW))
			.containsEntry("closed_at", null);
		assertThat(json.readTree((String) alert.get("evidence")).get("dates").get(0).asString()).isEqualTo("2026-11-02");
		// NFR-11: raised by the system
		Map<String, Object> audit = jdbc.queryForMap("""
				SELECT actor_id, action, after::text AS after FROM audit_log WHERE entity = 'risk_alert' AND school_id = ?""",
				world.schoolX);
		assertThat(audit).containsEntry("actor_id", null).containsEntry("action", "CREATE");
		assertThat((String) audit.get("after")).contains("FULL_DAY_ABSENCE", world.studentA.toString());
		// BR-18: no summons, no message to the parent
		assertThat(count("notification_outbox")).isEqualTo(outbox);
	}

	@Test
	void anOpenAlertIsNeverRaisedTwice() { // BR-17
		absent(MONDAY, world.period1);
		absent(MONDAY, period2);

		detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL);
		assertThat(detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL)).isZero();
		assertThat(detection.evaluateSchool(world.schoolX, MONDAY)).isZero();

		assertThat(alerts("FULL_DAY_ABSENCE")).isOne();
	}

	@Test
	void halfADayRaisesNothing() { // D2
		absent(MONDAY, world.period1);

		assertThat(detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL)).isZero();
	}

	@Test
	void aCancelledAbsenceDoesNotCount() { // BR-15
		absent(MONDAY, world.period1);
		UUID cancelled = absent(MONDAY, period2);
		jdbc.update("""
				UPDATE attendance_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur de saisie'
				WHERE id = ?""", world.user(DM_1), cancelled);

		assertThat(detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL)).isZero();
	}

	@Test
	void theDmsSettingsDecide() { // D9: count and on/off from UC-D7
		jdbc.update("UPDATE threshold_rule SET count = 2 WHERE school_id = ? AND type = 'CONSECUTIVE_LATENESS'",
				world.schoolX);
		jdbc.update("UPDATE threshold_rule SET active = false WHERE school_id = ? AND type = 'FULL_DAY_ABSENCE'",
				world.schoolX);
		late(MONDAY);
		late(TUESDAY);
		absent(TUESDAY, world.period1);
		absent(TUESDAY, period2);

		assertThat(detection.evaluate(world.schoolX, world.studentA, TUESDAY, ALL)).isOne();

		assertThat(alerts("CONSECUTIVE_LATENESS")).isOne();
		assertThat(alerts("FULL_DAY_ABSENCE")).isZero();
		assertThat(jdbc.queryForObject("SELECT threshold FROM risk_alert WHERE school_id = ?", Integer.class,
				world.schoolX)).isEqualTo(2);
	}

	@Test
	void onlyTheGivenRulesRun() { // BR-17: a lateness runs the lateness rule only
		absent(MONDAY, world.period1);
		absent(MONDAY, period2);

		assertThat(detection.evaluate(world.schoolX, world.studentA, MONDAY, EnumSet.of(RuleType.CONSECUTIVE_LATENESS)))
			.isZero();
	}

	@Test
	void justifyingLaterDoesNotCloseTheAlert() throws Exception { // BR-19
		jdbc.update("UPDATE threshold_rule SET count = 2 WHERE school_id = ? AND type = 'UNJUSTIFIED_ABSENCES'",
				world.schoolX);
		UUID first = absent(MONDAY, world.period1);
		UUID second = absent(MONDAY, period2);
		detection.evaluate(world.schoolX, world.studentA, MONDAY, EnumSet.of(RuleType.UNJUSTIFIED_ABSENCES));

		mockMvc.perform(post("/api/v1/justifications").header("Authorization", "Bearer " + world.token(DM_1))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(Map.of("reason", "FAMILY", "absenceIds", List.of(first, second)))))
			.andExpect(status().isCreated());
		detection.evaluateSchool(world.schoolX, MONDAY);

		assertThat(jdbc.queryForObject("""
				SELECT status FROM risk_alert WHERE school_id = ? AND rule_type = 'UNJUSTIFIED_ABSENCES'""", String.class,
				world.schoolX)).isEqualTo("OPEN");
	}

	@Test
	void aClosedAlertIsRaisedAgainOnlyForLaterDays() { // BR-17, D29
		absent(MONDAY, world.period1);
		absent(MONDAY, period2);
		detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL);
		jdbc.update("UPDATE risk_alert SET status = 'CLOSED', closed_at = now(), closed_by = ? WHERE school_id = ?",
				world.user(DM_1), world.schoolX);

		assertThat(detection.evaluate(world.schoolX, world.studentA, MONDAY, ALL)).isZero();

		absent(TUESDAY, world.period1);
		absent(TUESDAY, period2);
		assertThat(detection.evaluate(world.schoolX, world.studentA, TUESDAY, ALL)).isOne();
		assertThat(jdbc.queryForList("""
				SELECT as_of::text FROM risk_alert WHERE school_id = ? AND status = 'OPEN'""", String.class,
				world.schoolX)).containsExactly("2026-11-03");
	}

	@Test
	void theNightlyRunCoversEveryStudentOfTheSchool() { // BR-17
		jdbc.update("UPDATE threshold_rule SET count = 2 WHERE school_id = ? AND type = 'CONSECUTIVE_LATENESS'",
				world.schoolX);
		UUID studentB = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0002', 'Autre', 'Fictif', 'M', 2011) RETURNING id""", UUID.class, world.schoolX,
				world.classB);
		absent(MONDAY, world.period1);
		absent(MONDAY, period2);
		late(studentB, LocalDate.parse("2026-10-30"));
		late(studentB, MONDAY);

		assertThat(detection.evaluateSchool(world.schoolX, MONDAY)).isEqualTo(2);
		assertThat(detection.evaluateSchool(world.schoolY, MONDAY)).isZero();
		// between terms nothing runs
		assertThat(detection.evaluateSchool(world.schoolX, LocalDate.parse("2026-12-21"))).isZero();
		assertThat(jdbc.queryForList("SELECT rule_type FROM risk_alert WHERE school_id = ? ORDER BY rule_type",
				String.class, world.schoolX)).containsExactly("CONSECUTIVE_LATENESS", "FULL_DAY_ABSENCE");
	}

	@Test
	void anUnknownStudentIsIgnored() { // NFR-8: a student of another school is not found
		assertThat(detection.evaluate(world.schoolY, world.studentA, MONDAY, ALL)).isZero();
		assertThat(detection.evaluate(world.schoolX, UUID.randomUUID(), MONDAY, ALL)).isZero();
	}

	private int alerts(String type) {
		return jdbc.queryForObject("SELECT count(*) FROM risk_alert WHERE school_id = ? AND rule_type = ?",
				Integer.class, world.schoolX, type);
	}

	private int count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE school_id = ?", Integer.class,
				world.schoolX);
	}

	/** Student A absent in that period, in a confirmed session. */
	private UUID absent(LocalDate date, UUID period) {
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", session, world.schoolX, world.classA, date, period,
				world.user(TEACHER_A), Timestamp.from(NOW));
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, 'ABSENT')""", id, world.schoolX, session, world.studentA);
		return id;
	}

	private void late(LocalDate date) {
		late(world.studentA, date);
	}

	private void late(UUID student, LocalDate date) {
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, '07:48', ?, ?)""", UUID.randomUUID(), world.schoolX, student, date,
				world.user(ASSISTANT_DM_1), Timestamp.from(NOW));
	}

}
