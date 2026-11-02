package com.schoollink.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Attendance counted per day and class, for the dashboards (attendance module API, UC-A6, UC-D8).
 * Within {@code schoolId} only (NFR-8).
 */
public interface AttendanceCounts {

	/**
	 * One row per date and class with at least one recorded session between two dates, inclusive,
	 * for the given classes (the class of the session). A cancelled absence counts as present
	 * (BR-14, BR-15): the student was there.
	 */
	List<DayClass> daily(UUID schoolId, Set<UUID> classIds, LocalDate from, LocalDate to);

	/**
	 * @param studentPeriods one per student of each session, present or absent (BR-2, BR-5)
	 * @param unjustifiedAbsentPeriods the absences without a justification (BR-10, BR-11)
	 */
	record DayClass(LocalDate date, UUID classId, int sessions, int studentPeriods, int absentPeriods,
			int unjustifiedAbsentPeriods) {
	}

}
