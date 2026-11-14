package com.schoollink.people.internal;

import com.schoollink.people.Students;
import com.schoollink.people.internal.ChildContacts.Contacts;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-P3: a parent's own children, and UC-P5: whom to contact about each (NFR-8: from the account's
 * guardian links, never from the request).
 */
@RestController
class ChildrenController {

	/** {@code leftOn}: the day the child left the school, null while enrolled (D43); the history stays. */
	record Child(UUID id, String firstName, String lastName, UUID classId, String className, LocalDate leftOn) {
	}

	private final Students students;

	private final SchoolClasses classes;

	private final ChildContacts contacts;

	ChildrenController(Students students, SchoolClasses classes, ChildContacts contacts) {
		this.students = students;
		this.classes = classes;
		this.contacts = contacts;
	}

	@GetMapping("/api/v1/me/children")
	@Transactional(readOnly = true)
	List<Child> myChildren(@AuthenticationPrincipal CurrentUser user) {
		requireParent(user);
		Map<UUID, Card> cards = students.cards(user.schoolId(), user.children());
		Map<UUID, String> names = classes.names(user.schoolId(),
				Set.copyOf(cards.values().stream().map(Card::classId).toList()));
		Map<UUID, LocalDate> left = students.leftOn(user.schoolId(), user.children());
		return cards.values()
			.stream()
			.map(c -> new Child(c.id(), c.firstName(), c.lastName(), c.classId(), names.get(c.classId()),
					left.get(c.id())))
			.sorted(Comparator.comparing(Child::firstName).thenComparing(Child::lastName))
			.toList();
	}

	/** UC-P5, D39: the class's teachers and the block's DMs, with their phones. */
	@GetMapping("/api/v1/me/children/{id}/contacts")
	@Transactional(readOnly = true)
	Contacts contacts(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		requireParent(user);
		// NFR-8: another family's child looks like no child at all
		if (!user.children().contains(id)) {
			throw ApiException.notFound("not_found");
		}
		UUID classId = students.classOf(user.schoolId(), id).orElseThrow(() -> ApiException.notFound("not_found"));
		UUID blockId = classes.blockOf(user.schoolId(), Set.of(classId)).get(classId);
		String className = classes.names(user.schoolId(), Set.of(classId)).get(classId);
		return contacts.of(user.schoolId(), classId, blockId, className);
	}

	private static void requireParent(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.PARENT)) {
			throw ApiException.forbidden();
		}
	}

}
