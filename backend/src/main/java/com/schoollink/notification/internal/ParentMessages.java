package com.schoollink.notification.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.notification.internal.Outbox.Channel;
import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.Outbox.Row;
import com.schoollink.notification.internal.Outbox.Status;
import com.schoollink.people.Guardians;
import com.schoollink.people.Guardians.StudentGuardian;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolClasses;
import com.schoollink.school.Schools;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D4, UC-A5, D38: messages to parents. A DM writes to the guardians of one student or one class
 * of their blocks; an administrator to those of the whole school or one class (NFR-8: the scope comes
 * from the signed-in user). Each guardian gets it once, by push with the app (D7) or, if the author
 * asks for it, by SMS without; the preview gives the counts and the SMS cost before sending (BR-25,
 * NFR-14). Sending writes the message, its outbox rows and an audit row in one transaction (BR-20,
 * NFR-6, NFR-11); the dispatcher sends them. Messages are never edited or deleted.
 */
@Service
class ParentMessages {

	static final int MAX_BODY = 480;

	enum Kind {

		DM_MESSAGE, ANNOUNCEMENT

	}

	/**
	 * What the author wrote. {@code id}: made by the author's device, so sending twice sends once.
	 * DM: {@code studentId} or {@code classId}. Administrator: {@code classId}, or neither for the
	 * whole school.
	 */
	record Draft(UUID id, UUID studentId, UUID classId, String body, Boolean sms, Boolean plainSms) {

		/** Left out: no SMS. */
		@Override
		public Boolean sms() {
			return Boolean.TRUE.equals(sms);
		}

		@Override
		public Boolean plainSms() {
			return Boolean.TRUE.equals(plainSms);
		}

	}

	/**
	 * Who gets it and what it costs. {@code smsSample}: the SMS in the author's language, null
	 * without SMS. {@code overCap}: more SMS than the month has left; sending is then refused.
	 */
	record Preview(int recipients, int byApp, int bySms, int unreached, int smsUnits, boolean smsUnicode, int smsLeft,
			int estimatedCost, boolean overCap, String smsSample) {
	}

	record Sent(UUID id, boolean created) {
	}

	record ClassOption(UUID classId, String className, int students) {
	}

	private enum Audience {

		STUDENT, CLASS, SCHOOL

	}

	/** Who the message is for, once the scope is checked. */
	private record Resolved(Audience audience, UUID studentId, UUID classId, Set<UUID> students) {
	}

	/** One guardian, once, with the child named when the message is about one student. */
	private record Recipient(UUID guardianId, String language, boolean hasAccount, String firstName) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Students students;

	private final Guardians guardians;

	private final SchoolClasses classes;

	private final Schools schools;

	private final SchoolCalendar calendar;

	private final SmsUsage usage;

	private final Templates templates;

	private final Outbox outbox;

	private final AuditLog audit;

	private final Clock clock;

	ParentMessages(NamedParameterJdbcTemplate jdbc, Students students, Guardians guardians, SchoolClasses classes,
			Schools schools, SchoolCalendar calendar, SmsUsage usage, Templates templates, Outbox outbox, AuditLog audit,
			Clock clock) {
		this.jdbc = jdbc;
		this.students = students;
		this.guardians = guardians;
		this.classes = classes;
		this.schools = schools;
		this.calendar = calendar;
		this.usage = usage;
		this.templates = templates;
		this.outbox = outbox;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	Preview preview(CurrentUser user, Kind kind, Draft draft) {
		UUID school = authorSchool(user, kind);
		Resolved target = resolve(user, kind, school, draft);
		String body = draft.body() == null ? "" : draft.body().strip();
		return preview(user, kind, school, recipients(school, target), body, draft.sms(), draft.plainSms());
	}

	@Transactional
	Sent send(CurrentUser user, Kind kind, Draft draft) {
		UUID school = authorSchool(user, kind);
		if (draft.id() == null) {
			throw ApiException.invalid("messages.id_required", "id");
		}
		Resolved target = resolve(user, kind, school, draft);
		String body = draft.body() == null ? "" : draft.body().strip();
		if (body.isEmpty()) {
			throw ApiException.invalid("messages.body_required", "body");
		}
		if (body.length() > MAX_BODY) {
			throw ApiException.invalid("messages.body_too_long", "body", MAX_BODY);
		}
		Optional<Sent> again = repeated(school, kind, target, draft, body);
		if (again.isPresent()) {
			return again.get();
		}
		List<Recipient> recipients = recipients(school, target);
		if (recipients.stream().noneMatch(r -> r.hasAccount() || draft.sms())) {
			throw ApiException.conflict("messages.no_recipient");
		}
		if (draft.sms()) {
			// BR-25: one message at a time takes from this month's SMS
			schools.lockSmsBudget(school);
			int bySms = (int) recipients.stream().filter(r -> !r.hasAccount()).count();
			int left = usage.left(school);
			if (bySms > left) {
				throw ApiException.conflict("messages.sms_over_cap", bySms, left);
			}
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("""
				INSERT INTO parent_message (id, school_id, author_id, kind, audience, student_id, class_id, body, sms,
				                            plain_sms, created_at)
				VALUES (:id, :school, :author, :kind, :audience, :student, :class, :body, :sms, :plain, :now)""",
				new MapSqlParameterSource("id", draft.id()).addValue("school", school)
					.addValue("author", user.userId())
					.addValue("kind", kind.name())
					.addValue("audience", target.audience().name())
					.addValue("student", target.studentId())
					.addValue("class", target.classId())
					.addValue("body", body)
					.addValue("sms", draft.sms())
					.addValue("plain", draft.plainSms())
					.addValue("now", Timestamp.from(now)));
		EventType type = EventType.valueOf(kind.name());
		String className = target.classId() == null ? null
				: classes.names(school, Set.of(target.classId())).get(target.classId());
		List<Row> rows = new ArrayList<>();
		for (Recipient r : recipients) {
			if (!r.hasAccount() && !draft.sms()) {
				continue;
			}
			Map<String, Object> payload = new HashMap<>();
			payload.put("body", body);
			payload.put("plainSms", draft.plainSms());
			if (r.firstName() != null) {
				payload.put("firstName", r.firstName());
			}
			if (className != null) {
				payload.put("className", className);
			}
			rows.add(new Row(school, r.guardianId(), target.studentId(), type, draft.id(),
					r.hasAccount() ? Channel.PUSH : Channel.SMS, r.language(), payload, Status.QUEUED));
		}
		outbox.write(rows);
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("kind", kind.name());
		after.put("audience", target.audience().name());
		after.put("studentId", target.studentId());
		after.put("classId", target.classId());
		after.put("body", body);
		after.put("sms", draft.sms());
		after.put("recipients", rows.stream().map(Row::guardianId).distinct().count());
		audit.record(new AuditEntry(school, user.userId(), AuditAction.CREATE, "parent_message", draft.id().toString(),
				null, after));
		return new Sent(draft.id(), true);
	}

	/** The classes the author may write to, by name, with their number of students. */
	@Transactional(readOnly = true)
	List<ClassOption> classes(CurrentUser user) {
		if (user.schoolId() == null || !user.hasAny(Role.DM, Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		Set<UUID> ids = new java.util.HashSet<>(classes.idsInCurrentYear(school, today(school)));
		if (!user.has(Role.ADMIN)) {
			ids.retainAll(user.classesAs(Role.DM));
		}
		Map<UUID, String> names = classes.names(school, ids);
		Map<UUID, Long> counts = students.inClasses(school, names.keySet(), null, Integer.MAX_VALUE)
			.stream()
			.collect(Collectors.groupingBy(Card::classId, Collectors.counting()));
		return names.entrySet()
			.stream()
			.map(e -> new ClassOption(e.getKey(), e.getValue(), counts.getOrDefault(e.getKey(), 0L).intValue()))
			.sorted(Comparator.comparing(ClassOption::className).thenComparing(ClassOption::classId))
			.toList();
	}

	/** A DM writes messages, an administrator announcements; anyone else is refused (SPEC §3). */
	static UUID authorSchool(CurrentUser user, Kind kind) {
		Role role = kind == Kind.DM_MESSAGE ? Role.DM : Role.ADMIN;
		if (user.schoolId() == null || !user.has(role)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	/** The audience inside the author's scope (NFR-8); 404 outside it, as if it did not exist. */
	private Resolved resolve(CurrentUser user, Kind kind, UUID school, Draft draft) {
		if (kind == Kind.DM_MESSAGE) {
			if ((draft.studentId() == null) == (draft.classId() == null)) {
				throw ApiException.invalid("messages.target_required", "target");
			}
			Set<UUID> scope = user.classesAs(Role.DM);
			if (draft.studentId() != null) {
				students.classOf(school, draft.studentId())
					.filter(scope::contains)
					.orElseThrow(() -> ApiException.notFound("not_found"));
				return new Resolved(Audience.STUDENT, draft.studentId(), null, Set.of(draft.studentId()));
			}
			if (!scope.contains(draft.classId()) || !classes.classExists(school, draft.classId())) {
				throw ApiException.notFound("not_found");
			}
			return new Resolved(Audience.CLASS, null, draft.classId(), students.idsInClass(school, draft.classId()));
		}
		if (draft.studentId() != null) {
			throw ApiException.invalid("messages.target_required", "target");
		}
		if (draft.classId() != null) {
			if (!classes.classExists(school, draft.classId())) {
				throw ApiException.notFound("not_found");
			}
			return new Resolved(Audience.CLASS, null, draft.classId(), students.idsInClass(school, draft.classId()));
		}
		Set<UUID> current = classes.idsInCurrentYear(school, today(school));
		Set<UUID> all = students.inClasses(school, current, null, Integer.MAX_VALUE)
			.stream()
			.map(Card::id)
			.collect(Collectors.toSet());
		return new Resolved(Audience.SCHOOL, null, null, all);
	}

	/** Every guardian of the students once, even with several children in the audience (D38). */
	private List<Recipient> recipients(UUID school, Resolved target) {
		Map<UUID, Recipient> byGuardian = new LinkedHashMap<>();
		for (StudentGuardian g : guardians.ofStudents(school, target.students())) {
			byGuardian.putIfAbsent(g.guardianId(), new Recipient(g.guardianId(), g.language(), g.hasAccount(),
					target.audience() == Audience.STUDENT ? g.studentFirstName() : null));
		}
		return List.copyOf(byGuardian.values());
	}

	private Preview preview(CurrentUser user, Kind kind, UUID school, List<Recipient> recipients, String body,
			boolean sms, boolean plain) {
		int byApp = (int) recipients.stream().filter(Recipient::hasAccount).count();
		int withoutApp = recipients.size() - byApp;
		int bySms = sms ? withoutApp : 0;
		int left = usage.left(school);
		String schoolName = schools.name(school);
		EventType type = EventType.valueOf(kind.name());
		String sample = sms ? templates.messageSms(type, language(user), body, plain, schoolName) : null;
		int units = sample == null ? 0 : SmsText.units(sample);
		// each guardian gets the SMS in their own language, whose length may differ
		int cost = 0;
		if (sms) {
			int price = schools.smsUnitPrice(school);
			for (Recipient r : recipients) {
				if (!r.hasAccount()) {
					cost += SmsText.units(templates.messageSms(type, r.language(), body, plain, schoolName)) * price;
				}
			}
		}
		return new Preview(recipients.size(), byApp, bySms, sms ? 0 : withoutApp, units,
				sample != null && !SmsText.isGsm7(sample), left, cost, bySms > left, sample);
	}

	/**
	 * Sending again what was already sent (a double tap, a retry after a lost answer) returns the first
	 * message; the same id with other content, or another author's id, is refused.
	 */
	private Optional<Sent> repeated(UUID school, Kind kind, Resolved target, Draft draft, String body) {
		List<Map<String, Object>> found = jdbc.queryForList("""
				SELECT school_id, kind, student_id, class_id, body, sms FROM parent_message WHERE id = :id""",
				Map.of("id", draft.id()));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		Map<String, Object> row = found.get(0);
		boolean same = school.equals(row.get("school_id")) && kind.name().equals(row.get("kind"))
				&& Objects.equals(target.studentId(), row.get("student_id"))
				&& Objects.equals(target.classId(), row.get("class_id")) && body.equals(row.get("body"))
				&& draft.sms().equals(row.get("sms"));
		if (!same) {
			throw ApiException.conflict("messages.id_reused");
		}
		return Optional.of(new Sent(draft.id(), false));
	}

	private static String language(CurrentUser user) {
		return "EN".equalsIgnoreCase(user.language()) ? "EN" : "FR";
	}

	private LocalDate today(UUID school) {
		return clock.instant().atZone(calendar.zone(school)).toLocalDate();
	}

}
