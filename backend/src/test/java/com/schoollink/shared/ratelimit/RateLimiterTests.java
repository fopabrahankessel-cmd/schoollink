package com.schoollink.shared.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Token buckets behind the login and activation limits (NFR-7). */
class RateLimiterTests {

	static final RateLimitPolicy FIVE_PER_15_MIN = new RateLimitPolicy("test", 5, Duration.ofMinutes(15));

	private final MutableClock clock = new MutableClock();

	private final RateLimiter limiter = new RateLimiter(clock);

	@Test
	void allowsCapacityThenAsksToWait() {
		for (int i = 0; i < 5; i++) {
			assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isZero();
		}

		// one token every 3 minutes
		assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isEqualTo(Duration.ofMinutes(3));
	}

	@Test
	void refillsOverTime() {
		for (int i = 0; i < 5; i++) {
			limiter.consume(FIVE_PER_15_MIN, "k");
		}
		clock.advance(Duration.ofMinutes(2));
		assertThat(limiter.retryAfter(FIVE_PER_15_MIN, "k")).isEqualTo(Duration.ofMinutes(1));

		clock.advance(Duration.ofMinutes(1));
		assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isZero();
		assertThat(limiter.retryAfter(FIVE_PER_15_MIN, "k")).isEqualTo(Duration.ofMinutes(3));
	}

	@Test
	void neverRefillsBeyondCapacity() {
		limiter.consume(FIVE_PER_15_MIN, "k");
		clock.advance(Duration.ofDays(1));

		for (int i = 0; i < 5; i++) {
			assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isZero();
		}
		assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isPositive();
	}

	@Test
	void retryAfterDoesNotSpendAnAttempt() {
		for (int i = 0; i < 10; i++) {
			assertThat(limiter.retryAfter(FIVE_PER_15_MIN, "k")).isZero();
		}
	}

	@Test
	void keysAndPoliciesAreIndependent() {
		RateLimitPolicy other = new RateLimitPolicy("other", 1, Duration.ofMinutes(1));
		for (int i = 0; i < 5; i++) {
			limiter.consume(FIVE_PER_15_MIN, "a");
		}

		assertThat(limiter.retryAfter(FIVE_PER_15_MIN, "a")).isPositive();
		assertThat(limiter.retryAfter(FIVE_PER_15_MIN, "b")).isZero();
		assertThat(limiter.retryAfter(other, "a")).isZero();
	}

	@Test
	void concurrentRequestsNeverGetMoreAttemptsThanTheBucketHolds() throws Exception {
		int threads = 50;
		var start = new CountDownLatch(1);
		var allowed = new AtomicInteger();
		try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
			for (int i = 0; i < threads; i++) {
				pool.submit(() -> {
					start.await();
					if (limiter.tryConsume(FIVE_PER_15_MIN, "k").isZero()) {
						allowed.incrementAndGet();
					}
					return null;
				});
			}
			start.countDown();
		}

		assertThat(allowed).hasValue(5);
	}

	@Test
	void refundGivesBackOneAttempt() {
		for (int i = 0; i < 5; i++) {
			limiter.tryConsume(FIVE_PER_15_MIN, "k");
		}

		limiter.refund(FIVE_PER_15_MIN, "k");

		assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isZero();
		assertThat(limiter.tryConsume(FIVE_PER_15_MIN, "k")).isPositive();
	}

	@Test
	void resetForgetsAKey() {
		for (int i = 0; i < 5; i++) {
			limiter.consume(FIVE_PER_15_MIN, "k");
		}

		limiter.reset(FIVE_PER_15_MIN, "k");

		assertThat(limiter.retryAfter(FIVE_PER_15_MIN, "k")).isZero();
	}

	static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-28T07:30:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
