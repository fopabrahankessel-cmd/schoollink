package com.schoollink.reporting.internal;

import com.schoollink.attendance.AttendanceHistory;
import com.schoollink.attendance.AttendanceHistory.Day;
import com.schoollink.attendance.AttendanceHistory.DayAbsence;
import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.people.UserAccounts;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D9, D36: the students of the DM's blocks absent the whole day. A student is listed when every
 * period timetabled for their class that day was recorded and their record in it is a live (BR-15),
 * unjustified absence (D2). A class with a timetabled period never recorded lists nobody: it is named
 * in {@code unrecorded} instead, since its full-day absences cannot be known.
 */
@Service
class FullDayReports {

	/** D36: a day's report is available from this time of the day, school time. */
	static final LocalTime DAY_OVER = LocalTime.of(17, 0);

	/** How far back the default day is looked for (holidays included). */
	private static final int LOOK_BACK_DAYS = 60;

	private static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER;

	record FullDayReport(LocalDate date, String generatedBy, int studentCount, int classCount,
			List<BlockSection> blocks, List<Unrecorded> unrecorded) {
	}

	record BlockSection(UUID blockId, String blockName, List<ClassSection> classes) {
	}

	record ClassSection(UUID classId, String className, List<ReportStudent> students) {
	}

	record ReportStudent(UUID id, String lastName, String firstName, String matricule) {
	}

	record Unrecorded(UUID classId, String className, List<Integer> periods) {
	}

	record DefaultDate(LocalDate date) {
	}

	private final AttendanceHistory attendance;

	private final SchoolCalendar calendar;

	private final SchoolClasses classes;

	private final Students students;

	private final UserAccounts accounts;

	private final AuditLog audit;

	private final Clock clock;

	FullDayReports(AttendanceHistory attendance, SchoolCalendar calendar, SchoolClasses classes, Students students,
			UserAccounts accounts, AuditLog audit, Clock clock) {
		this.attendance = attendance;
		this.calendar = calendar;
		this.classes = classes;
		this.students = students;
		this.accounts = accounts;
		this.audit = audit;
		this.clock = clock;
	}

	/** D36: today from 17:00 school time, otherwise the previous school day. */
	@Transactional(readOnly = true)
	DefaultDate defaultDate(CurrentUser user) {
		UUID school = dmSchool(user);
		ZonedDateTime now = clock.instant().atZone(calendar.zone(school));
		LocalDate today = now.toLocalDate();
		if (!now.toLocalTime().isBefore(DAY_OVER) && calendar.isSchoolDay(school, today)) {
			return new DefaultDate(today);
		}
		for (int back = 1; back <= LOOK_BACK_DAYS; back++) {
			LocalDate day = today.minusDays(back);
			if (calendar.isSchoolDay(school, day)) {
				return new DefaultDate(day);
			}
		}
		throw ApiException.notFound("not_found");
	}

	/** Generating is an export of personal data: audited, with counts only (NFR-11). */
	@Transactional
	FullDayReport report(CurrentUser user, LocalDate date) {
		UUID school = dmSchool(user);
		check(school, date);
		Set<UUID> scope = user.classesAs(Role.DM);
		Day day = attendance.day(school, scope, date);
		Map<UUID, String> classNames = classes.names(school, scope);

		Map<UUID, Map<UUID, Set<UUID>>> unjustified = new HashMap<>(); // class -> student -> periods
		for (DayAbsence absence : day.absences()) {
			if (!absence.justified()) {
				unjustified.computeIfAbsent(absence.classId(), c -> new HashMap<>())
					.computeIfAbsent(absence.studentId(), s -> new HashSet<>())
					.add(absence.periodId());
			}
		}

		Map<UUID, List<UUID>> listed = new HashMap<>(); // class -> students
		List<Unrecorded> unrecorded = new ArrayList<>();
		for (UUID classId : scope) {
			Set<UUID> timetabled = calendar.periodsOf(school, classId, date);
			if (timetabled.isEmpty()) {
				continue;
			}
			Set<UUID> missing = new HashSet<>(timetabled);
			missing.removeAll(day.recordedPeriods().getOrDefault(classId, Set.of()));
			if (!missing.isEmpty()) {
				List<Integer> numbers = missing.stream().map(p -> calendar.period(school, p).number()).sorted().toList();
				unrecorded.add(new Unrecorded(classId, classNames.get(classId), numbers));
				continue;
			}
			unjustified.getOrDefault(classId, Map.of()).forEach((student, periods) -> {
				if (periods.containsAll(timetabled)) {
					listed.computeIfAbsent(classId, c -> new ArrayList<>()).add(student);
				}
			});
		}
		unrecorded.sort(Comparator.comparing(Unrecorded::className, BY_NAME));

		List<BlockSection> blocks = sections(school, listed, classNames);
		int studentCount = listed.values().stream().mapToInt(List::size).sum();
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("date", date.toString());
		after.put("students", studentCount);
		after.put("classes", listed.size());
		audit.record(new AuditEntry(school, user.userId(), AuditAction.EXPORT, "full_day_report", date.toString(), null,
				after));
		return new FullDayReport(date, accounts.fullName(school, user.userId()).orElse(""), studentCount, listed.size(),
				blocks, List.copyOf(unrecorded));
	}

	/** Blocks by name, classes by name, students by last then first name. */
	private List<BlockSection> sections(UUID school, Map<UUID, List<UUID>> listed, Map<UUID, String> classNames) {
		Map<UUID, Card> cards = students.cards(school, listed.values().stream().flatMap(List::stream).toList());
		Map<UUID, UUID> blockOf = classes.blockOf(school, listed.keySet());
		Map<UUID, String> blockNames = classes.blockNames(school, new HashSet<>(blockOf.values()));
		Map<UUID, List<ClassSection>> byBlock = new HashMap<>();
		listed.forEach((classId, ids) -> {
			List<ReportStudent> rows = ids.stream()
				.map(cards::get)
				.map(c -> new ReportStudent(c.id(), c.lastName(), c.firstName(), c.matricule()))
				.sorted(Comparator.comparing(ReportStudent::lastName, BY_NAME)
					.thenComparing(ReportStudent::firstName, BY_NAME)
					.thenComparing(ReportStudent::matricule))
				.toList();
			byBlock.computeIfAbsent(blockOf.get(classId), b -> new ArrayList<>())
				.add(new ClassSection(classId, classNames.get(classId), rows));
		});
		return byBlock.entrySet()
			.stream()
			.map(e -> new BlockSection(e.getKey(), blockNames.get(e.getKey()),
					e.getValue().stream().sorted(Comparator.comparing(ClassSection::className, BY_NAME)).toList()))
			.sorted(Comparator.comparing(BlockSection::blockName, BY_NAME))
			.toList();
	}

	/** D36: a school day, not in the future, and today only once the day is over. */
	private void check(UUID school, LocalDate date) {
		ZonedDateTime now = clock.instant().atZone(calendar.zone(school));
		if (date.isAfter(now.toLocalDate())) {
			throw ApiException.invalid("report.date_in_future", "date");
		}
		if (!calendar.isSchoolDay(school, date)) {
			throw ApiException.invalid("report.not_a_school_day", "date");
		}
		if (date.equals(now.toLocalDate()) && now.toLocalTime().isBefore(DAY_OVER)) {
			throw ApiException.invalid("report.day_not_finished", "date");
		}
	}

	private static UUID dmSchool(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

}
