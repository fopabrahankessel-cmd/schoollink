package com.schoollink.notification.internal;

import com.schoollink.notification.internal.SendResult.Rejected;
import com.schoollink.notification.internal.SendResult.Retry;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends an SMS through Twilio's Messages API (task 3.7, D32). The status callback brings the
 * delivery report to {@code POST /api/v1/webhooks/sms/delivery} (BR-24).
 * <p>
 * Outcomes: accepted, {@code Sent} with the message SID; a request Twilio refuses (an invalid or
 * unreachable number, a STOP from the recipient), {@code Rejected}, never retried; a network error,
 * a rate limit, a server error or refused credentials, {@code Retry} with backoff. NFR-9: Twilio's
 * error messages quote the phone number, so only its numeric error code is kept.
 */
class TwilioSmsProvider implements SmsProvider {

	private final RestClient client;

	private final TwilioSettings settings;

	private final JsonMapper json;

	TwilioSmsProvider(RestClient.Builder builder, TwilioSettings settings, JsonMapper json) {
		String credentials = Base64.getEncoder()
			.encodeToString((settings.accountSid() + ":" + settings.authToken()).getBytes(StandardCharsets.UTF_8));
		this.client = builder.baseUrl(settings.baseUrl()).defaultHeader("Authorization", "Basic " + credentials).build();
		this.settings = settings;
		this.json = json;
	}

	@Override
	public SendResult send(SmsMessage message) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("To", message.phone());
		form.add("From", settings.from());
		form.add("Body", message.text());
		if (!settings.statusCallback().isEmpty()) {
			form.add("StatusCallback", settings.statusCallback());
		}
		try {
			String body = client.post()
				.uri("/2010-04-01/Accounts/{account}/Messages.json", settings.accountSid())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.accept(MediaType.APPLICATION_JSON)
				.body(form)
				.retrieve()
				.body(String.class);
			JsonNode sid = body == null ? null : json.readTree(body).get("sid");
			return SendResult.sent(sid == null ? null : sid.asString());
		}
		catch (RestClientResponseException ex) {
			return outcome(ex.getStatusCode(), ex.getResponseBodyAsString());
		}
		catch (ResourceAccessException ex) {
			return new Retry("twilio unreachable");
		}
	}

	private SendResult outcome(HttpStatusCode status, String body) {
		int code = errorCode(body);
		String error = "twilio " + status.value() + (code > 0 ? " error " + code : "");
		if (status.value() == 429 || status.is5xxServerError() || status.value() == 401 || status.value() == 403) {
			// passing, or a configuration to fix: retried, then FAILED after the last attempt
			return new Retry(error);
		}
		return new Rejected(error);
	}

	/** Twilio's numeric error code, from its JSON error body; 0 when there is none. */
	private int errorCode(String body) {
		try {
			JsonNode code = json.readTree(body).get("code");
			return code == null ? 0 : code.asInt();
		}
		catch (RuntimeException ex) {
			return 0;
		}
	}

}
