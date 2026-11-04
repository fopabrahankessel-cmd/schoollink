package com.schoollink.notification.internal;

import com.google.firebase.FirebaseApp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

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

	/**
	 * BR-21: a real key file must start Firebase. Runs without any secret: the key is generated here, and
	 * nothing is sent. It caught a JSON library missing from the classpath that only showed with a key set.
	 */
	@Test
	void aServiceAccountFileStartsFirebase(@TempDir Path dir) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		String key = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
				+ "\n-----END PRIVATE KEY-----\n";
		Path file = dir.resolve("firebase-service-account.json");
		Files.writeString(file, JsonMapper.shared().writeValueAsString(Map.of("type", "service_account", "project_id",
				"fictional-project", "private_key_id", "0", "private_key", key, "client_email",
				"push@fictional-project.iam.gserviceaccount.com", "client_id", "0", "token_uri",
				"https://oauth2.googleapis.com/token")));
		try {
			MockEnvironment environment = new MockEnvironment();
			environment.setActiveProfiles("prod");

			assertThat(new PushConfig().pushProvider(file.toString(), null, environment))
				.isInstanceOf(FirebasePushProvider.class);
		}
		finally {
			FirebaseApp.getApps().stream().filter(app -> app.getName().equals(PushConfig.APP_NAME)).forEach(FirebaseApp::delete);
		}
	}

	@Test
	void anUnreadableCredentialsFileStopsTheStart() {
		assertThatThrownBy(() -> new PushConfig().pushProvider("does-not-exist/firebase.json", null, new MockEnvironment()))
			.hasMessageContaining("Cannot read the Firebase service-account file");
	}

}
