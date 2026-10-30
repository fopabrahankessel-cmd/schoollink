package com.schoollink.notification;

import java.time.Duration;
import java.util.UUID;

/**
 * UC-P1: the activation one-time code goes by SMS at once, through the SMS provider and not the
 * outbox: it is useless after a few minutes and must never sit in a table (notification module
 * API; PLAN, open questions). It is counted towards the school's SMS use but never blocked by the
 * monthly cap (BR-25, task 3.x).
 */
public interface ActivationSms {

	/**
	 * Sends the code, in the guardian's language. Returns whether the provider accepted it.
	 * NFR-9: neither the phone nor the code is logged.
	 *
	 * @param messageId an id to follow the send in the logs
	 */
	boolean send(UUID schoolId, UUID messageId, String phone, String language, String code, Duration validity);

}
