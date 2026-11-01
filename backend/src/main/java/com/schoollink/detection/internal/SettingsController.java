package com.schoollink.detection.internal;

import com.schoollink.detection.internal.ThresholdSettings.SettingsRequest;
import com.schoollink.detection.internal.ThresholdSettings.SettingsView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** UC-D7: thresholds, school start time and SMS digest times (BR-7, BR-16, BR-22). */
@RestController
class SettingsController {

	private final ThresholdSettings settings;

	SettingsController(ThresholdSettings settings) {
		this.settings = settings;
	}

	@GetMapping("/api/v1/settings/thresholds")
	SettingsView get(@AuthenticationPrincipal CurrentUser user) {
		return settings.get(user);
	}

	@PutMapping("/api/v1/settings/thresholds")
	SettingsView put(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SettingsRequest body) {
		return settings.update(user, body);
	}

}
