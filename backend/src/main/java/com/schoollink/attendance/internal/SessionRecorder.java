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
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * <p>
 * A phone is never refused for good because its clock or its class list is wrong (BR-6, NFR-3):
 * the queue on the device does not retry a refusal, so the roll would be lost. A confirmation time
 * that cannot be right is replaced by the server's, and the phone's is kept beside it; an absentee
 * who is not in the class any more is left out, and the audit entry says which.
 */
@Service
class SessionRecorder {

	/** How far ahead of the server clock a device's confirmation time may be before it is replaced. */
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

	/** {@code deviceConfirmedAt}: what the phone sent, when the server replaced it; else null. */
	private record Stored(UUID schoolId, UUID teacherId, SessionView view, Instant deviceConfirmedAt,
			Set<UUID> students) {
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
		Instant sentAt = request.confirmedAt().truncatedTo(ChronoUnit.MICROS);
		Optional<Stored> existing = find(id);
		if (existing.isPresent()) {
			return replay(user, existing.get(), request, sentAt);
		}
		Instant now = clock.instant();
		Set<UUID> roll = validate(user, request, now);
		// BR-6: a wrong clock on the phone does not lose the roll; the lock time is then the server's
		boolean plausible = plausible(school, request.date(), sentAt, now);
		Instant confirmedAt = plausible ? sentAt : now.truncatedTo(ChronoUnit.MICROS);
		Instant deviceConfirmedAt = plausible ? null : sentAt;
		// a stale class list on the phone: who is not in the class any more gets no record
		Set<UUID> absent = new HashSet<>(request.absentStudentIds());
		absent.retainAll(roll);
		List<String> dropped = request.absentStudentIds()
			.stream()
			.filter(student -> !roll.contains(student))
			.map(UUID::toString)
			.sorted()
			.toList();

		int inserted = jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at, synced_at,
				                                device_confirmed_at)
				VALUES (:id, :school, :class, :date, :period, :teacher, :confirmedAt, :now, :deviceConfirmedAt)
				ON CONFLICT DO NOTHING""",
				new MapSqlParameterSource("id", id).addValue("school", school)
					.addValue("class", request.classId())
					.addValue("date", request.date())
					.addValue("period", request.periodId())
					.addValue("teacher", user.userId())
					.addValue("confirmedAt", Timestamp.from(confirmedAt))
					.addValue("now", Timestamp.from(now))
					.addValue("deviceConfirmedAt", deviceConfirmedAt == null ? null : Timestamp.from(deviceConfirmedAt),
							Types.TIMESTAMP));
		if (inserted == 0) {
			// a concurrent upload of the same id won the race, or another device recorded this period
			return find(id).map(stored -> replay(user, stored, request, sentAt))
				.orElseThrow(() -> ApiException.conflict("attendance.session_exists"));
		}
		List<SqlParameterSource> records = new ArrayList<>();
		List<Absence> absences = new ArrayList<>();
		for (UUID student : roll) {
			UUID recordId = UUID.randomUUID();
			boolean isAbsent = absent.contains(student);
			if (isAbsent) {
				absences.add(new Absence(recordId, student));
			}
			records.add(new MapSqlParameterSource("id", recordId).addValue("school", school)
				.addValue("session", id)
				.addValue("student", student)
				.addValue("status", isAbsent ? "ABSENT" : "PRESENT"));
		}
		jdbc.batchUpdate("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (:id, :school, :session, :student, :status)""", records.toArray(SqlParameterSource[]::new));

		// D2: the outbox rows for the guardians are written by its listeners, in this transaction
		events.publishEvent(new AttendanceConfirmed(id, school, request.classId(), request.date(), request.periodId(),
				user.userId(), confirmedAt, absences));
		// NFR-11: ids only, no names; what the server replaced or left out is on record
		Map<String, Object> after = new LinkedHashMap<>(Map.of("classId", request.classId(), "date",
				request.date().toString(), "periodId", request.periodId(), "confirmedAt", confirmedAt.toString(), "students",
				roll.size(), "absentStudentIds", absent.stream().map(UUID::toString).sorted().toList()));
		if (deviceConfirmedAt != null) {
			after.put("deviceConfirmedAt", deviceConfirmedAt.toString());
		}
		if (!dropped.isEmpty()) {
			after.put("droppedStudentIds", dropped);
		}
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "attendance_session", id.toString(), null,
				after));
		return new Saved(new SessionView(id, request.classId(), request.date(), request.periodId(), confirmedAt, now,
				roll.size(), Set.copyOf(absent)), true);
	}

	/** Not ahead of the server clock, and not before the day of the session it confirms. */
	private boolean plausible(UUID school, LocalDate date, Instant confirmedAt, Instant now) {
		return !confirmedAt.isAfter(now.plus(CLOCK_SKEW))
				&& !confirmedAt.atZone(calendar.zone(school)).toLocalDate().isBefore(date);
	}

	/** Returns the roll of the class: every student gets a record (BR-2). */
	private Set<UUID> validate(CurrentUser user, SessionRequest request, Instant now) {
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
		if (request.date().isAfter(now.atZone(zone).toLocalDate())) {
			throw ApiException.invalid("attendance.date_in_future", "date");
		}
		// D3, D10
		if (!calendar.isSchoolDay(school, request.date())) {
			throw ApiException.invalid("attendance.not_a_school_day", "date");
		}
		return students.idsInClass(school, request.classId());
	}

	/**
	 * The same upload again (a retry after a lost answer, BR-6): the stored session, unchanged.
	 * Anything different is an attempt to change a locked session (BR-4). A session of another
	 * school or another teacher is not the caller's: 404, so its existence does not leak (NFR-8).
	 * The phone resends what it sent the first time: its own confirmation time, when the server
	 * replaced it, and the absentees the server left out, who have no record in the session.
	 */
	private Saved replay(CurrentUser user, Stored stored, SessionRequest request, Instant confirmedAt) {
		if (!stored.schoolId().equals(user.schoolId()) || !stored.teacherId().equals(user.userId())) {
			throw ApiException.notFound("not_found");
		}
		SessionView view = stored.view();
		Set<UUID> absent = new HashSet<>(request.absentStudentIds());
		absent.retainAll(stored.students());
		boolean same = view.classId().equals(request.classId()) && view.date().equals(request.date())
				&& view.periodId().equals(request.periodId())
				&& (view.confirmedAt().equals(confirmedAt) || confirmedAt.equals(stored.deviceConfirmedAt()))
				&& view.absentStudentIds().equals(absent);
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
				       s.device_confirmed_at
				FROM attendance_session s WHERE s.id = :id""", p, (rs, n) -> {
			Set<UUID> students = new HashSet<>();
			Set<UUID> absent = new HashSet<>();
			jdbc.query("SELECT student_id, status FROM attendance_record WHERE session_id = :id", p, record -> {
				UUID student = record.getObject("student_id", UUID.class);
				students.add(student);
				if ("ABSENT".equals(record.getString("status"))) {
					absent.add(student);
				}
			});
			Timestamp deviceConfirmedAt = rs.getTimestamp("device_confirmed_at");
			return new Stored(rs.getObject("school_id", UUID.class), rs.getObject("teacher_id", UUID.class),
					new SessionView(id, rs.getObject("class_id", UUID.class), rs.getObject("date", LocalDate.class),
							rs.getObject("period_id", UUID.class), rs.getTimestamp("confirmed_at").toInstant(),
							rs.getTimestamp("synced_at").toInstant(), students.size(), Set.copyOf(absent)),
					deviceConfirmedAt == null ? null : deviceConfirmedAt.toInstant(), students);
		}).stream().findFirst();
	}

}
