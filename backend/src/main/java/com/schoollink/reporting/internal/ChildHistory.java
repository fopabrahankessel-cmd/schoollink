package com.schoollink.reporting.internal;

import com.schoollink.attendance.AttendanceHistory;
import com.schoollink.attendance.AttendanceHistory.Absence;
import com.schoollink.lateness.LatenessHistory;
import com.schoollink.lateness.LatenessHistory.Lateness;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolCalendar.Period;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's absences and lateness, day by day: for the parent (UC-P3, NFR-2: readable without
 * training) and for the DM of the student's class or the admin (UC-D8). Attendance and lateness are
 * shown side by side, never merged (BR-8, BR-9); cancelled records are left out (BR-15). A student
 * outside the user's children or scope is not found (NFR-8, D17).
 */
@Service
class ChildHistory {

	/** Without dates: the last 90 days. */
	static final int DEFAULT_DAYS = 90;

	/** {@code recordId}: what the DM justifies or cancels (UC-D1, UC-D6). */
	record PeriodAbsence(UUID recordId, int periodNumber, LocalTime startTime, boolean justified) {
	}

	record DayLateness(UUID recordId, LocalTime arrivalTime, boolean justified) {
	}

	/** {@code lateness} null when the child was not late that day. */
	record Day(LocalDate date, List<PeriodAbsence> absences, DayLateness lateness) {
	}

	record Totals(int absentPeriods, int unjustifiedAbsentPeriods, int lateness, int unjustifiedLateness) {
	}

	/** The parent's view. */
	record History(UUID studentId, String firstName, LocalDate from, LocalDate to, Totals totals, List<Day> days) {
	}

	/** The staff view (UC-D8): who the student is, as staff recognise them (NFR-9). */
	record StudentHistory(UUID studentId, String matricule, String firstName, String lastName, UUID classId,
			String className, LocalDate from, LocalDate to, Totals totals, List<Day> days) {
	}

	private record Built(Totals totals, List<Day> days) {
	}

	private final Students students;

	private final SchoolClasses classes;

	private final AttendanceHistory attendance;

	private final LatenessHistory latenessHistory;

	private final SchoolCalendar calendar;

	private final Clock clock;

	ChildHistory(Students students, SchoolClasses classes, AttendanceHistory attendance,
			LatenessHistory latenessHistory, SchoolCalendar calendar, Clock clock) {
		this.students = students;
		this.classes = classes;
		this.attendance = attendance;
		this.latenessHistory = latenessHistory;
		this.calendar = calendar;
		this.clock = clock;
	}

	/** UC-P3. {@code to} defaults to today in the school's time zone, {@code from} to 90 days before. */
	@Transactional(readOnly = true)
	History of(CurrentUser user, UUID studentId, LocalDate from, LocalDate to) {
		if (user.schoolId() == null || !user.has(Role.PARENT)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		if (!user.children().contains(studentId)) {
			throw ApiException.notFound("not_found");
		}
		Card child = students.cards(school, List.of(studentId)).get(studentId);
		if (child == null) {
			throw ApiException.notFound("not_found");
		}
		DateRange range = range(school, from, to);
		Built built = build(school, studentId, range);
		return new History(studentId, child.firstName(), range.from(), range.to(), built.totals(), built.days());
	}

	/**
	 * UC-D8: the DM of the student's current class (D5), or the admin for the whole school. Same
	 * dates as {@link #of}.
	 */
	@Transactional(readOnly = true)
	StudentHistory ofStudent(CurrentUser user, UUID studentId, LocalDate from, LocalDate to) {
		if (user.schoolId() == null || !user.hasAny(Role.ADMIN, Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		Card card = students.cards(school, List.of(studentId)).get(studentId);
		if (card == null || !(user.has(Role.ADMIN) || user.classesAs(Role.DM).contains(card.classId()))) {
			throw ApiException.notFound("not_found");
		}
		DateRange range = range(school, from, to);
		Built built = build(school, studentId, range);
		String className = classes.names(school, Set.of(card.classId())).get(card.classId());
		return new StudentHistory(studentId, card.matricule(), card.firstName(), card.lastName(), card.classId(),
				className, range.from(), range.to(), built.totals(), built.days());
	}

	private DateRange range(UUID school, LocalDate from, LocalDate to) {
		return DateRange.of(from, to, clock.instant().atZone(calendar.zone(school)).toLocalDate(), DEFAULT_DAYS);
	}

	private Built build(UUID school, UUID studentId, DateRange range) {
		List<Absence> absences = attendance.absences(school, studentId, range.from(), range.to());
		List<Lateness> lateness = latenessHistory.lateness(school, studentId, range.from(), range.to());
		Map<UUID, Period> periods = new HashMap<>();
		Map<LocalDate, List<PeriodAbsence>> absentByDay = new TreeMap<>(Comparator.reverseOrder());
		for (Absence absence : absences) {
			Period period = periods.computeIfAbsent(absence.periodId(), id -> calendar.period(school, id));
			absentByDay.computeIfAbsent(absence.date(), d -> new ArrayList<>())
				.add(new PeriodAbsence(absence.recordId(), period.number(), period.startTime(), absence.justified()));
		}
		Map<LocalDate, DayLateness> lateByDay = new HashMap<>();
		for (Lateness late : lateness) {
			lateByDay.put(late.date(), new DayLateness(late.recordId(), late.arrivalTime(), late.justified()));
		}
		TreeMap<LocalDate, Day> days = new TreeMap<>(Comparator.reverseOrder());
		absentByDay.forEach((date, list) -> {
			list.sort(Comparator.comparingInt(PeriodAbsence::periodNumber));
			days.put(date, new Day(date, List.copyOf(list), lateByDay.get(date)));
		});
		lateByDay.forEach((date, late) -> days.putIfAbsent(date, new Day(date, List.of(), late)));

		Totals totals = new Totals(absences.size(), (int) absences.stream().filter(a -> !a.justified()).count(),
				lateness.size(), (int) lateness.stream().filter(l -> !l.justified()).count());
		return new Built(totals, List.copyOf(days.values()));
	}

}
