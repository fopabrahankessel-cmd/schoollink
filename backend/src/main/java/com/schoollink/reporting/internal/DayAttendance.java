package com.schoollink.reporting.internal;

import com.schoollink.attendance.AttendanceHistory;
import com.schoollink.attendance.AttendanceHistory.DayRecord;
import com.schoollink.attendance.AttendanceHistory.DaySession;
import com.schoollink.lateness.LatenessHistory;
import com.schoollink.lateness.LatenessHistory.LatenessOnDay;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.people.UserAccounts;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolCalendar.Period;
import com.schoollink.school.SchoolCalendar.Slot;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D10, D40: one day of attendance in the DM's classes, period by period. A period with a session
 * is recorded, whoever recorded it; one without is "not recorded" once it has started (school time),
 * otherwise upcoming. It is information only: the DM never records a roll call here (BR-4, §11 call
 * 6). Cancelled absences and lateness are listed but never counted (BR-15); absences and lateness
 * stay side by side, never merged (BR-8, BR-9). Read-only and not audited: it is not an export.
 */
@Service
class DayAttendance {

	private static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER;

	enum PeriodStatus {

		RECORDED, NOT_RECORDED, UPCOMING

	}

	enum RecordState {

		UNJUSTIFIED, JUSTIFIED, CANCELLED;

		static RecordState of(boolean justified, boolean cancelled) {
			return cancelled ? CANCELLED : justified ? JUSTIFIED : UNJUSTIFIED;
		}

	}

	record Day(LocalDate date, boolean schoolDay, Totals totals, List<ClassDay> classes) {
	}

	record Totals(int notRecorded, int absentStudents, int lateness) {
	}

	record ClassDay(UUID classId, String className, List<PeriodDay> periods, List<LateRow> lateness) {
	}

	record PeriodDay(UUID periodId, int number, LocalTime startTime, LocalTime endTime, String subject,
			String teacherName, PeriodStatus status, int absentCount, List<AbsenceRow> absences) {
	}

	record AbsenceRow(UUID recordId, UUID studentId, String firstName, String lastName, String matricule,
			RecordState state) {
	}

	record LateRow(UUID recordId, UUID studentId, String firstName, String lastName, String matricule,
			LocalTime arrivalTime, RecordState state) {
	}

	private final AttendanceHistory attendance;

	private final LatenessHistory lateness;

	private final SchoolCalendar calendar;

	private final SchoolClasses classes;

	private final Students students;

	private final UserAccounts accounts;

	private final Clock clock;

	DayAttendance(AttendanceHistory attendance, LatenessHistory lateness, SchoolCalendar calendar,
			SchoolClasses classes, Students students, UserAccounts accounts, Clock clock) {
		this.attendance = attendance;
		this.lateness = lateness;
		this.calendar = calendar;
		this.classes = classes;
		this.students = students;
		this.accounts = accounts;
		this.clock = clock;
	}

	/** {@code date}: null for today at school; never in the future. */
	@Transactional(readOnly = true)
	Day day(CurrentUser user, LocalDate date) {
		UUID school = dmSchool(user);
		ZonedDateTime now = clock.instant().atZone(calendar.zone(school));
		LocalDate today = now.toLocalDate();
		LocalDate day = date == null ? today : date;
		if (day.isAfter(today)) {
			throw ApiException.invalid("day.date_in_future", "date");
		}
		if (!calendar.isSchoolDay(school, day)) {
			return new Day(day, false, new Totals(0, 0, 0), List.of());
		}
		Set<UUID> scope = user.classesAs(Role.DM);
		Map<UUID, Map<UUID, DaySession>> sessions = new HashMap<>(); // class -> period -> session
		for (DaySession session : attendance.sessionsOn(school, scope, day)) {
			sessions.computeIfAbsent(session.classId(), c -> new HashMap<>()).put(session.periodId(), session);
		}
		Map<UUID, List<Slot>> slots = new HashMap<>();
		for (UUID classId : scope) {
			List<Slot> classSlots = calendar.slotsOf(school, classId, day);
			if (!classSlots.isEmpty() || sessions.containsKey(classId)) {
				slots.put(classId, classSlots);
			}
		}

		Map<UUID, UUID> classOf = new HashMap<>(); // student -> current class
		for (UUID classId : slots.keySet()) {
			// D43: a student who left since keeps what was recorded that day
			students.everInClasses(school, Set.of(classId)).forEach(card -> classOf.put(card.id(), classId));
		}
		List<LatenessOnDay> late = lateness.onDay(school, classOf.keySet(), day);
		Set<UUID> named = new HashSet<>(classOf.keySet());
		sessions.values().forEach(m -> m.values().forEach(s -> s.absences().forEach(r -> named.add(r.studentId()))));
		Map<UUID, Card> cards = students.cards(school, named);
		Map<UUID, String> teachers = new HashMap<>();
		Map<UUID, String> classNames = classes.names(school, slots.keySet());
		LocalTime time = day.equals(today) ? now.toLocalTime() : LocalTime.MAX;

		List<ClassDay> result = new ArrayList<>();
		Set<UUID> absentStudents = new HashSet<>();
		int notRecorded = 0;
		int liveLateness = 0;
		for (UUID classId : slots.keySet()) {
			Map<UUID, DaySession> recorded = sessions.getOrDefault(classId, Map.of());
			Map<Integer, PeriodDay> periods = new TreeMap<>();
			for (Slot slot : slots.get(classId)) {
				DaySession session = recorded.get(slot.periodId());
				if (session == null) {
					PeriodStatus status = slot.startTime().isAfter(time) ? PeriodStatus.UPCOMING : PeriodStatus.NOT_RECORDED;
					if (status == PeriodStatus.NOT_RECORDED) {
						notRecorded++;
					}
					periods.put(slot.periodNumber(), new PeriodDay(slot.periodId(), slot.periodNumber(), slot.startTime(),
							slot.endTime(), slot.subject(), teacher(school, slot.teacherId(), teachers), status, 0, List.of()));
				}
				else {
					periods.put(slot.periodNumber(), recorded(school, slot.periodId(), slot.periodNumber(), slot.startTime(),
							slot.endTime(), slot.subject(), session, cards, teachers, absentStudents));
				}
			}
			for (DaySession session : recorded.values()) {
				Period period = calendar.period(school, session.periodId());
				// a session whose period left the timetable after the roll call is still shown
				periods.computeIfAbsent(period.number(), n -> recorded(school, period.id(), n, period.startTime(),
						period.endTime(), null, session, cards, teachers, absentStudents));
			}
			List<LateRow> lateRows = new ArrayList<>();
			for (LatenessOnDay record : late) {
				if (classId.equals(classOf.get(record.studentId()))) {
					Card card = cards.get(record.studentId());
					lateRows.add(new LateRow(record.recordId(), record.studentId(), card.firstName(), card.lastName(),
							card.matricule(), record.arrivalTime(), RecordState.of(record.justified(), record.cancelled())));
					if (!record.cancelled()) {
						liveLateness++;
					}
				}
			}
			lateRows.sort(Comparator.comparing(LateRow::arrivalTime));
			result.add(new ClassDay(classId, classNames.get(classId), List.copyOf(periods.values()), List.copyOf(lateRows)));
		}
		result.sort(Comparator.comparing(ClassDay::className, BY_NAME));
		return new Day(day, true, new Totals(notRecorded, absentStudents.size(), liveLateness), List.copyOf(result));
	}

	private PeriodDay recorded(UUID school, UUID periodId, int number, LocalTime start, LocalTime end, String subject,
			DaySession session, Map<UUID, Card> cards, Map<UUID, String> teachers, Set<UUID> absentStudents) {
		List<AbsenceRow> rows = new ArrayList<>();
		int live = 0;
		for (DayRecord record : session.absences()) {
			Card card = cards.get(record.studentId());
			rows.add(new AbsenceRow(record.recordId(), record.studentId(), card.firstName(), card.lastName(),
					card.matricule(), RecordState.of(record.justified(), record.cancelled())));
			if (!record.cancelled()) {
				live++;
				absentStudents.add(record.studentId());
			}
		}
		rows.sort(Comparator.comparing(AbsenceRow::lastName, BY_NAME)
			.thenComparing(AbsenceRow::firstName, BY_NAME)
			.thenComparing(AbsenceRow::matricule));
		return new PeriodDay(periodId, number, start, end, subject, teacher(school, session.teacherId(), teachers),
				PeriodStatus.RECORDED, live, List.copyOf(rows));
	}

	private String teacher(UUID school, UUID teacherId, Map<UUID, String> names) {
		if (teacherId == null) {
			return null;
		}
		return names.computeIfAbsent(teacherId, id -> accounts.fullName(school, id).orElse(null));
	}

	private static UUID dmSchool(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

}
