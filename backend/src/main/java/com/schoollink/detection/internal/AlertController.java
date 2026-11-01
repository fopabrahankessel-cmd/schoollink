package com.schoollink.detection.internal;

import com.schoollink.detection.internal.Alerts.AlertView;
import com.schoollink.detection.internal.Alerts.DecisionRequest;
import com.schoollink.detection.internal.Alerts.Status;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-D2: risk alerts and the DM's decisions (BR-18). */
@RestController
class AlertController {

	private final Alerts alerts;

	AlertController(Alerts alerts) {
		this.alerts = alerts;
	}

	/** {@code status}: {@code open} (the default) or {@code closed}. */
	@GetMapping("/api/v1/alerts")
	List<AlertView> list(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(defaultValue = "open") String status) {
		Status wanted = switch (status.toLowerCase(Locale.ROOT)) {
			case "open" -> Status.OPEN;
			case "closed" -> Status.CLOSED;
			default -> throw ApiException.invalid("request.malformed", "status");
		};
		return alerts.list(user, wanted);
	}

	@PostMapping("/api/v1/alerts/{id}/decision")
	AlertView decide(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody DecisionRequest body) {
		return alerts.decide(user, id, body);
	}

}
