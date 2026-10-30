package com.schoollink.notification.internal;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.google.firebase.messaging.FcmFakes.batch;
import static com.google.firebase.messaging.FcmFakes.delivered;
import static com.google.firebase.messaging.FcmFakes.failure;
import static com.google.firebase.messaging.FcmFakes.json;
import static com.google.firebase.messaging.FcmFakes.refused;
import static com.schoollink.testsupport.Actor.PARENT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The FCM push provider (task 2.7, BR-21, BR-24, NFR-9) behind the outbox dispatcher, with a
 * mocked {@link FirebaseMessaging}. PARENT_A is the guardian of student A. Tokens are made up.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, FirebasePushProviderTests.Fcm.class })
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class FirebasePushProviderTests {

	static final Instant T0 = Instant.parse("2026-11-02T07:40:00Z");

	static final String PHONE = "fake-phone:APA91bFakePhoneToken_0001";

	static final String TABLET = "fake-tablet:APA91bFakeTabletToken_0002";

	@Autowired
	OutboxDispatcher dispatcher;

	@Autowired
	FirebaseMessaging fcm;

	@Autowired
	DeviceTokens devices;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	TestClock clock;

	TestWorld world;

	UUID parent;

	UUID guardian;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		parent = world.user(PARENT_A);
		guardian = jdbc.queryForObject("SELECT id FROM guardian WHERE user_id = ?", UUID.class, parent);
		Mockito.reset(fcm);
		clock.set(T0);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void theAbsenceGoesToEveryPhoneOfTheParent() throws Exception { // BR-20, BR-21
		phones(PHONE, TABLET);
		when(fcm.sendEachForMulticast(any())).thenReturn(batch(delivered("m1"), delivered("m2")));
		UUID row = absencePush();

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "SENT").containsEntry("attempts", 1);
		ArgumentCaptor<MulticastMessage> sent = ArgumentCaptor.forClass(MulticastMessage.class);
		verify(fcm).sendEachForMulticast(sent.capture());
		List<JsonNode> messages = json(sent.getValue()).stream().map(json::readTree).toList();
		assertThat(messages).extracting(m -> m.get("token").asString()).containsExactlyInAnyOrder(PHONE, TABLET);
		assertThat(messages).allSatisfy(m -> {
			assertThat(m.at("/notification/title").asString()).isEqualTo("Absence : Awa");
			assertThat(m.at("/notification/body").asString())
				.isEqualTo("Absence signalée pour Awa en période 1 (07:30), le 02/11.");
			assertThat(m.at("/data/notificationId").asString()).isEqualTo(row.toString());
			assertThat(m.at("/android/priority").asString()).isEqualTo("high");
		});
	}

	@Test
	void onePhoneIsEnoughAndAnUninstalledAppLosesItsToken() throws Exception { // BR-21
		phones(PHONE, TABLET);
		when(fcm.sendEachForMulticast(any()))
			.thenReturn(batch(delivered("m1"), refused(MessagingErrorCode.UNREGISTERED, "Requested entity was not found.")));
		UUID row = absencePush();

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "SENT");
		assertThat(devices.tokensOf(parent)).containsExactly(PHONE);
	}

	@Test
	void whenNoPhoneCanReceiveItTheRowFailsAtOnce() throws Exception { // BR-22: the SMS digest takes over
		phones(PHONE, TABLET);
		when(fcm.sendEachForMulticast(any())).thenReturn(batch(refused(MessagingErrorCode.UNREGISTERED, "gone"),
				refused(MessagingErrorCode.SENDER_ID_MISMATCH, "wrong sender")));
		UUID row = absencePush();

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "FAILED").containsEntry("attempts", 1)
			.containsEntry("last_error", "no device accepted it: SENDER_ID_MISMATCH, UNREGISTERED");
		assertThat(devices.tokensOf(parent)).isEmpty();
	}

	@Test
	void aParentWithoutARegisteredPhoneFailsWithoutCallingFcm() throws Exception {
		UUID row = absencePush();

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "FAILED").containsEntry("last_error", "no device registered");
		verify(fcm, never()).sendEachForMulticast(any());
	}

	@Test
	void aPassingFcmFailureIsRetriedAndKeepsTheTokens() throws Exception { // BR-24
		phones(PHONE, TABLET);
		when(fcm.sendEachForMulticast(any())).thenReturn(
				batch(refused(MessagingErrorCode.UNAVAILABLE, "try later"), refused(MessagingErrorCode.UNREGISTERED, "gone")),
				batch(delivered("m1")));
		UUID row = absencePush();

		dispatcher.dispatchDue();
		assertThat(state(row)).containsEntry("status", "QUEUED").containsEntry("last_error", "FCM UNAVAILABLE");
		assertThat(devices.tokensOf(parent)).containsExactly(PHONE);

		clock.set(T0.plus(OutboxDispatcher.BACKOFF.getFirst()));
		dispatcher.dispatchDue();
		assertThat(state(row)).containsEntry("status", "SENT").containsEntry("attempts", 2);
	}

	@Test
	void aFailedRequestIsRetriedWithoutLeakingTheToken(CapturedOutput output) throws Exception { // NFR-9
		phones(PHONE);
		when(fcm.sendEachForMulticast(any()))
			.thenThrow(failure(MessagingErrorCode.INTERNAL, "Internal error sending to " + PHONE));
		UUID row = absencePush();

		dispatcher.dispatchDue();

		assertThat(state(row)).containsEntry("status", "QUEUED").containsEntry("last_error", "FCM INTERNAL");
		assertThat(output.getAll()).contains("FCM request failed with INTERNAL").doesNotContain(PHONE, "APA91b");
	}

	private void phones(String... tokens) {
		for (String token : tokens) {
			devices.register(world.schoolX, parent, token, "ANDROID", T0.minusSeconds(60));
		}
	}

	private UUID absencePush() {
		return jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language, payload, next_attempt_at)
				VALUES (?, ?, ?, 'ABSENCE', ?, 'PUSH', 'FR', CAST(? AS jsonb), ?) RETURNING id""", UUID.class, world.schoolX,
				guardian, world.studentA, UUID.randomUUID(),
				json.writeValueAsString(
						Map.of("firstName", "Awa", "date", "2026-11-02", "periodNumber", 1, "periodStart", "07:30")),
				Timestamp.from(T0));
	}

	private Map<String, Object> state(UUID row) {
		return jdbc.queryForMap("SELECT status, attempts::int AS attempts, last_error FROM notification_outbox WHERE id = ?",
				row);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class Fcm {

		@Bean
		FirebaseMessaging firebaseMessaging() {
			return Mockito.mock(FirebaseMessaging.class);
		}

		@Bean
		@Primary
		PushProvider firebasePushProvider(FirebaseMessaging firebaseMessaging, DeviceTokens devices) {
			return new FirebasePushProvider(firebaseMessaging, devices);
		}

	}

}
