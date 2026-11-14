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

	/** NFR-6: whether sending works right now, whatever the range of the dashboard. */
	Sending sending(UUID schoolId);

	/**
	 * @param failedLastHour sends that failed for good in the last hour (BR-24)
	 * @param overdue sends due for more than {@code OVERDUE_AFTER} and still not sent: the
	 * dispatcher is stopped or cannot keep up. A send waiting for its next try is not overdue.
	 * @param lastDigestAt the last run of the SMS digest (BR-22); null when it never ran
	 */
	record Sending(int failedLastHour, int overdue, Instant lastDigestAt) {
	}

	/** {@code pending}: queued or being sent. */
	record ChannelDelivery(int pending, int sent, int delivered, int opened, int failed) {
	}

	record Delivery(ChannelDelivery push, ChannelDelivery sms) {
	}

	record SmsCounter(int sentThisMonth, int monthlyCap) {
	}

}
