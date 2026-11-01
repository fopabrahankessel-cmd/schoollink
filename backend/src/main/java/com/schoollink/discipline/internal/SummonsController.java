package com.schoollink.discipline.internal;

import com.schoollink.discipline.internal.SummonsService.SummonsRequest;
import com.schoollink.discipline.internal.SummonsService.SummonsView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** UC-D3: the DM summons a parent (BR-23). */
@RestController
class SummonsController {

	private final SummonsService summonses;

	SummonsController(SummonsService summonses) {
		this.summonses = summonses;
	}

	@PostMapping("/api/v1/summons")
	@ResponseStatus(HttpStatus.CREATED)
	SummonsView summon(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SummonsRequest body) {
		return summonses.summon(user, body);
	}

}
