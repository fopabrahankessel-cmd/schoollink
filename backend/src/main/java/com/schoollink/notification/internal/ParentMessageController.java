package com.schoollink.notification.internal;

import com.schoollink.notification.internal.ParentMessages.ClassOption;
import com.schoollink.notification.internal.ParentMessages.Draft;
import com.schoollink.notification.internal.ParentMessages.Kind;
import com.schoollink.notification.internal.ParentMessages.Preview;
import com.schoollink.notification.internal.ParentMessages.Sent;
import com.schoollink.notification.internal.ParentMessages.SentMessage;
import com.schoollink.shared.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-D4, UC-A5, D38: the DM's messages and the administrator's announcements to parents. */
@RestController
class ParentMessageController {

	private final ParentMessages messages;

	ParentMessageController(ParentMessages messages) {
		this.messages = messages;
	}

	@PostMapping("/api/v1/messages/preview")
	Preview previewMessage(@AuthenticationPrincipal CurrentUser user, @RequestBody Draft draft) {
		return messages.preview(user, Kind.DM_MESSAGE, draft);
	}

	/** 201 when sent now, 200 when this id was already sent. */
	@PostMapping("/api/v1/messages")
	ResponseEntity<Map<String, UUID>> sendMessage(@AuthenticationPrincipal CurrentUser user, @RequestBody Draft draft) {
		return answer(messages.send(user, Kind.DM_MESSAGE, draft));
	}

	@PostMapping("/api/v1/announcements/preview")
	Preview previewAnnouncement(@AuthenticationPrincipal CurrentUser user, @RequestBody Draft draft) {
		return messages.preview(user, Kind.ANNOUNCEMENT, draft);
	}

	@PostMapping("/api/v1/announcements")
	ResponseEntity<Map<String, UUID>> sendAnnouncement(@AuthenticationPrincipal CurrentUser user,
			@RequestBody Draft draft) {
		return answer(messages.send(user, Kind.ANNOUNCEMENT, draft));
	}

	@GetMapping("/api/v1/messages")
	List<SentMessage> sentMessages(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) UUID studentId) {
		// D41: with studentId, the student page's Messages tab
		return messages.sent(user, Kind.DM_MESSAGE, limit, studentId);
	}

	@GetMapping("/api/v1/announcements")
	List<SentMessage> sentAnnouncements(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) Integer limit) {
		return messages.sent(user, Kind.ANNOUNCEMENT, limit, null);
	}

	@GetMapping("/api/v1/messages/classes")
	List<ClassOption> classes(@AuthenticationPrincipal CurrentUser user) {
		return messages.classes(user);
	}

	private static ResponseEntity<Map<String, UUID>> answer(Sent sent) {
		return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(Map.of("id", sent.id()));
	}

}
