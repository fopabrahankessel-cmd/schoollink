package com.schoollink.people.internal;

import com.schoollink.people.internal.GuardianAdmin.GuardianDetails;
import com.schoollink.people.internal.GuardianAdmin.GuardianUpdate;
import com.schoollink.people.internal.GuardianAdmin.LinkUpdate;
import com.schoollink.people.internal.GuardianDirectory.GuardianView;
import com.schoollink.people.internal.StudentAdmin.GuardianRequest;
import com.schoollink.people.internal.StudentAdmin.StudentView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-A4: the guardians of a class and whether they have an app account, for the admin. D43: the
 * admin changes a guardian, and which guardians a student has. A phone is in the body, never in a
 * URL (NFR-9). Access rules are in {@link GuardianDirectory} and {@link GuardianAdmin}.
 */
@RestController
class GuardianController {

	private final GuardianDirectory directory;

	private final GuardianAdmin guardians;

	GuardianController(GuardianDirectory directory, GuardianAdmin guardians) {
		this.directory = directory;
		this.guardians = guardians;
	}

	@GetMapping("/api/v1/guardians")
	List<GuardianView> list(@AuthenticationPrincipal CurrentUser user, @RequestParam UUID classId) {
		return directory.forClass(user, classId);
	}

	@PutMapping("/api/v1/guardians/{id}")
	GuardianDetails update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody GuardianUpdate body) {
		return guardians.update(user, id, body);
	}

	@PostMapping("/api/v1/students/{studentId}/guardians")
	@ResponseStatus(HttpStatus.CREATED)
	StudentView add(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID studentId,
			@Valid @RequestBody GuardianRequest body) {
		return guardians.add(user, studentId, body);
	}

	@PutMapping("/api/v1/students/{studentId}/guardians/{guardianId}")
	StudentView updateLink(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID studentId,
			@PathVariable UUID guardianId, @Valid @RequestBody LinkUpdate body) {
		return guardians.updateLink(user, studentId, guardianId, body);
	}

	/** {@code primary}: the guardian who becomes the primary, when the one removed was. */
	@DeleteMapping("/api/v1/students/{studentId}/guardians/{guardianId}")
	StudentView remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID studentId,
			@PathVariable UUID guardianId, @RequestParam(required = false) UUID primary) {
		return guardians.remove(user, studentId, guardianId, primary);
	}

}
