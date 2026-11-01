package com.schoollink.notification.internal;

import java.util.UUID;

/**
 * Sends one SMS (BR-22, BR-23): {@link TwilioSmsProvider}, or {@link LoggingSmsProvider} in dev and
 * tests without a gateway (see {@link SmsConfig}). A sent SMS carries the gateway's message id, which
 * its delivery report quotes (BR-24).
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
