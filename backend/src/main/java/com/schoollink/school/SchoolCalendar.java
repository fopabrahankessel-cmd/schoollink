package com.schoollink.school;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** School days and the timetable (school module API). Everything is within {@code schoolId} only (NFR-8). */
public interface SchoolCalendar {

	/** The school's time zone: "today" and "the current period" are read in it. */
	ZoneId zone(UUID schoolId);

	/** D3, D10: Monday to Friday, inside a term, outside that term's holidays. */
	boolean isSchoolDay(UUID schoolId, LocalDate date);

	/**
	 * D1: the teacher's timetabled lessons on that date, by period number: the slots of that weekday
	 * in the classes of the academic year the date's term belongs to. Empty when the date is not a
	 * school day.
	 */
	List<Lesson> lessonsOf(UUID schoolId, UUID teacherId, LocalDate date);

	record Lesson(UUID classId, String className, UUID periodId, int periodNumber, LocalTime startTime,
			LocalTime endTime, String subject) {
	}

}
