package com.schoollink.detection.internal;

import com.schoollink.attendance.AttendanceHistory.Absence;
import com.schoollink.detection.RuleType;
import com.schoollink.school.SchoolCalendar.Term;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * BR-16, D9: one evaluator per rule type, on one student's facts as of one date. Pure: no database,
 * no clock, so each rule is tested on a fixed calendar. A rule only finds; raising the alert is the
 * caller's (BR-17), and nothing here ever decides for the DM (BR-18).
 */
final class Rules {

	/** How far back a streak is followed, in calendar days: 20 school days with holidays in between. */
	static final int LOOKBACK_DAYS = 120;

	/**
	 * One student's facts as of {@code date}.
	 *
	 * @param after D29: the previous alert of this rule ended on this day, so only later days count;
	 * {@code null} when there was none
	 * @param schoolDay D3: Monday to Friday, in a term, outside its holidays
	 * @param timetable D2: the periods timetabled for the student's class on a day
	 * @param absences live absences (BR-15), at least back to the term's start and {@link #LOOKBACK_DAYS}
	 * @param lateDays the days with a live lateness, back {@link #LOOKBACK_DAYS}
	 * @param term the term holding {@code date}; {@code null} between terms
	 */
	record Facts(LocalDate date, LocalDate after, Predicate<LocalDate> schoolDay,
			Function<LocalDate, Set<UUID>> timetable, List<Absence> absences, Set<LocalDate> lateDays, Term term) {
	}

	/** What a rule saw when it fired, kept with the alert for the DM. */
	record Finding(Map<String, Object> evidence) {
	}

	private Rules() {
	}

	static Optional<Finding> evaluate(RuleType type, Facts facts, int count) {
		return switch (type) {
			case FULL_DAY_ABSENCE -> fullDayAbsence(facts, count);
			case CONSECUTIVE_LATENESS -> consecutiveLateness(facts, count);
			case UNJUSTIFIED_ABSENCES -> unjustifiedAbsences(facts, count);
		};
	}

	/**
	 * D2: absent in every timetabled period of the day, on {@code count} school days in a row ending
	 * on the date (1: that day). Until the last period is locked, the day is not full.
	 */
	static Optional<Finding> fullDayAbsence(Facts facts, int count) {
		Map<LocalDate, Set<UUID>> absentPeriods = facts.absences()
			.stream()
			.collect(Collectors.groupingBy(Absence::date, Collectors.mapping(Absence::periodId, Collectors.toSet())));
		return streak(facts, count, day -> {
			Set<UUID> timetabled = facts.timetable().apply(day);
			return !timetabled.isEmpty() && absentPeriods.getOrDefault(day, Set.of()).containsAll(timetabled);
		});
	}

	/** D3: a lateness on {@code count} school days in a row ending on the date; a day without one resets it. */
	static Optional<Finding> consecutiveLateness(Facts facts, int count) {
		return streak(facts, count, facts.lateDays()::contains);
	}

	/**
	 * SPEC §2 Q1: at least {@code count} unjustified absent periods in the current term, up to the
	 * date; after a previous alert, only those after it (D29). A justification recorded later lowers
	 * the count but never closes an alert already raised (BR-19).
	 */
	static Optional<Finding> unjustifiedAbsences(Facts facts, int count) {
		if (facts.term() == null) {
			return Optional.empty();
		}
		LocalDate from = facts.term().startDate();
		if (facts.after() != null && !facts.after().isBefore(from)) {
			from = facts.after().plusDays(1);
		}
		LocalDate start = from;
		long periods = facts.absences()
			.stream()
			.filter(a -> !a.justified() && !a.date().isBefore(start) && !a.date().isAfter(facts.date()))
			.count();
		if (periods < count) {
			return Optional.empty();
		}
		Map<String, Object> evidence = new LinkedHashMap<>();
		evidence.put("periods", periods);
		evidence.put("from", start.toString());
		evidence.put("to", facts.date().toString());
		return Optional.of(new Finding(evidence));
	}

	/**
	 * {@code count} school days in a row ending on the date on which {@code holds}, all after the
	 * previous alert (D29). Days that are not school days (weekends, holidays) are skipped, not
	 * counted and not breaking the streak (D3).
	 */
	private static Optional<Finding> streak(Facts facts, int count, Predicate<LocalDate> holds) {
		if (!facts.schoolDay().test(facts.date())) {
			return Optional.empty();
		}
		List<LocalDate> days = new ArrayList<>();
		LocalDate day = facts.date();
		for (int i = 0; i < LOOKBACK_DAYS && days.size() < count; i++, day = day.minusDays(1)) {
			if (facts.after() != null && !day.isAfter(facts.after())) {
				break;
			}
			if (!facts.schoolDay().test(day)) {
				continue;
			}
			if (!holds.test(day)) {
				break;
			}
			days.add(day);
		}
		if (days.size() < count) {
			return Optional.empty();
		}
		Collections.reverse(days);
		return Optional.of(new Finding(Map.of("dates", days.stream().map(LocalDate::toString).toList())));
	}

}
