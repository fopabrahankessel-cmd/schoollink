package com.schoollink.notification.internal;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * The push provider (BR-21). With {@code FIREBASE_CREDENTIALS} (the path of the Firebase
 * service-account JSON file, never committed), pushes go through FCM. Without it, the dev and
 * test profiles fall back to {@link LoggingPushProvider}, which delivers nothing; any other
 * profile refuses to start, so a server never marks pushes sent that no phone received.
 */
@Configuration(proxyBeanMethods = false)
class PushConfig {

	static final String APP_NAME = "schoollink";

	@Bean
	PushProvider pushProvider(@Value("${schoollink.push.firebase-credentials:}") String credentials,
			DeviceTokens devices, Environment environment) {
		if (credentials.isBlank()) {
			if (!environment.acceptsProfiles(Profiles.of("dev | test"))) {
				throw new IllegalStateException(
						"Set FIREBASE_CREDENTIALS to the Firebase service-account file to send push notifications");
			}
			return new LoggingPushProvider();
		}
		return new FirebasePushProvider(FirebaseMessaging.getInstance(firebaseApp(Path.of(credentials))), devices);
	}

	private static FirebaseApp firebaseApp(Path credentials) {
		// a restarted context in the same JVM (dev tools, tests) finds the app already there
		for (FirebaseApp app : FirebaseApp.getApps()) {
			if (app.getName().equals(APP_NAME)) {
				return app;
			}
		}
		try (InputStream in = Files.newInputStream(credentials)) {
			FirebaseOptions options = FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(in)).build();
			return FirebaseApp.initializeApp(options, APP_NAME);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Cannot read the Firebase service-account file in FIREBASE_CREDENTIALS", ex);
		}
	}

}
