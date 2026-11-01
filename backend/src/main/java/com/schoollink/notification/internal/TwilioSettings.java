package com.schoollink.notification.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Twilio account (task 3.7, D32), from the environment, never committed: {@code TWILIO_ACCOUNT_SID},
 * {@code TWILIO_AUTH_TOKEN}, {@code TWILIO_FROM} (the sender: a number or an approved sender ID) and
 * {@code SMS_STATUS_CALLBACK_URL} (the public address of the delivery webhook, exactly as Twilio
 * calls it: the signature covers it).
 *
 * @param baseUrl Twilio's API, replaced in tests only
 */
@ConfigurationProperties("schoollink.sms.twilio")
record TwilioSettings(String accountSid, String authToken, String from, String statusCallback, String baseUrl) {

	TwilioSettings {
		accountSid = accountSid == null ? "" : accountSid.strip();
		authToken = authToken == null ? "" : authToken.strip();
		from = from == null ? "" : from.strip();
		statusCallback = statusCallback == null ? "" : statusCallback.strip();
		baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.twilio.com" : baseUrl.strip();
	}

	boolean configured() {
		return !accountSid.isEmpty();
	}

	/** NFR-9: the auth token never appears in logs. */
	@Override
	public String toString() {
		return "TwilioSettings[accountSid=" + accountSid + ", authToken=***, from=" + from + ", statusCallback="
				+ statusCallback + "]";
	}

}
