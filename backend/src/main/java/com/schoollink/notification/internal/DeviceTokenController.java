package com.schoollink.notification.internal;

import com.schoollink.notification.internal.DeviceRegistration.DeviceTokenRequest;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** BR-21: the app's FCM token, so that pushes reach this phone. Idempotent. */
@RestController
class DeviceTokenController {

	private final DeviceRegistration registration;

	DeviceTokenController(DeviceRegistration registration) {
		this.registration = registration;
	}

	@PutMapping("/api/v1/me/device-token")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void register(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeviceTokenRequest body) {
		registration.register(user, body);
	}

}
