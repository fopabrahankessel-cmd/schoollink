package com.schoollink.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * In-memory rate limiter for login and parent activation (NFR-7). One backend instance serves
 * the pilot (modular monolith), so the buckets live in memory and reset on restart. Keys may
 * contain phone numbers: they stay in memory and are never logged (NFR-9).
 *
 * <p>Every change to a bucket happens under that bucket's lock. A bucket removed from the map
 * (reset or cleanup) is marked dead first, so a request still holding it retries on a fresh one
 * instead of recording its attempt on a bucket nobody sees.
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

	/**
	 * Spends one attempt if one is left, as a single locked check-and-spend, so concurrent
	 * requests can never get more attempts than the bucket holds. Returns {@link Duration#ZERO}
	 * if allowed, else how long to wait.
	 */
	public Duration tryConsume(RateLimitPolicy policy, String key) {
		return update(policy, key, bucket -> {
			double tokens = bucket.refill(policy, clock.instant());
			if (tokens >= 1 - EPSILON) {
				bucket.tokens = Math.max(0, tokens - 1);
				return Duration.ZERO;
			}
			return wait(policy, tokens);
		});
	}

	/** Spends one attempt unconditionally. */
	public void consume(RateLimitPolicy policy, String key) {
		update(policy, key, bucket -> bucket.tokens = Math.max(0, bucket.refill(policy, clock.instant()) - 1));
	}

	/** Gives back an attempt taken with {@link #tryConsume}, e.g. when it turned out to be a success. */
	public void refund(RateLimitPolicy policy, String key) {
		update(policy, key, bucket -> bucket.tokens = Math.min(policy.capacity(), bucket.refill(policy, clock.instant()) + 1));
	}

	/** How long until one attempt is available; {@link Duration#ZERO} if one is available now. */
	public Duration retryAfter(RateLimitPolicy policy, String key) {
		Bucket bucket = buckets.get(id(policy, key));
		if (bucket == null) {
			return Duration.ZERO;
		}
		synchronized (bucket) {
			double tokens = bucket.refill(policy, clock.instant());
			return tokens >= 1 - EPSILON ? Duration.ZERO : wait(policy, tokens);
		}
	}

	/** Forgets a key, e.g. after a successful login. */
	public void reset(RateLimitPolicy policy, String key) {
		String id = id(policy, key);
		Bucket bucket = buckets.get(id);
		if (bucket != null) {
			synchronized (bucket) {
				bucket.dead = true;
				buckets.remove(id, bucket);
			}
		}
	}

	/** Forgets everything; for tests. */
	public void clear() {
		buckets.keySet().forEach(id -> {
			Bucket bucket = buckets.get(id);
			if (bucket != null) {
				synchronized (bucket) {
					bucket.dead = true;
					buckets.remove(id, bucket);
				}
			}
		});
	}

	private <T> T update(RateLimitPolicy policy, String key, Function<Bucket, T> change) {
		String id = id(policy, key);
		while (true) {
			Instant now = clock.instant();
			if (buckets.size() > CLEANUP_THRESHOLD) {
				removeFullBuckets(policy, now);
			}
			Bucket bucket = buckets.computeIfAbsent(id, k -> new Bucket(policy.capacity(), now));
			synchronized (bucket) {
				if (!bucket.dead) {
					return change.apply(bucket);
				}
			}
		}
	}

	/** Whole milliseconds, ignoring floating-point leftovers (1 min, not 1 min + 1 ns). */
	private static Duration wait(RateLimitPolicy policy, double tokens) {
		double millis = (1 - tokens) / policy.refillPerNano() / 1_000_000;
		return Duration.ofMillis((long) Math.ceil(millis - EPSILON));
	}

	/** Full buckets carry no information, so dropping them keeps memory bounded. */
	private void removeFullBuckets(RateLimitPolicy policy, Instant now) {
		String prefix = policy.name() + "|";
		buckets.forEach((id, bucket) -> {
			if (id.startsWith(prefix)) {
				synchronized (bucket) {
					if (!bucket.dead && bucket.refill(policy, now) >= policy.capacity()) {
						bucket.dead = true;
						buckets.remove(id, bucket);
					}
				}
			}
		});
	}

	private static String id(RateLimitPolicy policy, String key) {
		return policy.name() + "|" + key;
	}

	/** Guarded by its own monitor. */
	private static final class Bucket {

		double tokens;

		Instant updatedAt;

		boolean dead;

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

	}

}
