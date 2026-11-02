package com.schoollink.reporting.internal;

import com.schoollink.shared.error.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** A range of school dates, both inclusive, as the history and the dashboards take it. */
record DateRange(LocalDate from, LocalDate to) {

	/** At most a school year and a bit. */
	static final int MAX_DAYS = 366;

	/**
	 * {@code to} defaults to {@code today}, {@code from} to {@code defaultDays} before {@code to}.
	 * A range backwards or longer than {@link #MAX_DAYS} is refused.
	 */
	static DateRange of(LocalDate from, LocalDate to, LocalDate today, int defaultDays) {
		LocalDate end = to != null ? to : today;
		LocalDate start = from != null ? from : end.minusDays(defaultDays);
		if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
			throw ApiException.invalid("history.range_invalid", "from", MAX_DAYS);
		}
		return new DateRange(start, end);
	}

	/** The first instant of {@code from} in the zone. */
	Instant start(ZoneId zone) {
		return from.atStartOfDay(zone).toInstant();
	}

	/** The first instant after {@code to} in the zone. */
	Instant end(ZoneId zone) {
		return to.plusDays(1).atStartOfDay(zone).toInstant();
	}

}
