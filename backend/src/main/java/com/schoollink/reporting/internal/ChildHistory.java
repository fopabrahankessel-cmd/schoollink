package com.schoollink.reporting.internal;

import com.schoollink.attendance.AttendanceHistory;
import com.schoollink.attendance.AttendanceHistory.Absence;
import com.schoollink.lateness.LatenessHistory;
import com.schoollink.lateness.LatenessHistory.Lateness;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolCalendar.Period;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-P3: a child's absences and lateness, day by day, for the parent (NFR-2: readable without
 * training). Attendance and lateness are shown side by side, never merged (BR-8, BR-9); cancelled
 * records are left out (BR-15). A child who is not the parent's is not found (NFR-8, D17).
 */
@Service
class ChildHistory {

	/** Without dates: the last 90 days. */
	static final int DEFAULT_DAYS = 90;

	/** At most a school year and a bit. */
	static final int MAX_DAYS = 366;

	record PeriodAbsence(int periodNumber, LocalTime startTime, boolean justified) {
	}

	record DayLateness(LocalTime arrivalTime, boolean justified) {
	}

	/** {@code lateness} null when the child was not late that day. */
	record Day(LocalDate date, List<PeriodAbsence> absences, DayLateness lateness) {
	}

	record Totals(int absentPeriods, int unjustifiedAbsentPeriods, int lateness, int unjustifiedLateness) {
	}

	record History(UUID studentId, String firstName, LocalDate from, LocalDate to, Totals totals, List<Day> days) {
	}

	private final Students students;

	private final AttendanceHistory attendance;

	private final LatenessHistory latenessHistory;

	private final SchoolCalendar calendar;

	private final Clock clock;

	ChildHistory(Students students, AttendanceHistory attendance, LatenessHistory latenessHistory,
			SchoolCalendar calendar, Clock clock) {
		this.students = students;
		this.attendance = attendance;
		this.latenessHistory = latenessHistory;
		this.calendar = calendar;
		this.clock = clock;
	}

	/** {@code to} defaults to today in the school's time zone, {@code from} to 90 days before. */
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
		LocalDate end = to != null ? to : clock.instant().atZone(calendar.zone(school)).toLocalDate();
		LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
		if (start.isAfter(end)) {
			throw ApiException.invalid("history.range_invalid", "from", MAX_DAYS);
		}
		if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
			throw ApiException.invalid("history.range_invalid", "from", MAX_DAYS);
		}

		List<Absence> absences = attendance.absences(school, studentId, start, end);
		List<Lateness> lateness = latenessHistory.lateness(school, studentId, start, end);
		Map<UUID, Period> periods = new HashMap<>();
		Map<LocalDate, List<PeriodAbsence>> absentByDay = new TreeMap<>(Comparator.reverseOrder());
		for (Absence absence : absences) {
			Period period = periods.computeIfAbsent(absence.periodId(), id -> calendar.period(school, id));
			absentByDay.computeIfAbsent(absence.date(), d -> new ArrayList<>())
				.add(new PeriodAbsence(period.number(), period.startTime(), absence.justified()));
		}
		Map<LocalDate, DayLateness> lateByDay = new HashMap<>();
		for (Lateness late : lateness) {
			lateByDay.put(late.date(), new DayLateness(late.arrivalTime(), late.justified()));
		}
		TreeMap<LocalDate, Day> days = new TreeMap<>(Comparator.reverseOrder());
		absentByDay.forEach((date, list) -> {
			list.sort(Comparator.comparingInt(PeriodAbsence::periodNumber));
			days.put(date, new Day(date, List.copyOf(list), lateByDay.get(date)));
		});
		lateByDay.forEach((date, late) -> days.putIfAbsent(date, new Day(date, List.of(), late)));

		Totals totals = new Totals(absences.size(), (int) absences.stream().filter(a -> !a.justified()).count(),
				lateness.size(), (int) lateness.stream().filter(l -> !l.justified()).count());
		return new History(studentId, child.firstName(), start, end, totals, List.copyOf(days.values()));
	}

}
