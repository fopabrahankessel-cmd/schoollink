package com.schoollink.detection.internal;

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

	DetectionDriver(Detection detection) {
		this.detection = detection;
	}

	/** What {@link NightlyDetection} does for one school at 20:15; returns the alerts raised. */
	public int nightly(UUID school, LocalDate date) {
		return detection.evaluateSchool(school, date);
	}

	@TestConfiguration(proxyBeanMethods = false)
	public static class Config {

		@Bean
		DetectionDriver detectionDriver(Detection detection) {
			return new DetectionDriver(detection);
		}

	}

}
