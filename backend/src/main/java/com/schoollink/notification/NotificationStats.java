package com.schoollink.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery and SMS usage, for the school dashboard (notification module API, UC-A6, BR-24, BR-25,
 * NFR-14). Within {@code schoolId} only (NFR-8).
 */
public interface NotificationStats {

	/**
	 * The outbox sends created between two instants ({@code to} excluded), by channel and status.
	 * Rows that are not sends are left out: an SMS held for the digest, folded into one or skipped
	 * (D33), and a notification withdrawn by a cancellation.
	 */
	Delivery delivery(UUID schoolId, Instant from, Instant to);

	/** BR-25: the SMS sent this month, in the school's time zone, and the monthly cap (D33). */
	SmsCounter smsCounter(UUID schoolId);

	/** {@code pending}: queued or being sent. */
	record ChannelDelivery(int pending, int sent, int delivered, int opened, int failed) {
	}

	record Delivery(ChannelDelivery push, ChannelDelivery sms) {
	}

	record SmsCounter(int sentThisMonth, int monthlyCap) {
	}

}
