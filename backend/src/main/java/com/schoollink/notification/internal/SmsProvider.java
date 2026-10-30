package com.schoollink.notification.internal;

import java.util.UUID;

/**
 * Sends one SMS (BR-22, BR-23). A real gateway adapter comes with task 3.7; until then
 * {@link LoggingSmsProvider} stands in.
 */
interface SmsProvider {

	/** NFR-9: the phone and the text (it may hold an invitation code) never appear in logs. */
	record SmsMessage(UUID notificationId, String phone, String text) {

		@Override
		public String toString() {
			return "SmsMessage[notificationId=" + notificationId + ", phone=***, text=" + text.length() + " chars]";
		}

	}

	SendResult send(SmsMessage message);

}
