package com.schoollink.attendance.internal;

import com.schoollink.attendance.internal.AbsenceCancellation.CancelRequest;
import com.schoollink.attendance.internal.AbsenceCancellation.CancelledView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** UC-D6: the DM cancels an absence recorded by mistake (BR-13 to BR-15). */
@RestController
class RecordCancelController {

	private final AbsenceCancellation cancellation;

	RecordCancelController(AbsenceCancellation cancellation) {
		this.cancellation = cancellation;
	}

	@PostMapping("/api/v1/records/{id}/cancel")
	CancelledView cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody CancelRequest body) {
		return cancellation.cancel(user, id, body);
	}

}
