package com.schoollink.notification.internal;

/**
 * What a provider says about one send (BR-24). {@code error} is the provider's reason, written to
 * {@code last_error}: never a phone number or a device token (NFR-9).
 */
sealed interface SendResult {

	record Sent() implements SendResult {
	}

	/** A failure that may pass: a network error, a timeout, a rate limit. Retried with backoff. */
	record Retry(String error) implements SendResult {
	}

	/** A failure that will not pass: an unknown number, no device registered. Never retried. */
	record Rejected(String error) implements SendResult {
	}

	static SendResult sent() {
		return new Sent();
	}

}
