package com.schoollink.reporting.internal;

import com.schoollink.reporting.internal.Dashboards.SchoolDashboard;
import com.schoollink.reporting.internal.Dashboards.ScopeDashboard;
import com.schoollink.shared.security.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-A6: the school dashboard (admin); UC-D8: the scope dashboard (DM). {@code from} and {@code to}
 * (ISO dates, inclusive): by default the last 30 days (D34).
 */
@RestController
class DashboardController {

	private final Dashboards dashboards;

	DashboardController(Dashboards dashboards) {
		this.dashboards = dashboards;
	}

	@GetMapping("/api/v1/dashboard/school")
	SchoolDashboard school(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return dashboards.school(user, from, to);
	}

	@GetMapping("/api/v1/dashboard/scope")
	ScopeDashboard scope(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return dashboards.scope(user, from, to);
	}

}
