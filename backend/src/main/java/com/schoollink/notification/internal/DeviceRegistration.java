package com.schoollink.notification.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.notification.internal.DeviceTokens.Registered;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PUT /me/device-token} (BR-21): the app registers its FCM token at each sign-in and start.
 * App users only: teachers, assistant DMs, DMs, counsellors and parents; the admin and the
 * researcher work on the web (403). A new token, or one taken over from another user, is audited
 * (NFR-11); a refresh only updates {@code last_seen}. NFR-9: the token itself is never audited.
 */
@Service
class DeviceRegistration {

	/** {@code token}: as FCM gives it (letters, digits, {@code - _ : .}). Android only (NFR-4). */
	record DeviceTokenRequest(@NotBlank @Size(max = 4096) @Pattern(regexp = "[A-Za-z0-9_:.\\-]*") String token,
			@NotNull @Pattern(regexp = "ANDROID") String platform) {
	}

	private final DeviceTokens tokens;

	private final AuditLog audit;

	private final Clock clock;

	DeviceRegistration(DeviceTokens tokens, AuditLog audit, Clock clock) {
		this.tokens = tokens;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	void register(CurrentUser user, DeviceTokenRequest request) {
		if (user.schoolId() == null
				|| !user.hasAny(Role.TEACHER, Role.ASSISTANT_DM, Role.DM, Role.COUNSELLOR, Role.PARENT)) {
			throw ApiException.forbidden();
		}
		Registered registered = tokens.register(user.schoolId(), user.userId(), request.token(), request.platform(),
				clock.instant());
		if (registered.previousUser() == null) {
			audit.record(new AuditEntry(user.schoolId(), user.userId(), AuditAction.CREATE, "device_token",
					registered.id().toString(), null, Map.of("userId", user.userId(), "platform", request.platform())));
		}
		else if (!Objects.equals(registered.previousUser(), user.userId())) {
			audit.record(new AuditEntry(user.schoolId(), user.userId(), AuditAction.UPDATE, "device_token",
					registered.id().toString(), Map.of("userId", registered.previousUser()),
					Map.of("userId", user.userId(), "platform", request.platform())));
		}
	}

}
