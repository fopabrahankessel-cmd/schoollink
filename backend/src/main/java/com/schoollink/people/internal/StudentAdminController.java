package com.schoollink.people.internal;

import com.schoollink.people.internal.StudentAdmin.ClassRequest;
import com.schoollink.people.internal.StudentAdmin.DetailsRequest;
import com.schoollink.people.internal.StudentAdmin.LeaveRequest;
import com.schoollink.people.internal.StudentAdmin.MoveRequest;
import com.schoollink.people.internal.StudentAdmin.Moved;
import com.schoollink.people.internal.StudentAdmin.NewStudentRequest;
import com.schoollink.people.internal.StudentAdmin.ReturnRequest;
import com.schoollink.people.internal.StudentAdmin.StudentRow;
import com.schoollink.people.internal.StudentAdmin.StudentView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** D43: the students of the school, for the admin. Access rules are in {@link StudentAdmin}. */
@RestController
@RequestMapping("/api/v1/students")
class StudentAdminController {

	private final StudentAdmin students;

	StudentAdminController(StudentAdmin students) {
		this.students = students;
	}

	@GetMapping
	List<StudentRow> list(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) UUID classId,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean left) {
		return students.list(user, classId, q, left);
	}

	@GetMapping("/{id}")
	StudentView get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return students.get(user, id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	StudentView create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NewStudentRequest body) {
		return students.create(user, body);
	}

	@PutMapping("/{id}")
	StudentView update(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody DetailsRequest body) {
		return students.update(user, id, body);
	}

	@PutMapping("/{id}/class")
	StudentView move(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody ClassRequest body) {
		return students.move(user, id, body.classId());
	}

	@PostMapping("/move")
	Moved move(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody MoveRequest body) {
		return students.move(user, body);
	}

	@PostMapping("/{id}/leave")
	StudentView leave(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody LeaveRequest body) {
		return students.leave(user, id, body.date());
	}

	@PostMapping("/{id}/return")
	StudentView comeBack(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody ReturnRequest body) {
		return students.comeBack(user, id, body.classId());
	}

}
