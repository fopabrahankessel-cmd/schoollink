package com.schoollink.notification.internal;

import java.util.UUID;

/**
 * Sends one push notification to every device of an app user (BR-21). The Firebase implementation
 * comes with task 2.7; until then {@link LoggingPushProvider} stands in.
 */
interface PushProvider {

	/** {@code notificationId} travels with the push, so the app can send the opened receipt (UC-P2). */
	record PushMessage(UUID notificationId, UUID userId, String title, String body) {
	}

	SendResult send(PushMessage message);

}
