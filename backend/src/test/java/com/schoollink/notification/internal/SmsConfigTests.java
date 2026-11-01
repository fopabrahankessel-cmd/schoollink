package com.schoollink.notification.internal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Without a Twilio account, only dev and test may start, with SMS logged (task 3.7, BR-24, D32). */
class SmsConfigTests {

	static final JsonMapper JSON = JsonMapper.builder().build();

	static final String CALLBACK = "https://api.example.org/api/v1/webhooks/sms/delivery";

	@Test
	void devAndTestLogSmsWithoutAnAccount() {
		for (String profile : new String[] { "dev", "test" }) {
			MockEnvironment environment = new MockEnvironment();
			environment.setActiveProfiles(profile);
			assertThat(new SmsConfig().smsProvider(settings("", "", "", ""), JSON, environment))
				.isInstanceOf(LoggingSmsProvider.class);
		}
	}

	@Test
	void anyOtherProfileRefusesToStartWithoutAnAccount() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");

		assertThatThrownBy(() -> new SmsConfig().smsProvider(settings(" ", "", "", ""), JSON, environment))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TWILIO_ACCOUNT_SID");
	}

	@Test
	void anAccountWithoutTokenSenderOrCallbackStopsTheStart() { // D32: never send unverifiable SMS
		MockEnvironment environment = new MockEnvironment();
		for (TwilioSettings incomplete : new TwilioSettings[] { settings("ACfake", "", "SchoolLink", CALLBACK),
				settings("ACfake", "fake-token", "", CALLBACK), settings("ACfake", "fake-token", "SchoolLink", "") }) {
			assertThatThrownBy(() -> new SmsConfig().smsProvider(incomplete, JSON, environment))
				.isInstanceOf(IllegalStateException.class);
		}
	}

	@Test
	void aCompleteAccountSendsThroughTwilio() {
		assertThat(new SmsConfig().smsProvider(settings("ACfake", "fake-token", "SchoolLink", CALLBACK), JSON,
				new MockEnvironment()))
			.isInstanceOf(TwilioSmsProvider.class);
	}

	private static TwilioSettings settings(String account, String token, String from, String callback) {
		return new TwilioSettings(account, token, from, callback, null);
	}

}
