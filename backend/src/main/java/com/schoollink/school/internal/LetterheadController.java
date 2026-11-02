package com.schoollink.school.internal;

import com.schoollink.school.internal.Letterheads.Layout;
import com.schoollink.school.internal.Letterheads.Letterhead;
import com.schoollink.school.internal.Letterheads.Line;
import com.schoollink.school.internal.Letterheads.Logo;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** UC-A7, D36: the school letterhead and its logo; written by the admin, read by the admin and the DM. */
@RestController
class LetterheadController {

	record SaveRequest(@NotNull Layout layout, List<Line> french, List<Line> english) {
	}

	private final Letterheads letterheads;

	LetterheadController(Letterheads letterheads) {
		this.letterheads = letterheads;
	}

	@GetMapping("/api/v1/school/letterhead")
	Letterhead get(@AuthenticationPrincipal CurrentUser user) {
		return letterheads.view(user);
	}

	@PutMapping("/api/v1/school/letterhead")
	Letterhead save(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SaveRequest request) {
		return letterheads.save(user, request.layout(), request.french(), request.english());
	}

	/** The logo as stored; private, since only signed-in staff may read it. */
	@GetMapping("/api/v1/school/letterhead/logo")
	ResponseEntity<byte[]> logo(@AuthenticationPrincipal CurrentUser user) {
		Logo logo = letterheads.logo(user);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(logo.type()))
			.cacheControl(CacheControl.noStore())
			.body(logo.bytes());
	}

	@PutMapping("/api/v1/school/letterhead/logo")
	Letterhead setLogo(@AuthenticationPrincipal CurrentUser user, @RequestParam MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw ApiException.invalid("letterhead.logo_type", "file");
		}
		return letterheads.setLogo(user, file.getBytes());
	}

	@DeleteMapping("/api/v1/school/letterhead/logo")
	Letterhead removeLogo(@AuthenticationPrincipal CurrentUser user) {
		return letterheads.removeLogo(user);
	}

}
