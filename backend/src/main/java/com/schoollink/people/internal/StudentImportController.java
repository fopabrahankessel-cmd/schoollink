package com.schoollink.people.internal;

import com.schoollink.people.internal.StudentImportService.ImportView;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** UC-A3 import, step 1: upload and preview (SPEC §9 {@code POST /students/import}). */
@RestController
class StudentImportController {

	private final StudentImportService imports;

	StudentImportController(StudentImportService imports) {
		this.imports = imports;
	}

	@PostMapping("/api/v1/students/import")
	@ResponseStatus(HttpStatus.CREATED)
	ImportView preview(@AuthenticationPrincipal CurrentUser user, @RequestParam UUID academicYearId,
			@RequestParam MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw ApiException.invalid("import.file_empty", "file");
		}
		return imports.preview(user, academicYearId, file.getOriginalFilename(), file.getBytes());
	}

	@GetMapping("/api/v1/students/import/{id}")
	ImportView get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return imports.get(user, id);
	}

}
