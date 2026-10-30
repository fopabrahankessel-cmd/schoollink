package com.schoollink.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-in for Firebase in the dev and test profiles, when no service account is configured
 * ({@link PushConfig}): logs the push and reports it sent. Nothing reaches a phone. Logs ids only,
 * never the text.
 */
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
