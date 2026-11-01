package com.schoollink.detection.internal;

import com.schoollink.attendance.AttendanceHistory.Absence;
import com.schoollink.detection.RuleType;
import com.schoollink.detection.internal.Rules.Facts;
import com.schoollink.detection.internal.Rules.Finding;
import com.schoollink.school.SchoolCalendar.Term;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-16, D2, D3, D29: each rule on a fixed calendar. November 2026: Monday 2 to Friday 6, Monday 9
 * to Friday 13 with Wednesday 11 a holiday (D10), Monday 16 to Friday 20. The term runs from 7
 * September to 18 December. Three periods every school day.
 */
class RulesTests {

	static final Term TERM = new Term(UUID.randomUUID(), date(9, 7), date(12, 18));

	static final LocalDate HOLIDAY = date(11, 11);

	static final UUID P1 = UUID.randomUUID();

	static final UUID P2 = UUID.randomUUID();

	static final UUID P3 = UUID.randomUUID();

	static final Predicate<LocalDate> SCHOOL_DAY = d -> d.getDayOfWeek().getValue() <= 5 && !d.equals(HOLIDAY)
			&& !d.isBefore(TERM.startDate()) && !d.isAfter(TERM.endDate());

	final List<Absence> absences = new ArrayList<>();

	final Set<LocalDate> lateDays = new HashSet<>();

	// Full-day absence (D2)

	@Test
	void absentInEveryPeriodOfTheDayIsAFullDay() {
		absent(date(11, 2), P1, P2, P3);

		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 2), null, 1)).hasValueSatisfying(
				f -> assertThat(f.evidence()).isEqualTo(Map.of("dates", List.of("2026-11-02"))));
	}

	@Test
	void aMissingPeriodIsNotAFullDay() { // the last period not locked yet, or the student came back
		absent(date(11, 2), P1, P2);

		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 2), null, 1)).isEmpty();
	}

	@Test
	void aDayWithoutTimetableOrSchoolIsNeverFull() {
		absent(date(11, 7), P1, P2, P3); // a Saturday
		absent(HOLIDAY, P1, P2, P3);

		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 7), null, 1)).isEmpty();
		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, HOLIDAY, null, 1)).isEmpty();
	}

	@Test
	void fullDaysInARowSkipTheWeekend() {
		absent(date(11, 6), P1, P2, P3); // Friday
		absent(date(11, 9), P1, P2, P3); // Monday

		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 9), null, 2)).hasValueSatisfying(
				f -> assertThat(f.evidence()).isEqualTo(Map.of("dates", List.of("2026-11-06", "2026-11-09"))));
	}

	@Test
	void aSchoolDayPresentBreaksTheRun() {
		absent(date(11, 5), P1, P2, P3);
		absent(date(11, 6), P1); // came for part of Friday
		absent(date(11, 9), P1, P2, P3);

		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 9), null, 2)).isEmpty();
	}

	@Test
	void afterAnAlertOnlyLaterDaysCount() { // D29
		absent(date(11, 2), P1, P2, P3);
		absent(date(11, 3), P1, P2, P3);

		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 2), date(11, 2), 1)).isEmpty();
		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 3), date(11, 2), 1)).isPresent();
		assertThat(evaluate(RuleType.FULL_DAY_ABSENCE, date(11, 3), date(11, 2), 2)).isEmpty();
	}

	// Consecutive lateness (D3)

	@Test
	void fiveSchoolDaysLateInARow() {
		late(date(11, 2), date(11, 3), date(11, 4), date(11, 5), date(11, 6));

		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 6), null, 5)).hasValueSatisfying(
				f -> assertThat(f.evidence()).isEqualTo(Map.of("dates",
						List.of("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06"))));
		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 5), null, 5)).isEmpty();
	}

	@Test
	void aSchoolDayOnTimeResetsTheStreak() {
		late(date(11, 2), date(11, 3), date(11, 5), date(11, 6), date(11, 9), date(11, 10)); // on time on the 4th

		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 10), null, 5)).isEmpty();
		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 10), null, 4)).isPresent();
	}

	@Test
	void weekendsAndHolidaysNeitherCountNorBreak() { // D3, D10
		late(date(11, 9), date(11, 10), date(11, 12), date(11, 13), date(11, 16));

		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 16), null, 5)).hasValueSatisfying(
				f -> assertThat(f.evidence()).isEqualTo(Map.of("dates",
						List.of("2026-11-09", "2026-11-10", "2026-11-12", "2026-11-13", "2026-11-16"))));
	}

	@Test
	void theStreakMustReachTheDate() { // evaluated on a day without lateness: nothing
		late(date(11, 2), date(11, 3), date(11, 4), date(11, 5), date(11, 6));

		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 9), null, 5)).isEmpty();
	}

	@Test
	void afterAnAlertTheStreakStartsAgain() { // D29
		late(date(11, 2), date(11, 3), date(11, 4), date(11, 5), date(11, 6), date(11, 9), date(11, 10), date(11, 12),
				date(11, 13), date(11, 16));

		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 13), date(11, 6), 5)).isEmpty();
		assertThat(evaluate(RuleType.CONSECUTIVE_LATENESS, date(11, 16), date(11, 6), 5)).hasValueSatisfying(
				f -> assertThat(f.evidence()).isEqualTo(Map.of("dates",
						List.of("2026-11-09", "2026-11-10", "2026-11-12", "2026-11-13", "2026-11-16"))));
	}

	// Unjustified absences (SPEC §2 Q1)

	@Test
	void fifteenUnjustifiedPeriodsInTheTerm() {
		for (int day = 2; day <= 6; day++) {
			absent(date(11, day), P1, P2, P3);
		}

		assertThat(evaluate(RuleType.UNJUSTIFIED_ABSENCES, date(11, 6), null, 15)).hasValueSatisfying(
				f -> assertThat(f.evidence()).containsEntry("periods", 15L)
					.containsEntry("from", "2026-09-07")
					.containsEntry("to", "2026-11-06"));
		assertThat(evaluate(RuleType.UNJUSTIFIED_ABSENCES, date(11, 5), null, 15)).isEmpty();
	}

	@Test
	void justifiedPeriodsAndOtherTermsDoNotCount() {
		for (int day = 2; day <= 6; day++) {
			absent(date(11, day), P1, P2, P3);
		}
		absences.set(0, new Absence(UUID.randomUUID(), date(11, 2), P1, true));
		absent(date(9, 4), P1); // before the term

		assertThat(evaluate(RuleType.UNJUSTIFIED_ABSENCES, date(11, 6), null, 15)).isEmpty();
		assertThat(evaluate(RuleType.UNJUSTIFIED_ABSENCES, date(11, 6), null, 14)).isPresent();
	}

	@Test
	void afterAnAlertOnlyLaterPeriodsCount() { // D29
		for (int day = 2; day <= 6; day++) {
			absent(date(11, day), P1, P2, P3);
		}

		assertThat(evaluate(RuleType.UNJUSTIFIED_ABSENCES, date(11, 6), date(11, 4), 6)).hasValueSatisfying(
				f -> assertThat(f.evidence()).containsEntry("periods", 6L).containsEntry("from", "2026-11-05"));
		assertThat(evaluate(RuleType.UNJUSTIFIED_ABSENCES, date(11, 6), date(11, 4), 7)).isEmpty();
	}

	@Test
	void betweenTermsNothingIsCounted() {
		absent(date(12, 21), P1, P2, P3);

		Facts facts = new Facts(date(12, 21), null, SCHOOL_DAY, d -> Set.of(P1, P2, P3), absences, lateDays, null);
		assertThat(Rules.unjustifiedAbsences(facts, 1)).isEmpty();
	}

	private Optional<Finding> evaluate(RuleType type, LocalDate date, LocalDate after, int count) {
		Facts facts = new Facts(date, after, SCHOOL_DAY,
				d -> SCHOOL_DAY.test(d) ? Set.of(P1, P2, P3) : Set.of(), absences, lateDays, TERM);
		return Rules.evaluate(type, facts, count);
	}

	private void absent(LocalDate date, UUID... periods) {
		for (UUID period : periods) {
			absences.add(new Absence(UUID.randomUUID(), date, period, false));
		}
	}

	private void late(LocalDate... dates) {
		lateDays.addAll(List.of(dates));
	}

	private static LocalDate date(int month, int day) {
		return LocalDate.of(2026, month, day);
	}

}
