package com.schoollink.lateness.internal;

import com.schoollink.lateness.internal.LatenessQueries.GateStudent;
import com.schoollink.lateness.internal.LatenessQueries.LatenessDay;
import com.schoollink.lateness.internal.LatenessRecorder.LatenessRequest;
import com.schoollink.lateness.internal.LatenessRecorder.LatenessView;
import com.schoollink.lateness.internal.LatenessRecorder.Saved;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-AD1, UC-AD2: lateness at the gate (BR-7 to BR-9). */
@RestController
class LatenessController {

	private final LatenessRecorder recorder;

	private final LatenessQueries queries;

	private final LatenessCancellation cancellation;

	LatenessController(LatenessRecorder recorder, LatenessQueries queries, LatenessCancellation cancellation) {
		this.recorder = recorder;
		this.queries = queries;
		this.cancellation = cancellation;
	}

	/** {@code q}: part of a name or the start of a matricule; omitted, the whole scope (offline copy). */
	@GetMapping("/api/v1/lateness/students")
	List<GateStudent> students(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) String q) {
		return queries.gateStudents(user, q);
	}

	/** {@code date} (ISO) defaults to today in the school's time zone. */
	@GetMapping("/api/v1/lateness")
	LatenessDay day(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return queries.day(user, date);
	}

	/** 201 when stored now, 200 when the device replayed a record already stored. */
	@PutMapping("/api/v1/lateness/{id}")
	ResponseEntity<LatenessView> save(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody LatenessRequest body) {
		Saved saved = recorder.save(user, id, body);
		return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.lateness());
	}

	/** D11: the DM cancels a lateness recorded by mistake; the parent who was told gets a correction. */
	@PostMapping("/api/v1/lateness/{id}/cancel")
	LatenessCancellation.CancelledView cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody LatenessCancellation.CancelRequest body) {
		return cancellation.cancel(user, id, body);
	}

}
