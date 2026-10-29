package com.schoollink.people.internal;

import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Request and response shapes of the staff accounts API (UC-A2, D18). */
final class Staff {

	private Staff() {
	}

	/** What the admin sees. Never contains a password, temporary or not. */
	record StaffView(UUID id, String firstName, String lastName, String phone, String email, String language,
			String status, boolean passwordChangeRequired, List<RoleView> roles, List<TeachingView> teaching) {
	}

	/** What other staff see of a colleague in their scope (SPEC §3): name and roles, no contact details. */
	record StaffSummary(UUID id, String firstName, String lastName, List<String> roles) {
	}

	record RoleView(UUID id, Role role, UUID blockId) {
	}

	record TeachingView(UUID id, UUID classId, String subject) {
	}

	/** D18: the temporary password is returned once, here, and never stored in clear or audited. */
	record CreatedStaff(StaffView staff, String temporaryPassword) {
	}

	record TemporaryPassword(String temporaryPassword) {
	}

	/**
	 * @param phone any Cameroon format; stored in E.164 (D15)
	 * @param language {@code FR} or {@code EN}; the school's language when omitted on create, unchanged on update
	 */
	record StaffRequest(@NotBlank @Size(max = 60) String firstName, @NotBlank @Size(max = 60) String lastName,
			@Size(max = 30) String phone, @Email @Size(max = 120) String email,
			@Pattern(regexp = "FR|EN") String language) {
	}

	record StatusRequest(@NotNull @Pattern(regexp = "ACTIVE|DISABLED") String status) {
	}

	/** @param blockId required for DM and ASSISTANT_DM (D5), absent otherwise */
	record RoleRequest(@NotNull Role role, UUID blockId) {
	}

	record TeachingRequest(@NotNull UUID classId, @NotBlank @Size(max = 60) String subject) {
	}

}
