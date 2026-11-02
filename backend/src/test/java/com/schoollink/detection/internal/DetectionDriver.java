package com.schoollink.detection.internal;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The detection module's nightly run (BR-17), for tests outside it (task 3.13): the test profile
 * does not schedule it. Import {@link Config}.
 */
public final class DetectionDriver {

	private final Detection detection;

	private final DetectionListeners listeners;

	DetectionDriver(Detection detection, DetectionListeners listeners) {
		this.detection = detection;
		this.listeners = listeners;
	}

	/** Waits for the runs after each commit (BR-17), which go on the detection executor. */
	public void awaitRules() throws InterruptedException {
		if (!listeners.awaitIdle(Duration.ofSeconds(30))) {
			throw new AssertionError("detection still running after 30 s");
		}
	}

	/** What {@link NightlyDetection} does for one school at 20:15; returns the alerts raised. */
	public int nightly(UUID school, LocalDate date) {
		return detection.evaluateSchool(school, date);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		DetectionDriver detectionDriver(Detection detection, DetectionListeners listeners) {
			return new DetectionDriver(detection, listeners);
		}

	}

}
