package com.schoollink.school;

import java.time.LocalTime;
import java.util.UUID;

/** The school's times the DM sets (UC-D7): start of the day and the SMS digest runs (school module API). */
public interface SchoolTimes {

	Times times(UUID schoolId);

	void update(UUID schoolId, Times times);

	/**
	 * @param startTime BR-7: a student entering after it is late
	 * @param digestFirst BR-22: the first SMS digest run (default 13:00)
	 * @param digestSecond D6: the second run, for what is new since the first (default 17:00); {@code null}: none
	 */
	record Times(LocalTime startTime, LocalTime digestFirst, LocalTime digestSecond) {
	}

}
