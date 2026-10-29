package com.schoollink.attendance.internal;

import com.schoollink.attendance.internal.TeacherSessions.TeacherDay;
import com.schoollink.shared.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-T3: the teacher's sessions of the day and which are recorded (D1). */
@RestController
class TeacherSessionsController {

	private final TeacherSessions sessions;

	TeacherSessionsController(TeacherSessions sessions) {
		this.sessions = sessions;
	}

	/** {@code date} (ISO, e.g. 2026-11-02) defaults to today in the school's time zone. */
	@GetMapping("/api/v1/me/sessions")
	TeacherDay mySessions(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return sessions.forDay(user, date);
	}

}
