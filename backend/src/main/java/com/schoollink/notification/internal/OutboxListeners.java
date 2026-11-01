package com.schoollink.notification.internal;

import com.schoollink.attendance.AbsenceCancelled;
import com.schoollink.attendance.AttendanceConfirmed;
import com.schoollink.attendance.AttendanceConfirmed.Absence;
import com.schoollink.discipline.SummonsIssued;
import com.schoollink.lateness.LatenessCancelled;
import com.schoollink.lateness.LatenessRecorded;
import com.schoollink.notification.internal.Outbox.Channel;
import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.Outbox.Row;
import com.schoollink.notification.internal.Outbox.Status;
import com.schoollink.people.Guardians;
import com.schoollink.people.Guardians.StudentGuardian;
import com.schoollink.people.InvitationIssued;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolCalendar.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the events that concern a parent into outbox rows, synchronously and in the publisher's
 * transaction (NFR-6): if the event's transaction rolls back, so do its notifications, and a
 * failure here rolls the event back. Never sends anything itself: the dispatcher does (task 2.6).
 *
 * <p>Channel (D7): a guardian with an app account gets a push, sent at once (BR-21); one without gets
 * an SMS. An absence's or a lateness's SMS is held for the daily digest (BR-22, D6); an invitation goes at once (D13).
 * Each row is in the guardian's language (BR-20). The payload holds what the templates need (task
 * 2.5), captured now, so a later rename does not change a sent message.
 */
@Component
class OutboxListeners {

	private final Outbox outbox;

	private final Guardians guardians;

	private final SchoolCalendar calendar;

	private final Corrections corrections;

	OutboxListeners(Outbox outbox, Guardians guardians, SchoolCalendar calendar, Corrections corrections) {
		this.outbox = outbox;
		this.guardians = guardians;
		this.calendar = calendar;
		this.corrections = corrections;
	}

	/** D2: one notification per absent period, to every guardian of the student; no absence, nothing. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(AttendanceConfirmed event) {
		if (event.absences().isEmpty()) {
			return;
		}
		Map<UUID, UUID> recordOf = event.absences().stream()
			.collect(Collectors.toMap(Absence::studentId, Absence::recordId));
		Period period = calendar.period(event.schoolId(), event.periodId());
		List<Row> rows = guardians.ofStudents(event.schoolId(), recordOf.keySet()).stream().map(guardian -> {
			Map<String, Object> payload = Map.of("studentId", guardian.studentId(), "firstName",
					guardian.studentFirstName(), "date", event.date().toString(), "periodNumber", period.number(),
					"periodStart", period.startTime().toString());
			return childEvent(event.schoolId(), guardian, EventType.ABSENCE, recordOf.get(guardian.studentId()),
					payload);
		}).toList();
		outbox.write(rows);
	}

	/** BR-20: the lateness goes to every guardian of the student (push now, or SMS in the digest). */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(LatenessRecorded event) {
		List<Row> rows = guardians.ofStudents(event.schoolId(), Set.of(event.studentId())).stream().map(guardian -> {
			Map<String, Object> payload = Map.of("studentId", guardian.studentId(), "firstName",
					guardian.studentFirstName(), "date", event.date().toString(), "arrivalTime",
					event.arrivalTime().toString());
			return childEvent(event.schoolId(), guardian, EventType.LATENESS, event.recordId(), payload);
		}).toList();
		outbox.write(rows);
	}

	/** BR-14: an absence cancelled by the DM; the parents who were told get a correction. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(AbsenceCancelled event) {
		corrections.correct(event.schoolId(), EventType.ABSENCE, event.recordId());
	}

	/** BR-14, D11: the same for a lateness. */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(LatenessCancelled event) {
		corrections.correct(event.schoolId(), EventType.LATENESS, event.recordId());
	}

	/**
	 * BR-23, D12: a summons goes at once to every guardian of the student, by push with the app and
	 * by SMS without, never held for the digest.
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(SummonsIssued event) {
		List<Row> rows = guardians.ofStudents(event.schoolId(), Set.of(event.studentId())).stream().map(guardian -> {
			Map<String, Object> payload = Map.of("studentId", guardian.studentId(), "firstName",
					guardian.studentFirstName(), "meetingDate", event.meetingDate().toString(), "meetingTime",
					event.meetingTime().toString());
			return new Row(event.schoolId(), guardian.guardianId(), guardian.studentId(), EventType.SUMMONS,
					event.summonsId(), guardian.hasAccount() ? Channel.PUSH : Channel.SMS, guardian.language(), payload,
					Status.QUEUED);
		}).toList();
		outbox.write(rows);
	}

	/**
	 * D13: the code goes by SMS at once to a guardian without an account. The payload holds the
	 * code in clear until it is sent: the dispatcher removes it after sending (task 2.6).
	 */
	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(InvitationIssued event) {
		outbox.write(List.of(new Row(event.schoolId(), event.guardianId(), null, EventType.INVITATION,
				event.invitationId(), Channel.SMS, guardians.language(event.schoolId(), event.guardianId()),
				Map.of("code", event.code(), "expiresAt", event.expiresAt().toString()), Status.QUEUED)));
	}

	/** Absence and lateness: push now for app users, SMS held for the digest otherwise (D7, BR-21, BR-22). */
	private static Row childEvent(UUID school, StudentGuardian guardian, EventType type, UUID eventId,
			Map<String, Object> payload) {
		return guardian.hasAccount()
				? new Row(school, guardian.guardianId(), guardian.studentId(), type, eventId, Channel.PUSH,
						guardian.language(), payload, Status.QUEUED)
				: new Row(school, guardian.guardianId(), guardian.studentId(), type, eventId, Channel.SMS,
						guardian.language(), payload, Status.HELD);
	}

}
