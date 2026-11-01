package com.schoollink.discipline.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.discipline.Summonses;
import com.schoollink.discipline.SummonsIssued;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.shared.error.ApiException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR-23, D12, D31: a summons is stored, audited and published in the caller's transaction; the
 * notification module sends it at once. The meeting must be on a school day, from now to
 * {@link #MAX_DAYS_AHEAD} days ahead, during the day.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JdbcSummonses implements Summonses {

	static final int MAX_DAYS_AHEAD = 30;

	static final int MAX_REASON = 300;

	static final LocalTime EARLIEST = LocalTime.of(6, 0);

	static final LocalTime LATEST = LocalTime.of(18, 0);

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolCalendar calendar;

	private final AuditLog audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	JdbcSummonses(NamedParameterJdbcTemplate jdbc, SchoolCalendar calendar, AuditLog audit,
			ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.calendar = calendar;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Override
	public UUID issue(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, LocalDate meetingDate,
			LocalTime meetingTime, String reason) {
		String why = reason == null || reason.isBlank() ? null : reason.strip();
		if (why == null && alertId == null) {
			// the alert says why; a summons on its own must
			throw ApiException.invalid("summons.reason_required", "reason");
		}
		if (why != null && why.length() > MAX_REASON) {
			throw ApiException.invalid("summons.reason_too_long", "reason", MAX_REASON);
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		ZoneId zone = calendar.zone(schoolId);
		LocalDate today = now.atZone(zone).toLocalDate();
		// D31, UC-D3: one tap gives the next school day at the start of school
		LocalDate date = meetingDate != null ? meetingDate : nextSchoolDay(schoolId, today);
		LocalTime time = meetingTime != null ? meetingTime : calendar.startTime(schoolId);
		if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))
				|| !LocalDateTime.of(date, time).atZone(zone).toInstant().isAfter(now)) {
			throw ApiException.invalid("summons.meeting_date", "meetingDate", MAX_DAYS_AHEAD);
		}
		if (!calendar.isSchoolDay(schoolId, date)) {
			throw ApiException.invalid("summons.not_a_school_day", "meetingDate");
		}
		if (time.isBefore(EARLIEST) || time.isAfter(LATEST)) {
			throw ApiException.invalid("summons.meeting_time", "meetingTime", EARLIEST, LATEST);
		}
		LocalTime minute = time.truncatedTo(ChronoUnit.MINUTES);

		UUID id = jdbc.queryForObject("""
				INSERT INTO summons (school_id, student_id, alert_id, reason, meeting_date, meeting_time, sent_by, sent_at)
				VALUES (:school, :student, :alert, :reason, :date, :time, :by, :now) RETURNING id""",
				new MapSqlParameterSource("school", schoolId).addValue("student", studentId)
					.addValue("alert", alertId)
					.addValue("reason", why)
					.addValue("date", date)
					.addValue("time", Time.valueOf(minute))
					.addValue("by", actorId)
					.addValue("now", Timestamp.from(now)),
				UUID.class);
		Map<String, Object> after = new HashMap<>();
		after.put("studentId", studentId.toString());
		after.put("alertId", alertId == null ? null : alertId.toString());
		after.put("reason", why);
		after.put("meetingDate", date.toString());
		after.put("meetingTime", minute.toString());
		audit.record(new AuditEntry(schoolId, actorId, AuditAction.CREATE, "summons", id.toString(), null, after));
		events.publishEvent(new SummonsIssued(id, schoolId, studentId, alertId, date, minute, actorId, now));
		return id;
	}

	/** The first school day after today, or today's date plus one when none comes soon (refused next). */
	private LocalDate nextSchoolDay(UUID schoolId, LocalDate today) {
		for (int i = 1; i <= MAX_DAYS_AHEAD; i++) {
			if (calendar.isSchoolDay(schoolId, today.plusDays(i))) {
				return today.plusDays(i);
			}
		}
		throw ApiException.invalid("summons.not_a_school_day", "meetingDate");
	}

}
