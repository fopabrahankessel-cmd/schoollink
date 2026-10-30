package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.notification.internal.OutboxDispatcher.Claimed;
import com.schoollink.notification.internal.PushProvider.PushMessage;
import com.schoollink.notification.internal.SendResult.Rejected;
import com.schoollink.notification.internal.SendResult.Retry;
import com.schoollink.notification.internal.SmsProvider.SmsMessage;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outbox dispatcher (task 2.6, BR-21, BR-24, D13, NFR-6, NFR-9) with scripted providers and a
 * set clock. The dispatcher's own transactions join the test's, so everything is rolled back.
 * Generated data only.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, OutboxDispatcherTests.Providers.class })
@Transactional
class OutboxDispatcherTests {

	static final Instant T0 = Instant.parse("2026-11-02T07:40:00Z");

	@Autowired
	OutboxDispatcher dispatcher;

	@Autowired
	ScriptedPush push;

	@Autowired
	ScriptedSms sms;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	TestClock clock;

	TestWorld world;

	UUID withApp;

	UUID parentAccount;

	UUID withoutApp;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		parentAccount = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone) VALUES (?, 'Parent', 'Fictif', '+237690000201') RETURNING id""",
				UUID.class, world.schoolX);
		withApp = guardian(parentAccount, "+237690000201", "FR");
		withoutApp = guardian(null, "+237690000202", "EN");
		push.reset();
		sms.reset();
		clock.set(T0);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aQueuedPushIsSentInTheGuardiansLanguage() { // BR-20, BR-21
		UUID row = absencePush(withApp);

		assertThat(dispatcher.dispatchDue()).isOne();

		assertThat(push.sent).singleElement().satisfies(message -> {
			assertThat(message.notificationId()).isEqualTo(row);
			assertThat(message.userId()).isEqualTo(parentAccount);
			assertThat(message.title()).isEqualTo("Absence : Awa");
			assertThat(message.body()).isEqualTo("Absence signalée pour Awa en période 1 (07:30), le 02/11.");
		});
		assertThat(state(row)).containsEntry("status", "SENT").containsEntry("attempts", 1)
			.containsEntry("sent_at", Timestamp.from(T0)).containsEntry("claimed_at", null)
			.containsEntry("last_error", null);
	}

	@Test
	void aProviderThatFailsTwiceThenSucceedsEndsSentAfterThreeAttempts() { // BR-24 (plan check)
		push.script(new Retry("timeout"), new Retry("timeout"), SendResult.sent());
		UUID row = absencePush(withApp);

		dispatcher.dispatchDue();
		assertThat(state(row)).containsEntry("status", "QUEUED").containsEntry("attempts", 1)
			.containsEntry("next_attempt_at", Timestamp.from(T0.plusSeconds(30)))
			.containsEntry("last_error", "timeout");

		assertThat(dispatcher.dispatchDue()).as("not due before the backoff").isZero();

		clock.set(T0.plusSeconds(30));
		dispatcher.dispatchDue();
		assertThat(state(row)).containsEntry("status", "QUEUED").containsEntry("attempts", 2)
			.containsEntry("next_attempt_at", Timestamp.from(T0.plusSeconds(30).plus(Duration.ofMinutes(2))));

		clock.set(T0.plusSeconds(30).plus(Duration.ofMinutes(2)));
		dispatcher.dispatchDue();
		assertThat(state(row)).containsEntry("status", "SENT").containsEntry("attempts", 3).containsEntry("last_error", null);
		assertThat(push.sent).hasSize(3);
	}

	@Test
	void afterThreeRetriesTheRowFails() { // BR-24
		push.script(new Retry("down"), new Retry("down"), new Retry("down"), new Retry("still down"));
		UUID row = absencePush(withApp);
		Instant now = T0;

		for (int attempt = 1; attempt <= 4; attempt++) {
			clock.set(now);
			assertThat(dispatcher.dispatchDue()).as("attempt " + attempt).isOne();
			if (attempt < 4) {
				now = now.plus(OutboxDispatcher.BACKOFF.get(attempt - 1));
			}
		}

		assertThat(state(row)).containsEntry("status", "FAILED").containsEntry("attempts", 4)
			.containsEntry("last_error", "still down");
		clock.set(now.plus(Duration.ofHours(1)));
		assertThat(dispatcher.dispatchDue()).isZero();
	}

	@Test
	void aRefusalFailsAtOnce() { // BR-24: retrying would not help
		push.script(new Rejected("no device registered"));
		UUID row = absencePush(withApp);

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "FAILED").containsEntry("attempts", 1)
			.containsEntry("last_error", "no device registered");
	}

	@Test
	void aProviderExceptionIsRetriedWithoutItsMessage() { // NFR-9: the message may hold the phone
		sms.failWith(() -> new IllegalStateException("gateway refused +237690000202"));
		UUID row = correctionSms(withoutApp);

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "QUEUED")
			.containsEntry("last_error", "provider error: IllegalStateException");
	}

	@Test
	void aRowPickedMidSendIsRecoveredAfterARestart() { // NFR-6 (plan check)
		UUID row = absencePush(withApp);
		List<Claimed> crashed = dispatcher.claim(); // claimed, then the server stopped before sending
		assertThat(crashed).hasSize(1);
		assertThat(state(row)).containsEntry("status", "SENDING").containsEntry("attempts", 1);

		assertThat(dispatcher.dispatchDue()).as("within the lease, nobody takes it").isZero();

		clock.set(T0.plus(OutboxDispatcher.LEASE));
		assertThat(dispatcher.dispatchDue()).isOne();
		assertThat(state(row)).containsEntry("status", "SENT").containsEntry("attempts", 2);
		assertThat(push.sent).hasSize(1);

		// the crashed round's late outcome does not overwrite the recovered one
		dispatcher.complete(crashed.getFirst(), new Retry("too late"));
		assertThat(state(row)).containsEntry("status", "SENT");
	}

	@Test
	void heldSmsWaitForTheDigest() { // BR-22
		jdbc.update("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language, payload, status, next_attempt_at)
				VALUES (?, ?, ?, 'ABSENCE', ?, 'SMS', 'EN', '{}', 'HELD', ?)""", world.schoolX, withoutApp, world.studentA,
				UUID.randomUUID(), Timestamp.from(T0));

		assertThat(dispatcher.dispatchDue()).isZero();
		assertThat(sms.sent).isEmpty();
	}

	@Test
	void anInvitationSmsIsSentAndItsCodeErased() { // D13, NFR-9
		UUID invitation = invitation("2026-11-30T00:00:00Z", false);
		UUID row = invitationSms(invitation, "K7M2-Q9XP");

		dispatcher.dispatchDue();

		assertThat(sms.sent).singleElement().satisfies(message -> {
			assertThat(message.phone()).isEqualTo("+237690000202");
			assertThat(message.text()).isEqualTo(
					"SchoolLink: your activation code for Lycée Fictif X is K7M2-Q9XP. Enter it in the SchoolLink app before 30/11.");
			assertThat(message.toString()).doesNotContain("690000202", "K7M2");
		});
		assertThat(state(row)).containsEntry("status", "SENT");
		assertThat(payload(row)).doesNotContain("K7M2").contains("expiresAt");
	}

	@Test
	void aRevokedInvitationIsNotSentAndItsCodeErased() { // D13
		UUID invitation = invitation("2026-11-30T00:00:00Z", true);
		UUID row = invitationSms(invitation, "K7M2-Q9XP");

		dispatcher.dispatchDue();

		assertThat(sms.sent).isEmpty();
		assertThat(state(row)).containsEntry("status", "FAILED")
			.containsEntry("last_error", "the invitation code was revoked or has expired");
		assertThat(payload(row)).doesNotContain("K7M2");
	}

	@Test
	void aPushForAGuardianWithoutAnAccountFails() { // D7
		UUID row = absencePush(withoutApp);

		dispatcher.dispatchDue();

		assertThat(push.sent).isEmpty();
		assertThat(state(row)).containsEntry("status", "FAILED")
			.containsEntry("last_error", "the guardian has no app account");
	}

	private Map<String, Object> state(UUID row) {
		Map<String, Object> state = jdbc.queryForMap("""
				SELECT status, attempts::int AS attempts, sent_at, claimed_at, next_attempt_at, last_error
				FROM notification_outbox WHERE id = ?""", row);
		return state;
	}

	private String payload(UUID row) {
		return jdbc.queryForObject("SELECT payload::text FROM notification_outbox WHERE id = ?", String.class, row);
	}

	private UUID absencePush(UUID guardian) {
		return row(guardian, world.studentA, "ABSENCE", UUID.randomUUID(), "PUSH", "FR",
				Map.of("firstName", "Awa", "date", "2026-11-02", "periodNumber", 1, "periodStart", "07:30"));
	}

	private UUID correctionSms(UUID guardian) {
		return row(guardian, world.studentA, "CORRECTION", UUID.randomUUID(), "SMS", "EN",
				Map.of("firstName", "Awa", "date", "2026-11-02", "kind", "LATENESS"));
	}

	private UUID invitationSms(UUID invitation, String code) {
		return row(withoutApp, null, "INVITATION", invitation, "SMS", "EN",
				Map.of("code", code, "expiresAt", "2026-11-29T23:30:00Z"));
	}

	private UUID row(UUID guardian, UUID student, String type, UUID eventId, String channel, String language,
			Map<String, Object> payload) {
		return jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language, payload, next_attempt_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?) RETURNING id""", UUID.class, world.schoolX, guardian,
				student, type, eventId, channel, language, json.writeValueAsString(payload), Timestamp.from(T0));
	}

	private UUID guardian(UUID account, String phone, String language) {
		UUID guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, user_id, name, phone, language) VALUES (?, ?, 'Parent Fictif', ?, ?) RETURNING id""",
				UUID.class, world.schoolX, account, phone, language);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'GUARDIAN', false)""", world.schoolX, world.studentA, guardian);
		return guardian;
	}

	private UUID invitation(String expiresAt, boolean revoked) {
		return jdbc.queryForObject("""
				INSERT INTO invitation_code (school_id, guardian_id, code_hash, created_at, expires_at, revoked_at)
				VALUES (?, ?, ?, ?, ?::timestamptz, ?) RETURNING id""", UUID.class, world.schoolX, withoutApp,
				"hash-" + UUID.randomUUID(), Timestamp.from(T0.minus(Duration.ofDays(1))), expiresAt,
				revoked ? Timestamp.from(T0.minusSeconds(60)) : null);
	}

	/** A provider that answers from a script (then "sent") and records what it was asked. */
	abstract static class Scripted<M> {

		final List<M> sent = new ArrayList<>();

		private final Deque<SendResult> script = new ArrayDeque<>();

		private Supplier<RuntimeException> failure;

		void script(SendResult... results) {
			script.addAll(List.of(results));
		}

		void failWith(Supplier<RuntimeException> failure) {
			this.failure = failure;
		}

		void reset() {
			sent.clear();
			script.clear();
			failure = null;
		}

		SendResult answer(M message) {
			if (failure != null) {
				throw failure.get();
			}
			sent.add(message);
			return script.isEmpty() ? SendResult.sent() : script.poll();
		}

	}

	static class ScriptedPush extends Scripted<PushMessage> implements PushProvider {

		@Override
		public SendResult send(PushMessage message) {
			return answer(message);
		}

	}

	static class ScriptedSms extends Scripted<SmsMessage> implements SmsProvider {

		@Override
		public SendResult send(SmsMessage message) {
			return answer(message);
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Providers {

		@Bean
		@Primary
		ScriptedPush scriptedPush() {
			return new ScriptedPush();
		}

		@Bean
		@Primary
		ScriptedSms scriptedSms() {
			return new ScriptedSms();
		}

	}

}
