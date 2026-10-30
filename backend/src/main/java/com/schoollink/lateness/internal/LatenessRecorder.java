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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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
 */
@Service
class LatenessRecorder {

	/** How far ahead of the server clock a device's time may be. */
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

	private record Stored(UUID schoolId, UUID recordedBy, LatenessView view) {
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
		UUID classId = validate(user, normalized);

		Instant now = clock.instant();
		int inserted = jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at, synced_at)
				VALUES (:id, :school, :student, :date, :arrival, :by, :recordedAt, :now)
				ON CONFLICT DO NOTHING""",
				new MapSqlParameterSource("id", id).addValue("school", school)
					.addValue("student", normalized.studentId())
					.addValue("date", normalized.date())
					.addValue("arrival", normalized.arrivalTime())
					.addValue("by", user.userId())
					.addValue("recordedAt", Timestamp.from(normalized.recordedAt()))
					.addValue("now", Timestamp.from(now)));
		if (inserted == 0) {
			// a concurrent upload of the same id won the race, or the student is already recorded today
			return find(id).map(stored -> replay(user, stored, normalized))
				.orElseThrow(() -> ApiException.conflict("lateness.already_recorded"));
		}
		events.publishEvent(new LatenessRecorded(id, school, normalized.studentId(), classId, normalized.date(),
				normalized.arrivalTime(), user.userId(), normalized.recordedAt()));
		// NFR-11: ids only, no names
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "lateness_record", id.toString(), null,
				Map.of("studentId", normalized.studentId(), "date", normalized.date().toString(), "arrivalTime",
						normalized.arrivalTime().toString(), "recordedAt", normalized.recordedAt().toString())));
		return new Saved(new LatenessView(id, normalized.studentId(), normalized.date(), normalized.arrivalTime(),
				normalized.recordedAt(), now), true);
	}

	/** Returns the student's class. */
	private UUID validate(CurrentUser user, LatenessRequest request) {
		UUID school = user.schoolId();
		// NFR-8, D17: a student of another school or outside the scope is not found
		UUID classId = students.classOf(school, request.studentId())
			.filter(user.classesAs(Role.ASSISTANT_DM)::contains)
			.orElseThrow(() -> ApiException.notFound("not_found"));
		ZoneId zone = calendar.zone(school);
		ZonedDateTime now = clock.instant().atZone(zone);
		if (request.date().isAfter(now.toLocalDate())) {
			throw ApiException.invalid("lateness.date_in_future", "date");
		}
		// D3, D10
		if (!calendar.isSchoolDay(school, request.date())) {
			throw ApiException.invalid("lateness.not_a_school_day", "date");
		}
		// BR-7: late means through the gate after the school's start time
		LocalTime start = calendar.startTime(school);
		if (!request.arrivalTime().isAfter(start)) {
			throw ApiException.invalid("lateness.not_late", "arrivalTime", start.toString());
		}
		ZonedDateTime arrival = request.date().atTime(request.arrivalTime()).atZone(zone);
		if (arrival.isAfter(now.plus(CLOCK_SKEW))) {
			throw ApiException.invalid("lateness.arrival_in_future", "arrivalTime");
		}
		Instant recordedAt = request.recordedAt();
		if (recordedAt.isAfter(now.toInstant().plus(CLOCK_SKEW))
				|| recordedAt.isBefore(arrival.toInstant().minus(CLOCK_SKEW))) {
			throw ApiException.invalid("lateness.recorded_at_invalid", "recordedAt");
		}
		return classId;
	}

	/**
	 * The same upload again (a retry after a lost answer, NFR-3): the stored record, unchanged.
	 * Anything different is an attempt to change it (D20). A record of another school or another
	 * assistant DM is not the caller's: 404, so its existence does not leak (NFR-8).
	 */
	private Saved replay(CurrentUser user, Stored stored, LatenessRequest request) {
		if (!stored.schoolId().equals(user.schoolId()) || !stored.recordedBy().equals(user.userId())) {
			throw ApiException.notFound("not_found");
		}
		LatenessView view = stored.view();
		boolean same = view.studentId().equals(request.studentId()) && view.date().equals(request.date())
				&& view.arrivalTime().equals(request.arrivalTime()) && view.recordedAt().equals(request.recordedAt());
		if (!same) {
			throw ApiException.conflict("lateness.record_locked");
		}
		return new Saved(view, false);
	}

	private Optional<Stored> find(UUID id) {
		return jdbc.query("""
				SELECT school_id, recorded_by, student_id, date, arrival_time, recorded_at, synced_at
				FROM lateness_record WHERE id = :id""", new MapSqlParameterSource("id", id),
				(rs, n) -> new Stored(rs.getObject("school_id", UUID.class), rs.getObject("recorded_by", UUID.class),
						new LatenessView(id, rs.getObject("student_id", UUID.class), rs.getObject("date", LocalDate.class),
								rs.getObject("arrival_time", LocalTime.class), rs.getTimestamp("recorded_at").toInstant(),
								rs.getTimestamp("synced_at").toInstant())))
			.stream()
			.findFirst();
	}

}
