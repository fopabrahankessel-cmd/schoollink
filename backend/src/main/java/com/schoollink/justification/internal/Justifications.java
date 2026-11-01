package com.schoollink.justification.internal;

import com.schoollink.attendance.AbsenceRecords;
import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.lateness.LatenessRecords;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D1: the DM in charge of a student's class justifies absences and lateness the parent explained
 * in person (BR-11), one record or several at once, such as a three-day illness (BR-12). All the
 * records of a request get the same justification, or none does: one record out of scope,
 * cancelled (BR-15) or present makes the whole request fail. A record already justified gets the
 * new justification, so the DM can correct a reason; the audit keeps the previous one (NFR-11).
 */
@Service
class Justifications {

	/** At most this many records in one request: a few weeks of a student's absences. */
	static final int MAX_RECORDS = 100;

	static final int MAX_NOTE = 300;

	/** BR-11. */
	enum Reason {

		ILLNESS, FAMILY, SCHOOL_ACTIVITY, OTHER

	}

	/**
	 * {@code note}: optional, never for {@link Reason#ILLNESS} (NFR-10: no health detail beyond
	 * "illness"). Either list may be omitted, not both.
	 */
	record JustificationRequest(@NotNull Reason reason, @Size(max = MAX_NOTE) String note, List<UUID> absenceIds,
			List<UUID> latenessIds) {
	}

	record JustificationView(UUID id, Reason reason, String note, Instant recordedAt, List<UUID> absenceIds,
			List<UUID> latenessIds) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final AbsenceRecords absences;

	private final LatenessRecords lateness;

	private final Students students;

	private final AuditLog audit;

	private final Clock clock;

	Justifications(NamedParameterJdbcTemplate jdbc, AbsenceRecords absences, LatenessRecords lateness,
			Students students, AuditLog audit, Clock clock) {
		this.jdbc = jdbc;
		this.absences = absences;
		this.lateness = lateness;
		this.students = students;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	JustificationView justify(CurrentUser user, JustificationRequest request) {
		// BR-11: the DM only; not the admin, not the assistant DM who records lateness
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		Set<UUID> absenceIds = distinct(request.absenceIds());
		Set<UUID> latenessIds = distinct(request.latenessIds());
		if (absenceIds.isEmpty() && latenessIds.isEmpty()) {
			throw ApiException.invalid("justification.no_records", "absenceIds");
		}
		if (absenceIds.size() + latenessIds.size() > MAX_RECORDS) {
			throw ApiException.invalid("justification.too_many_records", "absenceIds", MAX_RECORDS);
		}
		String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
		if (note != null && request.reason() == Reason.ILLNESS) {
			throw ApiException.invalid("justification.note_not_allowed", "note");
		}

		List<AbsenceRecords.Recorded> absent = absences.lock(school, absenceIds);
		List<LatenessRecords.Recorded> late = lateness.lock(school, latenessIds);
		// NFR-8: a record of another school is not found
		if (absent.size() != absenceIds.size() || late.size() != latenessIds.size()) {
			throw ApiException.notFound("not_found");
		}
		// BR-11, D17: the DM in charge of the student's class; outside the scope a record is not found
		Set<UUID> studentIds = new LinkedHashSet<>();
		absent.forEach(r -> studentIds.add(r.studentId()));
		late.forEach(r -> studentIds.add(r.studentId()));
		Map<UUID, Card> cards = students.cards(school, studentIds);
		Set<UUID> scope = user.classesAs(Role.DM);
		if (!studentIds.stream().allMatch(id -> cards.containsKey(id) && scope.contains(cards.get(id).classId()))) {
			throw ApiException.notFound("not_found");
		}
		if (absent.stream().anyMatch(r -> !r.absent())) {
			throw ApiException.invalid("justification.not_an_absence", "absenceIds");
		}
		// BR-15: a cancelled record no longer counts, so there is nothing to justify
		if (Stream.concat(absent.stream().map(AbsenceRecords.Recorded::cancelled),
				late.stream().map(LatenessRecords.Recorded::cancelled))
			.anyMatch(Boolean::booleanValue)) {
			throw ApiException.conflict("justification.record_cancelled");
		}

		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		UUID id = jdbc.queryForObject("""
				INSERT INTO justification (school_id, reason, note, recorded_by, recorded_at)
				VALUES (:school, :reason, :note, :by, :now) RETURNING id""",
				new MapSqlParameterSource("school", school).addValue("reason", request.reason().name())
					.addValue("note", note)
					.addValue("by", user.userId())
					.addValue("now", Timestamp.from(now)),
				UUID.class);
		absences.justify(school, absenceIds, id);
		lateness.justify(school, latenessIds, id);

		// NFR-11: the justification, then each record with its previous justification
		Map<String, Object> after = new HashMap<>();
		after.put("reason", request.reason().name());
		after.put("note", note);
		after.put("absenceIds", List.copyOf(absenceIds));
		after.put("latenessIds", List.copyOf(latenessIds));
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "justification", id.toString(), null,
				after));
		for (AbsenceRecords.Recorded r : absent) {
			recordAudit(user, "attendance_record", r.id(), r.justificationId(), id, request.reason());
		}
		for (LatenessRecords.Recorded r : late) {
			recordAudit(user, "lateness_record", r.id(), r.justificationId(), id, request.reason());
		}
		return new JustificationView(id, request.reason(), note, now, List.copyOf(absenceIds),
				List.copyOf(latenessIds));
	}

	private void recordAudit(CurrentUser user, String entity, UUID recordId, UUID previous, UUID justification,
			Reason reason) {
		Map<String, Object> before = new HashMap<>();
		before.put("justificationId", previous == null ? null : previous.toString());
		audit.record(new AuditEntry(user.schoolId(), user.userId(), AuditAction.JUSTIFY, entity, recordId.toString(),
				before, Map.of("justificationId", justification.toString(), "reason", reason.name())));
	}

	private static Set<UUID> distinct(List<UUID> ids) {
		Set<UUID> result = new LinkedHashSet<>();
		if (ids != null) {
			for (UUID id : ids) {
				if (id == null) {
					throw ApiException.invalid("validation.not_null", "absenceIds");
				}
				result.add(id);
			}
		}
		return result;
	}

}
