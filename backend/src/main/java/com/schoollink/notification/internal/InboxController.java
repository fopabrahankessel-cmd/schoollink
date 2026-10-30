package com.schoollink.notification.internal;

import com.schoollink.notification.internal.Inbox.Entry;
import com.schoollink.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-P2: the parent's inbox and the open receipt (BR-24). */
@RestController
class InboxController {

	private final Inbox inbox;

	InboxController(Inbox inbox) {
		this.inbox = inbox;
	}

	/** {@code before}: the id of the last entry already shown, for the next page. */
	@GetMapping("/api/v1/me/notifications")
	List<Entry> notifications(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) UUID before, @RequestParam(required = false) Integer limit) {
		return inbox.entries(user, before, limit);
	}

	@PostMapping("/api/v1/notifications/{id}/opened")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void opened(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		inbox.opened(user, id);
	}

}
