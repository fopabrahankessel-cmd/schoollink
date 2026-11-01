package com.schoollink.notification.internal;

import com.schoollink.notification.internal.SendResult.Rejected;
import com.schoollink.notification.internal.SendResult.Retry;
import com.schoollink.notification.internal.SendResult.Sent;
import com.schoollink.notification.internal.SmsProvider.SmsMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * The Twilio SMS provider against Twilio's Messages API contract (task 3.7, BR-24, D32, NFR-9),
 * with recorded responses in {@code src/test/resources/twilio/}. Account, token and numbers are fake.
 */
class TwilioSmsProviderTests {

	static final String ACCOUNT = "ACfake00000000000000000000000000";

	static final String TOKEN = "fake-auth-token";

	static final String CALLBACK = "https://api.example.org/api/v1/webhooks/sms/delivery";

	static final String PHONE = "+237690000202";

	static final String MESSAGES = "https://api.twilio.test/2010-04-01/Accounts/" + ACCOUNT + "/Messages.json";

	MockRestServiceServer twilio;

	TwilioSmsProvider provider;

	@BeforeEach
	void bind() {
		RestClient.Builder builder = RestClient.builder();
		twilio = MockRestServiceServer.bindTo(builder).build();
		provider = new TwilioSmsProvider(builder,
				new TwilioSettings(ACCOUNT, TOKEN, "SchoolLink", CALLBACK, "https://api.twilio.test"),
				JsonMapper.builder().build());
	}

	@Test
	void anAcceptedSmsIsSentWithItsSid() { // BR-24, D32
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("To", PHONE);
		form.add("From", "SchoolLink");
		form.add("Body", "Fake test message");
		form.add("StatusCallback", CALLBACK);
		twilio.expect(requestTo(MESSAGES))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Basic " + Base64.getEncoder()
				.encodeToString((ACCOUNT + ":" + TOKEN).getBytes(StandardCharsets.UTF_8))))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().formData(form))
			.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
				.body(recorded("message-created.json")));

		SendResult result = provider.send(sms());

		assertThat(result).isEqualTo(new Sent("SMfake0000000000000000000000000001"));
		twilio.verify();
	}

	@Test
	void anInvalidNumberIsRejectedWithOnlyTheCode() { // BR-24: never retried; NFR-9: no phone kept
		twilio.expect(requestTo(MESSAGES))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body(recorded("error-invalid-to.json")));

		SendResult result = provider.send(sms());

		assertThat(result).isEqualTo(new Rejected("twilio 400 error 21211"));
		assertThat(result.toString()).doesNotContain("690000202");
	}

	@Test
	void aRateLimitIsRetried() { // BR-24
		twilio.expect(requestTo(MESSAGES))
			.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
				.body(recorded("error-rate-limit.json")));

		assertThat(provider.send(sms())).isEqualTo(new Retry("twilio 429 error 20429"));
	}

	@Test
	void aServerErrorWithoutABodyIsRetried() { // BR-24
		twilio.expect(requestTo(MESSAGES)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

		assertThat(provider.send(sms())).isEqualTo(new Retry("twilio 502"));
	}

	@Test
	void refusedCredentialsAreRetriedUntilFixed() { // BR-24: a configuration error, not the guardian's
		twilio.expect(requestTo(MESSAGES)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
			.contentType(MediaType.APPLICATION_JSON).body("{\"code\": 20003, \"message\": \"Authenticate\"}"));

		assertThat(provider.send(sms())).isEqualTo(new Retry("twilio 401 error 20003"));
	}

	@Test
	void anUnreachableGatewayIsRetried() { // BR-24
		twilio.expect(requestTo(MESSAGES)).andRespond(withException(new IOException("connect timed out")));

		assertThat(provider.send(sms())).isEqualTo(new Retry("twilio unreachable"));
	}

	@Test
	void theAuthTokenNeverShowsInTheSettings() { // NFR-9
		TwilioSettings settings = new TwilioSettings(ACCOUNT, TOKEN, "SchoolLink", CALLBACK, null);

		assertThat(settings.baseUrl()).isEqualTo("https://api.twilio.com");
		assertThat(settings.toString()).doesNotContain(TOKEN);
	}

	@Test
	void theSignatureMatchesTwilioDocumentedExample() { // D32: the worked example in Twilio's security docs
		Map<String, String[]> params = Map.of("CallSid", new String[] { "CA1234567890ABCDE" }, "Caller",
				new String[] { "+12349013030" }, "Digits", new String[] { "1234" }, "From",
				new String[] { "+12349013030" }, "To", new String[] { "+18005551212" });
		String url = "https://mycompany.com/myapp.php?foo=1&bar=2";

		assertThat(TwilioSignature.of("12345", url, params)).isEqualTo("0/KCTR6DLpKmkAf8muzZqo1nDgQ=");
		assertThat(TwilioSignature.valid("12345", url, params, "0/KCTR6DLpKmkAf8muzZqo1nDgQ=")).isTrue();
		assertThat(TwilioSignature.valid("12345", url, params, "1/KCTR6DLpKmkAf8muzZqo1nDgQ=")).isFalse();
		assertThat(TwilioSignature.valid("", url, params, "0/KCTR6DLpKmkAf8muzZqo1nDgQ=")).isFalse();
		assertThat(TwilioSignature.valid("12345", url, params, null)).isFalse();
	}

	private static SmsMessage sms() {
		return new SmsMessage(UUID.randomUUID(), PHONE, "Fake test message");
	}

	private static String recorded(String name) {
		try {
			return new ClassPathResource("twilio/" + name).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
