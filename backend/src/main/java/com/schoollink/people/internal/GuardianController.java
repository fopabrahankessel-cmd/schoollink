package com.schoollink.people.internal;

import com.schoollink.people.internal.GuardianDirectory.GuardianView;
import com.schoollink.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC-A4: the guardians of a class and their account or invitation status, for the admin. */
@RestController
class GuardianController {

	private final GuardianDirectory directory;

	GuardianController(GuardianDirectory directory) {
		this.directory = directory;
	}

	@GetMapping("/api/v1/guardians")
	List<GuardianView> list(@AuthenticationPrincipal CurrentUser user, @RequestParam UUID classId) {
		return directory.forClass(user, classId);
	}

}
