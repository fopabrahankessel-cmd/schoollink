package com.schoollink.notification.internal;

import com.schoollink.people.StaffCredentialsReset;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * D18, NFR-9: a password reset or a disabled account ends the user's sessions, and their phones
 * stop receiving pushes too, in the same transaction. The app registers its token again at the
 * next sign-in.
 */
@Component
class DeviceTokenRevoker {

	private final DeviceTokens devices;

	DeviceTokenRevoker(DeviceTokens devices) {
		this.devices = devices;
	}

	@EventListener
	void on(StaffCredentialsReset event) {
		devices.forgetUser(event.userId());
	}

}
