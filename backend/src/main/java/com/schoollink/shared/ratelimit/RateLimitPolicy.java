package com.schoollink.shared.ratelimit;

import java.time.Duration;

/**
 * A token bucket: up to {@code capacity} attempts, refilled evenly over {@code period}
 * (5 per 15 min = one more attempt every 3 min).
 */
public record RateLimitPolicy(String name, int capacity, Duration period) {

	double refillPerNano() {
		return capacity / (double) period.toNanos();
	}

}
