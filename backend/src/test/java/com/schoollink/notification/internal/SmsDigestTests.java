package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.SmsProvider.SmsMessage;
import com.schoollink.school.SchoolTimes.Times;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.PARENT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SMS digest and the monthly cap (task 3.8; BR-22, BR-25, D6, D12, D33, NFR-5), with a set
 * clock and a scripted SMS provider. School X is in Africa/Douala (UTC+1) with the default digest
 * times, 13:00 and 17:00. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, OutboxDispatcherTests.Providers.class })
@Transactional
class SmsDigestTests {

	static final String MONDAY = "2026-11-02";

	/** 13:00 and 17:00 in Douala. */
	static final Instant FIRST = Instant.parse("2026-11-02T12:00:00Z");

	static final Instant SECOND = Instant.parse("2026-11-02T16:00:00Z");

	@Autowired
	SmsDigest digest;

	@Autowired
	OutboxDispatcher dispatcher;

	@Autowired
	OutboxDispatcherTests.ScriptedSms sms;

	@Autowired
	OutboxDispatcherTests.ScriptedPush push;

	@Autowired
	Corrections corrections;

	@Autowired
	SmsUsage usage;

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

	/** PARENT_A, with the app. */
	UUID appParent;

	UUID noApp;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		appParent = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class, world.user(PARENT_A));
		noApp = guardian(world.studentA, "+237690000301", "FR");
		sms.reset();
		push.reset();
		clock.set(Instant.parse("2026-11-02T07:40:00Z"));
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aMorningOfAbsencesAndALatenessIsOneSms() { // BR-22, D6 (plan check: one digest per student)
		absence(noApp, world.studentA, MONDAY, 1);
		absence(noApp, world.studentA, MONDAY, 2);
		absence(noApp, world.studentA, MONDAY, 3);
		lateness(noApp, world.studentA, MONDAY, "07:48");

		clock.set(FIRST);
		assertThat(digest.runDue()).isOne();
		dispatcher.dispatchDue();

		assertThat(sms.sent).singleElement().satisfies(message -> assertThat(message.text())
			.isEqualTo("SchoolLink : Élève - absence P1-P3 et retard (07:48) le 02/11. Lycée Fictif X"));
		assertThat(jdbc.queryForList("""
				SELECT DISTINCT o.status FROM notification_outbox o JOIN notification_outbox d ON d.id = o.digest_id
				WHERE o.guardian_id = ? AND o.event_type IN ('ABSENCE', 'LATENESS') AND d.status = 'SENT'""",
				String.class, noApp)).containsExactly("DIGESTED");
	}

	@Test
	void eachGuardianAndEachChildGetsTheirOwnSms() { // BR-20, BR-22: one per student, per guardian
		UUID second = guardian(world.studentA, "+237690000302", "EN");
		UUID sibling = student("TW-0301", "Paul");
		link(noApp, sibling);
		absence(noApp, world.studentA, MONDAY, 1);
		absence(second, world.studentA, MONDAY, 1);
		lateness(noApp, sibling, MONDAY, "07:52");

		clock.set(FIRST);
		assertThat(digest.runDue()).isEqualTo(3);
		dispatcher.dispatchDue();

		assertThat(sms.sent).extracting(SmsMessage::text)
			.containsExactlyInAnyOrder("SchoolLink : Élève - absence P1 le 02/11. Lycée Fictif X",
					"SchoolLink: Élève absent P1 on 02/11. Lycée Fictif X",
					"SchoolLink : Paul - retard (07:52) le 02/11. Lycée Fictif X");
	}

	@Test
	void anOpenedPushLeavesThatEventOutOfTheSms() { // BR-22 (plan check)
		UUID p1 = absence(appParent, world.studentA, MONDAY, 1);
		UUID p2 = absence(appParent, world.studentA, MONDAY, 2);
		push(appParent, p1, true);
		push(appParent, p2, false);

		clock.set(FIRST);
		digest.runDue();
		dispatcher.dispatchDue();

		assertThat(sms.sent).singleElement()
			.satisfies(message -> assertThat(message.text()).contains("absence P2 le 02/11"));
		assertThat(smsStatus(appParent, p1)).isEqualTo("SKIPPED");
		assertThat(smsStatus(appParent, p2)).isEqualTo("DIGESTED");
	}

	@Test
	void aGuardianWhoOpenedEveryPushGetsNoSms() { // BR-22, D6: never an empty digest
		UUID p1 = absence(appParent, world.studentA, MONDAY, 1);
		push(appParent, p1, true);

		clock.set(FIRST);

		assertThat(digest.runDue()).isZero();
		assertThat(smsStatus(appParent, p1)).isEqualTo("SKIPPED");
		assertThat(digests()).isZero();
	}

	@Test
	void theDigestNeverShowsInTheParentsInbox() throws Exception { // UC-P2: the push stands for it
		UUID p1 = absence(appParent, world.studentA, MONDAY, 1);
		push(appParent, p1, false);
		clock.set(FIRST);
		digest.runDue();

		mockMvc.perform(get("/api/v1/me/notifications").header("Authorization", "Bearer " + world.token(PARENT_A)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].eventType").value(containsInAnyOrder("ABSENCE")));
	}

	@Test
	void eachRunHappensOnceAtItsTime() { // BR-22, D6, NFR-5
		absence(noApp, world.studentA, MONDAY, 1);

		clock.set(FIRST.minusSeconds(60));
		assertThat(digest.runDue()).as("12:59, too early").isZero();
		clock.set(FIRST);
		assertThat(digest.runDue()).isOne();
		clock.set(FIRST.plusSeconds(120));
		assertThat(digest.runDue()).as("13:02, already run").isZero();

		absence(noApp, world.studentA, MONDAY, 5);
		clock.set(SECOND.minusSeconds(60));
		assertThat(digest.runDue()).as("16:59").isZero();
		clock.set(SECOND);
		assertThat(digest.runDue()).isOne();
		dispatcher.dispatchDue();

		// D6: the 17:00 run tells only what is new since 13:00
		assertThat(sms.sent).extracting(SmsMessage::text)
			.containsExactlyInAnyOrder("SchoolLink : Élève - absence P1 le 02/11. Lycée Fictif X",
					"SchoolLink : Élève - absence P5 le 02/11. Lycée Fictif X");
	}

	@Test
	void aRunMissedWhileTheServerWasDownHappensWhenItComesBack() { // NFR-5
		absence(noApp, world.studentA, MONDAY, 1);

		clock.set(FIRST.plusSeconds(25 * 60));

		assertThat(digest.runDue()).isOne();
	}

	@Test
	void aDayWithoutAbsenceOrLatenessSendsNothing() { // D6
		clock.set(FIRST);

		assertThat(digest.runDue()).isZero();
		assertThat(digests()).isZero();
		assertThat(jdbc.queryForObject("SELECT digests FROM sms_digest_run WHERE school_id = ?", Integer.class,
				world.schoolX)).isZero();
	}

	@Test
	void aCancelledAbsenceIsNotInTheDigest() { // BR-14, BR-15
		UUID cancelled = absence(noApp, world.studentA, MONDAY, 1);
		absence(noApp, world.studentA, MONDAY, 2);
		corrections.correct(world.schoolX, EventType.ABSENCE, cancelled);

		clock.set(FIRST);
		digest.runDue();
		dispatcher.dispatchDue();

		assertThat(sms.sent).singleElement()
			.satisfies(message -> assertThat(message.text()).contains("absence P2 le 02/11"));
		assertThat(smsStatus(noApp, cancelled)).isEqualTo("WITHDRAWN");
	}

	@Test
	void anAbsenceCancelledAfterItsDigestWentGetsACorrection() { // BR-14, D11, D33
		UUID absence = absence(noApp, world.studentA, MONDAY, 1);
		clock.set(FIRST);
		digest.runDue();
		dispatcher.dispatchDue();

		corrections.correct(world.schoolX, EventType.ABSENCE, absence);

		assertThat(jdbc.queryForObject("""
				SELECT channel || ' ' || status FROM notification_outbox
				WHERE event_type = 'CORRECTION' AND event_id = ? AND guardian_id = ?""", String.class, absence, noApp))
			.isEqualTo("SMS QUEUED");
	}

	@Test
	void anAbsenceWhoseDigestFailedGetsNoCorrection() { // BR-14: the parent never heard of it
		UUID absence = absence(noApp, world.studentA, MONDAY, 1);
		clock.set(FIRST);
		digest.runDue();
		jdbc.update("UPDATE notification_outbox SET status = 'FAILED' WHERE event_type = 'DIGEST'");

		corrections.correct(world.schoolX, EventType.ABSENCE, absence);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE event_type = 'CORRECTION'",
				Integer.class)).isZero();
	}

	@Test
	void anOlderDayGetsItsOwnSms() { // BR-22: a session synced late
		absence(noApp, world.studentA, "2026-10-30", 2);
		absence(noApp, world.studentA, MONDAY, 1);

		clock.set(FIRST);
		assertThat(digest.runDue()).isEqualTo(2);
		dispatcher.dispatchDue();

		assertThat(sms.sent).extracting(SmsMessage::text)
			.containsExactlyInAnyOrder("SchoolLink : Élève - absence P2 le 30/10. Lycée Fictif X",
					"SchoolLink : Élève - absence P1 le 02/11. Lycée Fictif X");
	}

	@Test
	void theDigestIsInTheGuardiansLanguageNow() { // BR-20
		absence(noApp, world.studentA, MONDAY, 1);
		jdbc.update("UPDATE guardian SET language = 'EN' WHERE id = ?", noApp);

		clock.set(FIRST);
		digest.runDue();
		dispatcher.dispatchDue();

		assertThat(sms.sent).singleElement()
			.satisfies(message -> assertThat(message.text()).isEqualTo("SchoolLink: Élève absent P1 on 02/11. Lycée Fictif X"));
	}

	@Test
	void atTheCapOnlySummonsGoBySms() { // BR-25, D12 (plan check)
		jdbc.update("UPDATE school SET sms_monthly_cap = 2 WHERE id = ?", world.schoolX);
		sentSms(Instant.parse("2026-11-01T08:00:00Z"));
		sentSms(Instant.parse("2026-11-02T07:00:00Z"));
		absence(noApp, world.studentA, MONDAY, 1);
		clock.set(FIRST);
		digest.runDue();
		UUID summons = row(noApp, world.studentA, "SUMMONS", UUID.randomUUID(), "SMS", "QUEUED",
				Map.of("firstName", "Élève", "meetingDate", "2026-11-03", "meetingTime", "07:30"));

		dispatcher.dispatchDue();

		assertThat(sms.sent).singleElement()
			.satisfies(message -> assertThat(message.notificationId()).isEqualTo(summons));
		assertThat(jdbc.queryForMap("""
				SELECT status, last_error FROM notification_outbox WHERE event_type = 'DIGEST'"""))
			.containsEntry("status", "FAILED").containsEntry("last_error", "monthly SMS cap reached");
		assertThat(usage.sentThisMonth(world.schoolX)).isEqualTo(3);
	}

	@Test
	void theCounterStartsAgainEachMonthInTheSchoolsTimeZone() { // BR-25
		// 31/10 23:30 UTC is already 1 November in Douala
		sentSms(Instant.parse("2026-10-31T23:30:00Z"));
		sentSms(Instant.parse("2026-10-31T22:30:00Z"));
		row(noApp, world.studentA, "SUMMONS", UUID.randomUUID(), "PUSH", "SENT", Map.of());
		clock.set(FIRST);

		assertThat(usage.sentThisMonth(world.schoolX)).isOne();
		assertThat(usage.sentThisMonth(world.schoolY)).isZero();
	}

	@Test
	void theDueRunIsTheLatestTimePassed() { // BR-22, D28: the second time is optional
		Times both = new Times(LocalTime.of(7, 30), LocalTime.of(13, 0), LocalTime.of(17, 0));
		Times one = new Times(LocalTime.of(7, 30), LocalTime.of(13, 0), null);

		assertThat(SmsDigest.due(both, LocalTime.of(12, 59))).isNull();
		assertThat(SmsDigest.due(both, LocalTime.of(13, 0))).isEqualTo(LocalTime.of(13, 0));
		assertThat(SmsDigest.due(both, LocalTime.of(17, 0))).isEqualTo(LocalTime.of(17, 0));
		assertThat(SmsDigest.due(one, LocalTime.of(23, 0))).isEqualTo(LocalTime.of(13, 0));
	}

	private UUID absence(UUID guardian, UUID student, String date, int period) {
		UUID record = UUID.randomUUID();
		row(guardian, student, "ABSENCE", record, "SMS", "HELD", Map.of("studentId", student.toString(), "firstName",
				firstName(student), "date", date, "periodNumber", period, "periodStart", "07:30"));
		return record;
	}

	private UUID lateness(UUID guardian, UUID student, String date, String arrival) {
		UUID record = UUID.randomUUID();
		row(guardian, student, "LATENESS", record, "SMS", "HELD",
				Map.of("studentId", student.toString(), "firstName", firstName(student), "date", date, "arrivalTime", arrival));
		return record;
	}

	/** The push of the same absence, already sent; opened or not. */
	private void push(UUID guardian, UUID absence, boolean opened) {
		jdbc.update("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				    payload, status, attempts, sent_at, opened_at)
				SELECT school_id, guardian_id, student_id, event_type, event_id, 'PUSH', language, payload,
				       CASE WHEN ? THEN 'OPENED' ELSE 'SENT' END, 1, ?, ?
				FROM notification_outbox WHERE event_id = ? AND guardian_id = ? AND channel = 'SMS'""", opened,
				Timestamp.from(Instant.parse("2026-11-02T07:41:00Z")),
				opened ? Timestamp.from(Instant.parse("2026-11-02T09:00:00Z")) : null, absence, guardian);
	}

	private void sentSms(Instant at) {
		jdbc.update("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				    payload, status, attempts, sent_at)
				VALUES (?, ?, ?, 'CORRECTION', ?, 'SMS', 'FR', '{}'::jsonb, 'SENT', 1, ?)""", world.schoolX, noApp,
				world.studentA, UUID.randomUUID(), Timestamp.from(at));
	}

	private UUID row(UUID guardian, UUID student, String type, UUID eventId, String channel, String status,
			Map<String, Object> payload) {
		return jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				    payload, status, next_attempt_at, sent_at)
				VALUES (?, ?, ?, ?, ?, ?, 'FR', CAST(? AS jsonb), ?, ?, ?) RETURNING id""", UUID.class, world.schoolX,
				guardian, student, type, eventId, channel, json.writeValueAsString(payload), status,
				Timestamp.from(Instant.parse("2026-11-02T07:40:00Z")),
				"SENT".equals(status) ? Timestamp.from(Instant.parse("2026-11-02T07:41:00Z")) : null);
	}

	private String smsStatus(UUID guardian, UUID event) {
		return jdbc.queryForObject("""
				SELECT status FROM notification_outbox WHERE event_id = ? AND guardian_id = ? AND channel = 'SMS'""",
				String.class, event, guardian);
	}

	private int digests() {
		return jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE event_type = 'DIGEST'", Integer.class);
	}

	private String firstName(UUID student) {
		return jdbc.queryForObject("SELECT first_name FROM student WHERE id = ?", String.class, student);
	}

	private UUID guardian(UUID student, String phone, String language) {
		UUID guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, 'Parent Fictif', ?, ?) RETURNING id""",
				UUID.class, world.schoolX, phone, language);
		link(guardian, student);
		return guardian;
	}

	private void link(UUID guardian, UUID student) {
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'GUARDIAN', false)""", world.schoolX, student, guardian);
	}

	private UUID student(String matricule, String firstName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, 'Fictif', 'M', 2013) RETURNING id""", UUID.class, world.schoolX, world.classA,
				matricule, firstName);
	}

}
