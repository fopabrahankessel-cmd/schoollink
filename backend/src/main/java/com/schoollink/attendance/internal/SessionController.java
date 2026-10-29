package com.schoollink.attendance.internal;

import com.schoollink.attendance.internal.SessionRecorder.Saved;
import com.schoollink.attendance.internal.SessionRecorder.SessionRequest;
import com.schoollink.attendance.internal.SessionRecorder.SessionView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** UC-T1, UC-T2: a confirmed session from the teacher's device, idempotent on its UUID (BR-4, BR-6). */
@RestController
class SessionController {

	private final SessionRecorder recorder;

	SessionController(SessionRecorder recorder) {
		this.recorder = recorder;
	}

	/** 201 when stored now, 200 when the device replayed a session already stored. */
	@PutMapping("/api/v1/sessions/{id}")
	ResponseEntity<SessionView> save(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody SessionRequest body) {
		Saved saved = recorder.save(user, id, body);
		return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.session());
	}

}
