package com.schoollink.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A clock that tests can set, for rules that depend on the time of day. It follows the real time
 * until {@link #set} is called, and again after {@link #reset}. Import {@link Config} to replace
 * the application clock with it.
 */
public final class TestClock extends Clock {

	private volatile Instant fixed;

	public void set(Instant instant) {
		fixed = instant;
	}

	public void reset() {
		fixed = null;
	}

	@Override
	public Instant instant() {
		Instant instant = fixed;
		return instant == null ? Instant.now() : instant;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("read the instant and convert it with the school's zone");
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}

	}

}
