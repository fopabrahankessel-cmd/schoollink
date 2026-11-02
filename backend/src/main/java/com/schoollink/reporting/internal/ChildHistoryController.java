package com.schoollink.reporting.internal;

import com.schoollink.reporting.internal.ChildHistory.History;
import com.schoollink.reporting.internal.ChildHistory.StudentHistory;
import com.schoollink.shared.security.CurrentUser;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's attendance and lateness history: for the parent (UC-P3) and for the DM or the admin
 * (UC-D8). {@code from} and {@code to} (ISO dates, inclusive): by default the last 90 days.
 */
@RestController
class ChildHistoryController {

	private final ChildHistory history;

	ChildHistoryController(ChildHistory history) {
		this.history = history;
	}

	@GetMapping("/api/v1/children/{id}/history")
	History history(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return history.of(user, id, from, to);
	}

	@GetMapping("/api/v1/students/{id}/history")
	StudentHistory studentHistory(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return history.ofStudent(user, id, from, to);
	}

}
