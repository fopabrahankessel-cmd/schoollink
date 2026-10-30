package com.schoollink.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Without Firebase credentials, only dev and test may start, with pushes logged (task 2.7, BR-21). */
class PushConfigTests {

	@Test
	void devAndTestLogPushesWithoutCredentials() {
		for (String profile : new String[] { "dev", "test" }) {
			MockEnvironment environment = new MockEnvironment();
			environment.setActiveProfiles(profile);
			assertThat(new PushConfig().pushProvider("", null, environment)).isInstanceOf(LoggingPushProvider.class);
		}
	}

	@Test
	void anyOtherProfileRefusesToStartWithoutCredentials() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");

		assertThatThrownBy(() -> new PushConfig().pushProvider(" ", null, environment))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("FIREBASE_CREDENTIALS");
		assertThatThrownBy(() -> new PushConfig().pushProvider("", null, new MockEnvironment()))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anUnreadableCredentialsFileStopsTheStart() {
		assertThatThrownBy(() -> new PushConfig().pushProvider("does-not-exist/firebase.json", null, new MockEnvironment()))
			.hasMessageContaining("Cannot read the Firebase service-account file");
	}

}
