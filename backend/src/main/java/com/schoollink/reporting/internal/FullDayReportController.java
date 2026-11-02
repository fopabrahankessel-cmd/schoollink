package com.schoollink.reporting.internal;

import com.schoollink.reporting.internal.FullDayReports.DefaultDate;
import com.schoollink.reporting.internal.FullDayReports.FullDayReport;
import com.schoollink.shared.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-D9, D36: the DM's full-day absence report; the phone draws and shares the PDF. */
@RestController
class FullDayReportController {

	private final FullDayReports reports;

	FullDayReportController(FullDayReports reports) {
		this.reports = reports;
	}

	/** {@code date} (ISO): a school day, not in the future; today only from 17:00 school time. */
	@GetMapping("/api/v1/reports/full-day-absences")
	FullDayReport report(@AuthenticationPrincipal CurrentUser user,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return reports.report(user, date);
	}

	/** The day the screen opens on: today from 17:00, otherwise the previous school day. */
	@GetMapping("/api/v1/reports/full-day-absences/default-date")
	DefaultDate defaultDate(@AuthenticationPrincipal CurrentUser user) {
		return reports.defaultDate(user);
	}

}
