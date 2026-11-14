package com.schoollink.lateness.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.lateness.LatenessRecorded;
import com.schoollink.people.Students;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-AD1: a lateness recorded at the gate by an assistant DM, for a student of their scope (BR-7).
 * The device generated the id (NFR-3), so a replayed upload returns the stored record and changes
 * nothing; the same id with different content is refused, since a record never changes (D20).
 * One live record per student per day: a second one, under another id, is a conflict. Attendance
 * is never read or changed here (BR-8, BR-9).
 * <p>
 * A phone is never refused for good because its clock is wrong (NFR-3): the queue on the device
 * does not retry a refusal, so the record would be lost. An arrival that has not happened yet
 * becomes the time the server received it, a saving time that cannot be right becomes the
 * server's, and what the phone sent is kept beside them.
 */
@Service
class LatenessRecorder {

	/** How far ahead of the server clock a device's time may be before it is replaced. */
	static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

	/**
	 * {@code arrivalTime}: when the student came through the gate, in the school's time zone, to
	 * the minute. {@code recordedAt}: when the assistant DM saved it on the device.
	 */
	record LatenessRequest(@NotNull UUID studentId, @NotNull LocalDate date, @NotNull LocalTime arrivalTime,
			@NotNull Instant recordedAt) {
	}

	record LatenessView(UUID id, UUID studentId, LocalDate date, LocalTime arrivalTime, Instant recordedAt,
			Instant syncedAt) {
	}

	/** {@code created}: false when the device replayed a record that was already stored. */
	record Saved(LatenessView lateness, boolean created) {
	}

	/** The device values: what the phone sent, when the server replaced it; else null. */
	private record Stored(UUID schoolId, UUID recordedBy, LatenessView view, LocalTime deviceArrivalTime,
			Instant deviceRecordedAt) {
	}

	/** What is stored for a request: the student's class and the times, replaced where they had to be. */
	private record Accepted(UUID classId, LocalTime arrivalTime, Instant recordedAt) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Students students;

	private final SchoolCalendar calendar;

	private final AuditLog audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	LatenessRecorder(NamedParameterJdbcTemplate jdbc, Students students, SchoolCalendar calendar, AuditLog audit,
			ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.students = students;
		this.calendar = calendar;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	Saved save(CurrentUser user, UUID id, LatenessRequest request) {
		if (user.schoolId() == null || !user.has(Role.ASSISTANT_DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		// PostgreSQL keeps microseconds: a replay must compare equal to what was stored
		LatenessRequest normalized = new LatenessRequest(request.studentId(), request.date(),
				request.arrivalTime().truncatedTo(ChronoUnit.MINUTES), request.recordedAt().truncatedTo(ChronoUnit.MICROS));
		Optional<Stored> existing = find(id);
		if (existing.isPresent()) {
			return replay(user, existing.get(), normalized);
		}
		Instant now = clock.instant();
		Accepted accepted = validate(user, normalized, now);
		UUID classId = accepted.classId();
		LocalTime arrivalTime = accepted.arrivalTime();
		Instant recordedAt = accepted.recordedAt();
		LocalTime deviceArrivalTime = arrivalTime.equals(normalized.arrivalTime()) ? null : normalized.arrivalTime();
		Instant deviceRecordedAt = recordedAt.equals(normalized.recordedAt()) ? null : normalized.recordedAt();

		int inserted = jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at, synced_at,
				                             device_arrival_time, device_recorded_at)
				VALUES (:id, :school, :student, :date, :arrival, :by, :recordedAt, :now, :deviceArrival, :deviceRecordedAt)
				ON CONFLICT DO NOTHING""",
				new MapSqlParameterSource("id", id).addValue("school", school)
					.addValue("student", normalized.studentId())
					.addValue("date", normalized.date())
					.addValue("arrival", arrivalTime)
					.addValue("by", user.userId())
					.addValue("recordedAt", Timestamp.from(recordedAt))
					.addValue("now", Timestamp.from(now))
					.addValue("deviceArrival", deviceArrivalTime, Types.TIME)
					.addValue("deviceRecordedAt", deviceRecordedAt == null ? null : Timestamp.from(deviceRecordedAt),
							Types.TIMESTAMP));
		if (inserted == 0) {
			// a concurrent upload of the same id won the race, or the student is already recorded today
			return find(id).map(stored -> replay(user, stored, normalized))
				.orElseThrow(() -> ApiException.conflict("lateness.already_recorded"));
		}
		events.publishEvent(new LatenessRecorded(id, school, normalized.studentId(), classId, normalized.date(),
				arrivalTime, user.userId(), recordedAt));
		// NFR-11: ids only, no names; what the server replaced is on record
		Map<String, Object> after = new LinkedHashMap<>(Map.of("studentId", normalized.studentId(), "date",
				normalized.date().toString(), "arrivalTime", arrivalTime.toString(), "recordedAt", recordedAt.toString()));
		if (deviceArrivalTime != null) {
			after.put("deviceArrivalTime", deviceArrivalTime.toString());
		}
		if (deviceRecordedAt != null) {
			after.put("deviceRecordedAt", deviceRecordedAt.toString());
		}
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "lateness_record", id.toString(), null,
				after));
		return new Saved(new LatenessView(id, normalized.studentId(), normalized.date(), arrivalTime, recordedAt, now),
				true);
	}

	/** Checks the request and returns what to store: a time the phone cannot have right is replaced. */
	private Accepted validate(CurrentUser user, LatenessRequest request, Instant receivedAt) {
		UUID school = user.schoolId();
		// NFR-8, D17: a student of another school or outside the scope is not found
		UUID classId = students.classOf(school, request.studentId())
			.filter(user.classesAs(Role.ASSISTANT_DM)::contains)
			.orElseThrow(() -> ApiException.notFound("not_found"));
		ZoneId zone = calendar.zone(school);
		ZonedDateTime now = receivedAt.atZone(zone);
		if (request.date().isAfter(now.toLocalDate())) {
			throw ApiException.invalid("lateness.date_in_future", "date");
		}
		// D3, D10
		if (!calendar.isSchoolDay(school, request.date())) {
			throw ApiException.invalid("lateness.not_a_school_day", "date");
		}
		// NFR-3: an arrival that has not happened yet comes from a phone whose clock is ahead; the
		// student is at the gate now. The date is today then, since it is not in the future.
		LocalTime arrivalTime = request.arrivalTime();
		if (request.date().atTime(arrivalTime).atZone(zone).isAfter(now.plus(CLOCK_SKEW))) {
			arrivalTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
		}
		// BR-7: late means through the gate after the school's start time
		LocalTime start = calendar.startTime(school);
		if (!arrivalTime.isAfter(start)) {
			throw ApiException.invalid("lateness.not_late", "arrivalTime", start.toString());
		}
		// saved ahead of the server clock, or before the student arrived: the server's time instead
		Instant arrival = request.date().atTime(arrivalTime).atZone(zone).toInstant();
		Instant recordedAt = request.recordedAt();
		if (recordedAt.isAfter(receivedAt.plus(CLOCK_SKEW)) || recordedAt.isBefore(arrival.minus(CLOCK_SKEW))) {
			recordedAt = receivedAt.truncatedTo(ChronoUnit.MICROS);
		}
		return new Accepted(classId, arrivalTime, recordedAt);
	}

	/**
	 * The same upload again (a retry after a lost answer, NFR-3): the stored record, unchanged.
	 * Anything different is an attempt to change it (D20). A record of another school or another
	 * assistant DM is not the caller's: 404, so its existence does not leak (NFR-8). The phone
	 * resends what it sent the first time, so its own times match too when the server replaced them.
	 */
	private Saved replay(CurrentUser user, Stored stored, LatenessRequest request) {
		if (!stored.schoolId().equals(user.schoolId()) || !stored.recordedBy().equals(user.userId())) {
			throw ApiException.notFound("not_found");
		}
		LatenessView view = stored.view();
		boolean same = view.studentId().equals(request.studentId()) && view.date().equals(request.date())
				&& (view.arrivalTime().equals(request.arrivalTime())
						|| request.arrivalTime().equals(stored.deviceArrivalTime()))
				&& (view.recordedAt().equals(request.recordedAt())
						|| request.recordedAt().equals(stored.deviceRecordedAt()));
		if (!same) {
			throw ApiException.conflict("lateness.record_locked");
		}
		return new Saved(view, false);
	}

	private Optional<Stored> find(UUID id) {
		return jdbc.query("""
				SELECT school_id, recorded_by, student_id, date, arrival_time, recorded_at, synced_at,
				       device_arrival_time, device_recorded_at
				FROM lateness_record WHERE id = :id""", new MapSqlParameterSource("id", id), (rs, n) -> {
			Timestamp deviceRecordedAt = rs.getTimestamp("device_recorded_at");
			return new Stored(rs.getObject("school_id", UUID.class), rs.getObject("recorded_by", UUID.class),
					new LatenessView(id, rs.getObject("student_id", UUID.class), rs.getObject("date", LocalDate.class),
							rs.getObject("arrival_time", LocalTime.class), rs.getTimestamp("recorded_at").toInstant(),
							rs.getTimestamp("synced_at").toInstant()),
					rs.getObject("device_arrival_time", LocalTime.class),
					deviceRecordedAt == null ? null : deviceRecordedAt.toInstant());
		}).stream().findFirst();
	}

}
