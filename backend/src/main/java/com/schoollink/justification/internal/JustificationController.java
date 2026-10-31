package com.schoollink.justification.internal;

import com.schoollink.justification.internal.Justifications.JustificationRequest;
import com.schoollink.justification.internal.Justifications.JustificationView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-D1: the DM justifies absences and lateness (BR-10 to BR-12). */
@RestController
class JustificationController {

	private final Justifications justifications;

	JustificationController(Justifications justifications) {
		this.justifications = justifications;
	}

	@PostMapping("/api/v1/justifications")
	@ResponseStatus(HttpStatus.CREATED)
	JustificationView justify(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody JustificationRequest body) {
		return justifications.justify(user, body);
	}

}
