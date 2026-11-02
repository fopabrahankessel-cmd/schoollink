package com.schoollink.detection;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Risk alerts counted per rule, for the dashboards (detection module API, UC-A6, UC-D8). Within
 * {@code schoolId} only (NFR-8).
 */
public interface AlertCounts {

	/**
	 * One row per rule, in {@link RuleType} order, for the alerts of the given classes (the class
	 * the alert was raised in, BR-18): open now, raised and closed between two instants ({@code to}
	 * excluded).
	 */
	List<RuleCount> byRule(UUID schoolId, Set<UUID> classIds, Instant from, Instant to);

	record RuleCount(RuleType rule, int open, int raised, int closed) {
	}

}
