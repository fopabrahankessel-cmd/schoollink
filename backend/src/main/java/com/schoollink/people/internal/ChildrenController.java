package com.schoollink.people.internal;

import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC-P3: a parent's own children (NFR-8: from the account's guardian links, never from the request). */
@RestController
class ChildrenController {

	record Child(UUID id, String firstName, String lastName, UUID classId, String className) {
	}

	private final Students students;

	private final SchoolClasses classes;

	ChildrenController(Students students, SchoolClasses classes) {
		this.students = students;
		this.classes = classes;
	}

	@GetMapping("/api/v1/me/children")
	@Transactional(readOnly = true)
	List<Child> myChildren(@AuthenticationPrincipal CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.PARENT)) {
			throw ApiException.forbidden();
		}
		Map<UUID, Card> cards = students.cards(user.schoolId(), user.children());
		Map<UUID, String> names = classes.names(user.schoolId(),
				Set.copyOf(cards.values().stream().map(Card::classId).toList()));
		return cards.values()
			.stream()
			.map(c -> new Child(c.id(), c.firstName(), c.lastName(), c.classId(), names.get(c.classId())))
			.sorted(Comparator.comparing(Child::firstName).thenComparing(Child::lastName))
			.toList();
	}

}
