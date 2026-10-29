package com.schoollink.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiter for login and parent activation (NFR-7). One backend instance serves
 * the pilot (modular monolith), so the buckets live in memory and reset on restart. Keys may
 * contain phone numbers: they stay in memory and are never logged (NFR-9).
 */
@Component
public class RateLimiter {

	private static final int CLEANUP_THRESHOLD = 10_000;

	private static final double EPSILON = 1e-6;

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	private final Clock clock;

	public RateLimiter(Clock clock) {
		this.clock = clock;
	}

	/** Spends one attempt if one is left; returns {@link Duration#ZERO} if allowed, else how long to wait. */
	public Duration tryConsume(RateLimitPolicy policy, String key) {
		Duration wait = retryAfter(policy, key);
		if (wait.isZero()) {
			consume(policy, key);
		}
		return wait;
	}

	/** How long until one attempt is available; {@link Duration#ZERO} if one is available now. */
	public Duration retryAfter(RateLimitPolicy policy, String key) {
		Bucket bucket = buckets.get(id(policy, key));
		if (bucket == null) {
			return Duration.ZERO;
		}
		synchronized (bucket) {
			double tokens = bucket.refill(policy, clock.instant());
			if (tokens >= 1 - EPSILON) {
				return Duration.ZERO;
			}
			// whole milliseconds, ignoring floating-point leftovers (1 min, not 1 min + 1 ns)
			double millis = (1 - tokens) / policy.refillPerNano() / 1_000_000;
			return Duration.ofMillis((long) Math.ceil(millis - EPSILON));
		}
	}

	/** Spends one attempt unconditionally (e.g. a failed login). */
	public void consume(RateLimitPolicy policy, String key) {
		Instant now = clock.instant();
		if (buckets.size() > CLEANUP_THRESHOLD) {
			removeFullBuckets(policy, now);
		}
		Bucket bucket = buckets.computeIfAbsent(id(policy, key), k -> new Bucket(policy.capacity(), now));
		synchronized (bucket) {
			bucket.tokens = Math.max(0, bucket.refill(policy, now) - 1);
		}
	}

	/** Forgets a key, e.g. after a successful login. */
	public void reset(RateLimitPolicy policy, String key) {
		buckets.remove(id(policy, key));
	}

	/** Forgets everything; for tests. */
	public void clear() {
		buckets.clear();
	}

	private void removeFullBuckets(RateLimitPolicy policy, Instant now) {
		buckets.entrySet().removeIf(e -> e.getKey().startsWith(policy.name() + "|") && e.getValue().isFull(policy, now));
	}

	private static String id(RateLimitPolicy policy, String key) {
		return policy.name() + "|" + key;
	}

	private static final class Bucket {

		double tokens;

		Instant updatedAt;

		Bucket(double tokens, Instant updatedAt) {
			this.tokens = tokens;
			this.updatedAt = updatedAt;
		}

		double refill(RateLimitPolicy policy, Instant now) {
			long elapsed = Math.max(0, Duration.between(updatedAt, now).toNanos());
			tokens = Math.min(policy.capacity(), tokens + elapsed * policy.refillPerNano());
			updatedAt = now;
			return tokens;
		}

		synchronized boolean isFull(RateLimitPolicy policy, Instant now) {
			return refill(policy, now) >= policy.capacity();
		}

	}

}
