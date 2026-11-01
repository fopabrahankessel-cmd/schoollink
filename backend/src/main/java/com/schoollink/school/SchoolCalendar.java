package com.schoollink.school;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** School days and the timetable (school module API). Everything is within {@code schoolId} only (NFR-8). */
public interface SchoolCalendar {

	/** The school's time zone: "today" and "the current period" are read in it. */
	ZoneId zone(UUID schoolId);

	/** BR-7: a student entering after this time is late (default 07:30, set per school). */
	LocalTime startTime(UUID schoolId);

	boolean periodExists(UUID schoolId, UUID periodId);

	/** The period's number and times (for messages: "absent in period 2"). */
	Period period(UUID schoolId, UUID periodId);

	/** D3, D10: Monday to Friday, inside a term, outside that term's holidays. */
	boolean isSchoolDay(UUID schoolId, LocalDate date);

	/**
	 * D1: the teacher's timetabled lessons on that date, by period number: the slots of that weekday
	 * in the classes of the academic year the date's term belongs to. Empty when the date is not a
	 * school day.
	 */
	List<Lesson> lessonsOf(UUID schoolId, UUID teacherId, LocalDate date);

	/**
	 * D2: the periods timetabled for the class on that date, whoever teaches them; empty when the
	 * date is not a school day.
	 */
	Set<UUID> periodsOf(UUID schoolId, UUID classId, LocalDate date);

	/** The term holding the date, its holidays included; empty between terms. */
	Optional<Term> termOf(UUID schoolId, LocalDate date);

	record Term(UUID id, LocalDate startDate, LocalDate endDate) {
	}

	record Period(UUID id, int number, LocalTime startTime, LocalTime endTime) {
	}

	record Lesson(UUID classId, String className, UUID periodId, int periodNumber, LocalTime startTime,
			LocalTime endTime, String subject) {
	}

}
