package com.schoollink.attendance.internal;

import com.schoollink.attendance.AttendanceConfirmed;
import com.schoollink.attendance.AttendanceConfirmed.Absence;
import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.Students;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-T1, UC-T2: stores a session the teacher confirmed twice on the device (BR-3). It is locked
 * from the start (BR-4): there is no endpoint to change it, and the database rejects any change.
 * The device generated the id (BR-6), so a replayed upload returns the stored session and changes
 * nothing; the same id with different content is a change to a locked session and is refused.
 * A second session for the same class, date and period, under another id, is a conflict (BR-1).
 * Every student of the class gets a record: present unless listed as absent (BR-2), so a session
 * with no absentee is stored too (BR-5).
 */
@Service
class SessionRecorder {

	/** How far ahead of the server clock a device's confirmation time may be. */
	static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

	/** {@code confirmedAt}: when the teacher confirmed on the device, the lock time (BR-6). */
	record SessionRequest(@NotNull UUID classId, @NotNull LocalDate date, @NotNull UUID periodId,
			@NotNull Instant confirmedAt, @NotNull @Size(max = 300) Set<UUID> absentStudentIds) {
	}

	record SessionView(UUID id, UUID classId, LocalDate date, UUID periodId, Instant confirmedAt, Instant syncedAt,
			int studentCount, Set<UUID> absentStudentIds) {
	}

	/** {@code created}: false when the device replayed a session that was already stored. */
	record Saved(SessionView session, boolean created) {
	}

	private record Stored(UUID schoolId, UUID teacherId, SessionView view) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolClasses schoolClasses;

	private final SchoolCalendar calendar;

	private final Students students;

	private final AuditLog audit;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	SessionRecorder(NamedParameterJdbcTemplate jdbc, SchoolClasses schoolClasses, SchoolCalendar calendar,
			Students students, AuditLog audit, ApplicationEventPublisher events, Clock clock) {
		this.jdbc = jdbc;
		this.schoolClasses = schoolClasses;
		this.calendar = calendar;
		this.students = students;
		this.audit = audit;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	Saved save(CurrentUser user, UUID id, SessionRequest request) {
		if (user.schoolId() == null || !user.has(Role.TEACHER)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		// PostgreSQL keeps microseconds: a replay must compare equal to what was stored
		Instant confirmedAt = request.confirmedAt().truncatedTo(ChronoUnit.MICROS);
		Optional<Stored> existing = find(id);
		if (existing.isPresent()) {
			return replay(user, existing.get(), request, confirmedAt);
		}
		Set<UUID> roll = validate(user, request, confirmedAt);

		Instant now = clock.instant();
		int inserted = jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at, synced_at)
				VALUES (:id, :school, :class, :date, :period, :teacher, :confirmedAt, :now)
				ON CONFLICT DO NOTHING""",
				new MapSqlParameterSource("id", id).addValue("school", school)
					.addValue("class", request.classId())
					.addValue("date", request.date())
					.addValue("period", request.periodId())
					.addValue("teacher", user.userId())
					.addValue("confirmedAt", Timestamp.from(confirmedAt))
					.addValue("now", Timestamp.from(now)));
		if (inserted == 0) {
			// a concurrent upload of the same id won the race, or another device recorded this period
			return find(id).map(stored -> replay(user, stored, request, confirmedAt))
				.orElseThrow(() -> ApiException.conflict("attendance.session_exists"));
		}
		List<SqlParameterSource> records = new ArrayList<>();
		List<Absence> absences = new ArrayList<>();
		for (UUID student : roll) {
			UUID recordId = UUID.randomUUID();
			boolean absent = request.absentStudentIds().contains(student);
			if (absent) {
				absences.add(new Absence(recordId, student));
			}
			records.add(new MapSqlParameterSource("id", recordId).addValue("school", school)
				.addValue("session", id)
				.addValue("student", student)
				.addValue("status", absent ? "ABSENT" : "PRESENT"));
		}
		jdbc.batchUpdate("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (:id, :school, :session, :student, :status)""", records.toArray(SqlParameterSource[]::new));

		// D2: the outbox rows for the guardians are written by its listeners, in this transaction
		events.publishEvent(new AttendanceConfirmed(id, school, request.classId(), request.date(), request.periodId(),
				user.userId(), confirmedAt, absences));
		// NFR-11: ids only, no names
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "attendance_session", id.toString(), null,
				Map.of("classId", request.classId(), "date", request.date().toString(), "periodId", request.periodId(),
						"confirmedAt", confirmedAt.toString(), "students", roll.size(), "absentStudentIds",
						request.absentStudentIds().stream().map(UUID::toString).sorted().toList())));
		return new Saved(new SessionView(id, request.classId(), request.date(), request.periodId(), confirmedAt, now,
				roll.size(), Set.copyOf(request.absentStudentIds())), true);
	}

	/** Returns the roll of the class: every student gets a record (BR-2). */
	private Set<UUID> validate(CurrentUser user, SessionRequest request, Instant confirmedAt) {
		UUID school = user.schoolId();
		if (!schoolClasses.classExists(school, request.classId())) {
			throw ApiException.notFound("not_found");
		}
		// BR-1: only a teacher assigned to the class
		if (!user.classesAs(Role.TEACHER).contains(request.classId())) {
			throw ApiException.forbidden();
		}
		if (!calendar.periodExists(school, request.periodId())) {
			throw ApiException.invalid("reference.unknown", "periodId");
		}
		ZoneId zone = calendar.zone(school);
		Instant now = clock.instant();
		if (request.date().isAfter(now.atZone(zone).toLocalDate())) {
			throw ApiException.invalid("attendance.date_in_future", "date");
		}
		// D3, D10
		if (!calendar.isSchoolDay(school, request.date())) {
			throw ApiException.invalid("attendance.not_a_school_day", "date");
		}
		if (confirmedAt.isAfter(now.plus(CLOCK_SKEW)) || confirmedAt.atZone(zone).toLocalDate().isBefore(request.date())) {
			throw ApiException.invalid("attendance.confirmed_at_invalid", "confirmedAt");
		}
		Set<UUID> roll = students.idsInClass(school, request.classId());
		if (!roll.containsAll(request.absentStudentIds())) {
			throw ApiException.invalid("attendance.student_not_in_class", "absentStudentIds");
		}
		return roll;
	}

	/**
	 * The same upload again (a retry after a lost answer, BR-6): the stored session, unchanged.
	 * Anything different is an attempt to change a locked session (BR-4). A session of another
	 * school or another teacher is not the caller's: 404, so its existence does not leak (NFR-8).
	 */
	private Saved replay(CurrentUser user, Stored stored, SessionRequest request, Instant confirmedAt) {
		if (!stored.schoolId().equals(user.schoolId()) || !stored.teacherId().equals(user.userId())) {
			throw ApiException.notFound("not_found");
		}
		SessionView view = stored.view();
		boolean same = view.classId().equals(request.classId()) && view.date().equals(request.date())
				&& view.periodId().equals(request.periodId()) && view.confirmedAt().equals(confirmedAt)
				&& view.absentStudentIds().equals(new HashSet<>(request.absentStudentIds()));
		if (!same) {
			throw ApiException.conflict("attendance.session_locked");
		}
		return new Saved(view, false);
	}

	/** Absentees as recorded, cancelled or not: a replay is compared with the original upload. */
	private Optional<Stored> find(UUID id) {
		MapSqlParameterSource p = new MapSqlParameterSource("id", id);
		return jdbc.query("""
				SELECT s.school_id, s.teacher_id, s.class_id, s.date, s.period_id, s.confirmed_at, s.synced_at,
				       (SELECT count(*) FROM attendance_record r WHERE r.session_id = s.id AND r.school_id = s.school_id) AS students
				FROM attendance_session s WHERE s.id = :id""", p, (rs, n) -> {
			List<UUID> absent = jdbc.queryForList("""
					SELECT student_id FROM attendance_record WHERE session_id = :id AND status = 'ABSENT'""", p, UUID.class);
			return new Stored(rs.getObject("school_id", UUID.class), rs.getObject("teacher_id", UUID.class),
					new SessionView(id, rs.getObject("class_id", UUID.class), rs.getObject("date", LocalDate.class),
							rs.getObject("period_id", UUID.class), rs.getTimestamp("confirmed_at").toInstant(),
							rs.getTimestamp("synced_at").toInstant(), rs.getInt("students"), Set.copyOf(absent)));
		}).stream().findFirst();
	}

}
