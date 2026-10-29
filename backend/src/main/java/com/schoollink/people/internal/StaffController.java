package com.schoollink.people.internal;

import com.schoollink.people.internal.Staff.CreatedStaff;
import com.schoollink.people.internal.Staff.RoleRequest;
import com.schoollink.people.internal.Staff.RoleView;
import com.schoollink.people.internal.Staff.StaffRequest;
import com.schoollink.people.internal.Staff.StaffView;
import com.schoollink.people.internal.Staff.StatusRequest;
import com.schoollink.people.internal.Staff.TeachingRequest;
import com.schoollink.people.internal.Staff.TeachingView;
import com.schoollink.people.internal.Staff.TemporaryPassword;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-A2 staff accounts and assignments API. Access rules are in {@link StaffService}. */
@RestController
@RequestMapping("/api/v1/staff")
class StaffController {

	private final StaffService staff;

	StaffController(StaffService staff) {
		this.staff = staff;
	}

	@GetMapping
	List<?> list(@AuthenticationPrincipal CurrentUser user) {
		return staff.list(user);
	}

	@GetMapping("/{id}")
	StaffView get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return staff.get(user, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CreatedStaff create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody StaffRequest body) {
		return staff.create(user, body);
	}

	@PutMapping("/{id}")
	StaffView update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody StaffRequest body) {
		return staff.update(user, id, body);
	}

	@PutMapping("/{id}/status")
	StaffView status(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody StatusRequest body) {
		return staff.setStatus(user, id, body.status());
	}

	@PostMapping("/{id}/password-reset")
	TemporaryPassword resetPassword(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return staff.resetPassword(user, id);
	}

	@PostMapping("/{id}/roles")
	@ResponseStatus(HttpStatus.CREATED)
	RoleView addRole(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody RoleRequest body) {
		return staff.addRole(user, id, body);
	}

	@DeleteMapping("/{id}/roles/{roleId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeRole(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id, @PathVariable UUID roleId) {
		staff.removeRole(user, id, roleId);
	}

	@PostMapping("/{id}/teaching")
	@ResponseStatus(HttpStatus.CREATED)
	TeachingView addTeaching(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody TeachingRequest body) {
		return staff.addTeaching(user, id, body);
	}

	@DeleteMapping("/{id}/teaching/{teachingId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeTeaching(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@PathVariable UUID teachingId) {
		staff.removeTeaching(user, id, teachingId);
	}

}
