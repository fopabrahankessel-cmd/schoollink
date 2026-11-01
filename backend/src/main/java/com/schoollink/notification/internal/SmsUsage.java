package com.schoollink.notification.internal;

import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.Schools;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * BR-25, NFR-14: a school's SMS counter and its monthly cap. The counter is the outbox SMS sent
 * since the first of the month in the school's time zone, a digest counting once. The one-time
 * sign-in codes (UC-P1) go outside the outbox and are neither counted nor capped (D33).
 */
@Component
class SmsUsage {

	private final NamedParameterJdbcTemplate jdbc;

	private final Schools schools;

	private final SchoolCalendar calendar;

	private final Clock clock;

	SmsUsage(NamedParameterJdbcTemplate jdbc, Schools schools, SchoolCalendar calendar, Clock clock) {
		this.jdbc = jdbc;
		this.schools = schools;
		this.calendar = calendar;
		this.clock = clock;
	}

	int sentThisMonth(UUID schoolId) {
		ZoneId zone = calendar.zone(schoolId);
		ZonedDateTime start = clock.instant().atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone);
		return jdbc.queryForObject("""
				SELECT count(*) FROM notification_outbox
				WHERE school_id = :school AND channel = 'SMS' AND sent_at >= :start AND sent_at < :end""",
				new MapSqlParameterSource("school", schoolId).addValue("start", Timestamp.from(start.toInstant()))
					.addValue("end", Timestamp.from(start.plusMonths(1).toInstant())),
				Integer.class);
	}

	/** BR-25: at the cap, only summons go by SMS (D12). */
	boolean capReached(UUID schoolId) {
		return sentThisMonth(schoolId) >= schools.smsMonthlyCap(schoolId);
	}

}
