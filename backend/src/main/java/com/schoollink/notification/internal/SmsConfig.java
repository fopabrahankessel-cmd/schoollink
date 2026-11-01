package com.schoollink.notification.internal;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SMS gateway (BR-22, BR-23, D32). With {@code TWILIO_ACCOUNT_SID}, SMS go through Twilio.
 * Without it, the dev and test profiles fall back to {@link LoggingSmsProvider}, which delivers
 * nothing; any other profile refuses to start, so a server never marks SMS sent that no phone got.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TwilioSettings.class)
class SmsConfig {

	@Bean
	SmsProvider smsProvider(TwilioSettings twilio, JsonMapper json, Environment environment) {
		if (!twilio.configured()) {
			if (!environment.acceptsProfiles(Profiles.of("dev | test"))) {
				throw new IllegalStateException("Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM and "
						+ "SMS_STATUS_CALLBACK_URL to send SMS");
			}
			return new LoggingSmsProvider(environment);
		}
		if (twilio.authToken().isEmpty() || twilio.from().isEmpty() || twilio.statusCallback().isEmpty()) {
			throw new IllegalStateException(
					"TWILIO_AUTH_TOKEN, TWILIO_FROM and SMS_STATUS_CALLBACK_URL are required with TWILIO_ACCOUNT_SID");
		}
		// the dispatcher waits for each send: never for long
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
		requests.setReadTimeout(Duration.ofSeconds(15));
		return new TwilioSmsProvider(RestClient.builder().requestFactory(requests), twilio, json);
	}

}
