package com.schoollink.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in until a real gateway (task 3.7): logs the SMS and reports it sent. Nothing reaches a
 * phone. NFR-9: logs the notification id only, never the phone or the text.
 */
@Component
class LoggingSmsProvider implements SmsProvider {

	private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

	LoggingSmsProvider() {
		log.warn("No SMS gateway configured: SMS are logged, not delivered");
	}

	@Override
	public SendResult send(SmsMessage message) {
		log.info("SMS (not delivered): notification {}, {} characters", message.notificationId(), message.text().length());
		return SendResult.sent();
	}

}
