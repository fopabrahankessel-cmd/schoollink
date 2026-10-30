package com.schoollink.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in until Firebase (task 2.7): logs the push and reports it sent. Nothing reaches a phone.
 * Logs ids only, never the text.
 */
@Component
class LoggingPushProvider implements PushProvider {

	private static final Logger log = LoggerFactory.getLogger(LoggingPushProvider.class);

	LoggingPushProvider() {
		log.warn("No push provider configured: push notifications are logged, not delivered");
	}

	@Override
	public SendResult send(PushMessage message) {
		log.info("Push (not delivered): notification {}, user {}", message.notificationId(), message.userId());
		return SendResult.sent();
	}

}
