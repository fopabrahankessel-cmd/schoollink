package com.schoollink.people.internal;

import com.schoollink.people.internal.InvitationService.ClassIssuedView;
import com.schoollink.people.internal.InvitationService.IssuedCode;
import com.schoollink.shared.security.CurrentUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-A4 invitation codes (SPEC §9 {@code POST /guardians/{id}/invitations}), per guardian or per class. */
@RestController
class InvitationController {

	private final InvitationService invitations;

	InvitationController(InvitationService invitations) {
		this.invitations = invitations;
	}

	@PostMapping("/api/v1/guardians/{id}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	IssuedCode issueForGuardian(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return invitations.issueForGuardian(user, id);
	}

	@PostMapping("/api/v1/classes/{id}/invitations")
	@ResponseStatus(HttpStatus.CREATED)
	ClassIssuedView issueForClass(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return invitations.issueForClass(user, id);
	}

}
