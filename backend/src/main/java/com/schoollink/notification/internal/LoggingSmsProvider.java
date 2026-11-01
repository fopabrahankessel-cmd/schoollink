package com.schoollink.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Stands in for the gateway in dev and tests ({@link SmsConfig}): logs the SMS and reports it sent. Nothing reaches a
 * phone. NFR-9: never logs the phone; logs the text (invitation and one-time codes) only with the
 * dev profile, so a developer can activate a fake parent, and only its length otherwise.
 */
class LoggingSmsProvider implements SmsProvider {

	private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

	private final boolean showText;

	LoggingSmsProvider(Environment environment) {
		this.showText = environment.acceptsProfiles(Profiles.of("dev"));
		log.warn("No SMS gateway configured: SMS are logged, not delivered");
	}

	@Override
	public SendResult send(SmsMessage message) {
		if (showText) {
			log.info("SMS (not delivered, dev): notification {}: {}", message.notificationId(), message.text());
		}
		else {
			log.info("SMS (not delivered): notification {}, {} characters", message.notificationId(),
					message.text().length());
		}
		return SendResult.sent();
	}

}
