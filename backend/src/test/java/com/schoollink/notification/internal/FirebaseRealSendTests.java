package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.notification.internal.PushProvider.PushMessage;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;

import static com.schoollink.testsupport.Actor.PARENT_A;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 2.7's manual check: a real push to a test phone through Firebase (BR-21). Skipped unless
 * both are set:
 * <ul>
 * <li>{@code FIREBASE_CREDENTIALS}: the path of the service-account JSON file (never committed);</li>
 * <li>{@code FCM_TEST_TOKEN}: the FCM token of the SchoolLink app on the test phone.</li>
 * </ul>
 * Run with {@code mvnw test -Dtest=FirebaseRealSendTests}; the phone shows "SchoolLink test".
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIfEnvironmentVariable(named = "FIREBASE_CREDENTIALS", matches = ".+")
@EnabledIfEnvironmentVariable(named = "FCM_TEST_TOKEN", matches = ".+")
@Transactional
class FirebaseRealSendTests {

	@Autowired
	PushProvider push;

	@Autowired
	DeviceTokens devices;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Test
	void aRealPushReachesTheTestPhone() {
		assertThat(push).isInstanceOf(FirebasePushProvider.class);
		TestWorld world = TestWorld.create(jdbc, encoder);
		UUID parent = world.user(PARENT_A);
		devices.register(world.schoolX, parent, System.getenv("FCM_TEST_TOKEN"), "ANDROID", Instant.now());

		SendResult result = push.send(new PushMessage(UUID.randomUUID(), parent, "SchoolLink test",
				"Push de test / test push (" + Instant.now() + ")"));

		assertThat(result).isEqualTo(SendResult.sent());
	}

}
