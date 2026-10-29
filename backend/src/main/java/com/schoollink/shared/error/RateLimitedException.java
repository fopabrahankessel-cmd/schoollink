package com.schoollink.shared.error;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/**
 * 429 Too Many Requests (NFR-7). The response carries a {@code Retry-After} header in seconds and
 * a message giving the wait in whole minutes.
 */
public class RateLimitedException extends ApiException {

	private final Duration retryAfter;

	public RateLimitedException(Duration retryAfter) {
		super(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", null, Math.max(1, (retryAfter.toSeconds() + 59) / 60));
		this.retryAfter = retryAfter;
	}

	public long retryAfterSeconds() {
		return Math.max(1, (retryAfter.toMillis() + 999) / 1000);
	}

}
