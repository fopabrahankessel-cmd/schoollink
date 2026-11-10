package com.schoollink.reporting.internal;

import com.schoollink.reporting.internal.DayAttendance.Day;
import com.schoollink.shared.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-D10, D40: the DM's day of attendance, period by period. */
@RestController
class DayAttendanceController {

	private final DayAttendance days;

	DayAttendanceController(DayAttendance days) {
		this.days = days;
	}

	/** {@code date} (ISO, optional): today at school when left out; never in the future. */
	@GetMapping("/api/v1/attendance/day")
	Day day(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return days.day(user, date);
	}

}
