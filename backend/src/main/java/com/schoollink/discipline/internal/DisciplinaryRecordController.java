package com.schoollink.discipline.internal;

import com.schoollink.discipline.internal.DisciplinaryRecords.IncidentRequest;
import com.schoollink.discipline.internal.DisciplinaryRecords.RecordView;
import com.schoollink.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-D5, D37: a student's disciplinary records. */
@RestController
class DisciplinaryRecordController {

	private final DisciplinaryRecords records;

	DisciplinaryRecordController(DisciplinaryRecords records) {
		this.records = records;
	}

	/** {@code note} optional. */
	record CloseRequest(String note) {
	}

	record CancelRequest(String reason) {
	}

	@GetMapping("/api/v1/students/{id}/disciplinary-records")
	List<RecordView> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return records.list(user, id);
	}

	@PostMapping("/api/v1/students/{id}/disciplinary-records")
	@ResponseStatus(HttpStatus.CREATED)
	RecordView create(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody IncidentRequest body) {
		return records.create(user, id, body);
	}

	@PostMapping("/api/v1/disciplinary-records/{id}/close")
	RecordView close(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody(required = false) CloseRequest body) {
		return records.close(user, id, body == null ? null : body.note());
	}

	@PostMapping("/api/v1/disciplinary-records/{id}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @RequestBody CancelRequest body) {
		records.cancel(user, id, body.reason());
	}

}
