package com.schoollink.people.internal;

import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-T1, UC-T2: the classes a teacher teaches, each with its students, so the app can record a
 * session with no network (NFR-3). Only the teacher's own classes (SPEC §3: student list, R own
 * classes; NFR-8), and only what is needed to recognise a student (NFR-9: no birth year, no guardian).
 */
@RestController
class TeacherClassesController {

	record Student(UUID id, String matricule, String firstName, String lastName) {
	}

	record TeacherClass(UUID classId, String className, List<Student> students) {
	}

	private final Students students;

	private final SchoolClasses classes;

	TeacherClassesController(Students students, SchoolClasses classes) {
		this.students = students;
		this.classes = classes;
	}

	/** Classes by name; students by last name, then first name. */
	@GetMapping("/api/v1/me/classes")
	@Transactional(readOnly = true)
	List<TeacherClass> myClasses(@AuthenticationPrincipal CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.TEACHER)) {
			throw ApiException.forbidden();
		}
		Set<UUID> taught = user.classesAs(Role.TEACHER);
		Map<UUID, String> names = classes.names(user.schoolId(), taught);
		Map<UUID, List<Student>> byClass = new LinkedHashMap<>();
		names.keySet().forEach(id -> byClass.put(id, new ArrayList<>()));
		for (Card card : students.inClasses(user.schoolId(), names.keySet(), null, Integer.MAX_VALUE)) {
			byClass.get(card.classId())
				.add(new Student(card.id(), card.matricule(), card.firstName(), card.lastName()));
		}
		return byClass.entrySet()
			.stream()
			.map(e -> new TeacherClass(e.getKey(), names.get(e.getKey()), List.copyOf(e.getValue())))
			.sorted(Comparator.comparing(TeacherClass::className).thenComparing(TeacherClass::classId))
			.toList();
	}

}
