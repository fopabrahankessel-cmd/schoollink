package com.schoollink.auth.internal;

import com.schoollink.school.SchoolSummary;
import com.schoollink.school.Schools;
import com.schoollink.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** SPEC §9 auth endpoints. Phone numbers travel only in request bodies, never in URLs (NFR-9). */
@RestController
class AuthController {

	private final AuthService auth;

	private final ParentActivation activation;

	private final Schools schools;

	AuthController(AuthService auth, ParentActivation activation, Schools schools) {
		this.auth = auth;
		this.activation = activation;
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

	/** UC-P1 step 1: invitation code + phone; sends the one-time code by SMS. */
	@PostMapping("/api/v1/auth/parent/activate")
	ParentActivation.Started activate(@Valid @RequestBody ActivateRequest request, HttpServletRequest http) {
		return activation.start(request.code(), request.phone(), http.getRemoteAddr());
	}

	/** UC-P1 step 2: the one-time code; returns the parent's tokens. */
	@PostMapping("/api/v1/auth/parent/verify")
	TokenResponse verify(@Valid @RequestBody VerifyRequest request, HttpServletRequest http) {
		return activation.verify(request.activationId(), request.otp(), http.getRemoteAddr());
	}

	@PostMapping("/api/v1/auth/refresh")
	TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return auth.refresh(request.refreshToken());
	}

	/** Sign-out: revokes the session of this refresh token. No access token needed, it may have expired. */
	@PostMapping("/api/v1/auth/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@Valid @RequestBody RefreshRequest request) {
		auth.logout(request.refreshToken());
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

	/** D18: allowed even while the password is temporary; returns a fresh token pair. */
	@PostMapping("/api/v1/me/password")
	TokenResponse changePassword(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody ChangePasswordRequest request) {
		return auth.changePassword(user.userId(), request.currentPassword(), request.newPassword());
	}

	/** NFR-7: BCrypt reads at most 72 bytes, so longer passwords are refused rather than truncated. */
	record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
	}

	/** @param schoolId from the school picker (D16); may be omitted */
	record LoginRequest(UUID schoolId, @NotBlank String login, @NotBlank String password) {
	}

	record RefreshRequest(@NotBlank String refreshToken) {
	}

	/** {@code code}: as in the SMS ("K7QM-3XPA", spaces and case do not matter). */
	record ActivateRequest(@NotBlank @Size(max = 20) String code, @NotBlank @Size(max = 20) String phone) {
	}

	record VerifyRequest(@NotNull UUID activationId, @NotBlank @Size(max = 10) String otp) {
	}

}
