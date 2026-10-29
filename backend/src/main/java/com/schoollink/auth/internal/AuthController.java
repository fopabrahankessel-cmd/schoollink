package com.schoollink.auth.internal;

import com.schoollink.school.SchoolSummary;
import com.schoollink.school.Schools;
import com.schoollink.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** SPEC §9 auth endpoints. Phone numbers travel only in request bodies, never in URLs (NFR-9). */
@RestController
class AuthController {

	private final AuthService auth;

	private final Schools schools;

	AuthController(AuthService auth, Schools schools) {
		this.auth = auth;
		this.schools = schools;
	}

	/**
	 * The client IP for rate limiting (NFR-7) is the real client: Tomcat takes it from
	 * X-Forwarded-For only when the request comes from a trusted proxy (TRUSTED_PROXIES).
	 */
	@PostMapping("/api/v1/auth/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return auth.login(request.schoolId(), request.login(), request.password(), http.getRemoteAddr());
	}

	@PostMapping("/api/v1/auth/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return auth.refresh(request.refreshToken());
	}

	/** D16: the school picker shown before login. */
	@GetMapping("/api/v1/auth/schools")
	List<SchoolSummary> schools() {
		return schools.listForPicker();
	}

	@GetMapping("/api/v1/me")
	UserInfo me(@AuthenticationPrincipal CurrentUser user) {
		return UserInfo.of(user);
	}

	/** @param schoolId from the school picker (D16); may be omitted */
	record LoginRequest(UUID schoolId, @NotBlank String login, @NotBlank String password) {
	}

	record RefreshRequest(@NotBlank String refreshToken) {
	}

}
