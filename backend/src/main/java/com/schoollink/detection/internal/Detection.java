package com.schoollink.detection.internal;

import com.schoollink.attendance.AttendanceHistory;
import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.detection.RuleType;
import com.schoollink.detection.internal.Rules.Facts;
import com.schoollink.detection.internal.Rules.Finding;
import com.schoollink.lateness.LatenessHistory;
import com.schoollink.people.Students;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolCalendar.Term;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * BR-16, BR-17: runs the school's active rules for a student as of a date and raises the alerts
 * they find. Detection only ever inserts an OPEN {@code risk_alert}: never a decision, a summons
 * or anything sent to a parent (BR-18). At most one open alert per student and rule, enforced by
 * the database even when two runs race (BR-17).
 */
@Service
class Detection {

	private record Previous(LocalDate asOf, boolean open) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Students students;

	private final SchoolCalendar calendar;

	private final AttendanceHistory attendance;

	private final LatenessHistory lateness;

	private final AuditLog audit;

	private final JsonMapper json;

	private final Clock clock;

	Detection(NamedParameterJdbcTemplate jdbc, Students students, SchoolCalendar calendar,
			AttendanceHistory attendance, LatenessHistory lateness, AuditLog audit, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.students = students;
		this.calendar = calendar;
		this.attendance = attendance;
		this.lateness = lateness;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/** The given rules, when active, for one student as of {@code date}; the number of alerts raised. */
	@Transactional
	int evaluate(UUID schoolId, UUID studentId, LocalDate date, Set<RuleType> types) {
		Optional<UUID> classId = students.classOf(schoolId, studentId);
		if (classId.isEmpty()) {
			return 0;
		}
		Map<RuleType, Integer> rules = activeRules(schoolId);
		rules.keySet().retainAll(types);
		if (rules.isEmpty()) {
			return 0;
		}
		Facts base = facts(schoolId, studentId, classId.get(), date);
		int raised = 0;
		for (Map.Entry<RuleType, Integer> rule : rules.entrySet()) {
			Optional<Previous> previous = previous(schoolId, studentId, rule.getKey());
			if (previous.filter(Previous::open).isPresent()) {
				continue; // BR-17: one open alert per student and rule
			}
			Facts facts = new Facts(base.date(), previous.map(Previous::asOf).orElse(null), base.schoolDay(),
					base.timetable(), base.absences(), base.lateDays(), base.term());
			Optional<Finding> finding = Rules.evaluate(rule.getKey(), facts, rule.getValue());
			if (finding.isPresent()
					&& raise(schoolId, studentId, classId.get(), rule.getKey(), rule.getValue(), date, finding.get())) {
				raised++;
			}
		}
		return raised;
	}

	/**
	 * BR-17, the nightly run: every rule for every student with a live absence or lateness in the
	 * term holding {@code date}. Catches what the event runs missed (a session synced late, two
	 * periods locked at the same moment, a threshold the DM lowered).
	 */
	@Transactional
	int evaluateSchool(UUID schoolId, LocalDate date) {
		Optional<Term> term = calendar.termOf(schoolId, date);
		if (term.isEmpty()) {
			return 0;
		}
		Set<UUID> concerned = new HashSet<>(attendance.studentsAbsent(schoolId, term.get().startDate(), date));
		concerned.addAll(lateness.studentsLate(schoolId, term.get().startDate(), date));
		int raised = 0;
		for (UUID student : concerned) {
			raised += evaluate(schoolId, student, date, EnumSet.allOf(RuleType.class));
		}
		return raised;
	}

	private Map<RuleType, Integer> activeRules(UUID schoolId) {
		Map<RuleType, Integer> rules = new EnumMap<>(RuleType.class);
		jdbc.query("SELECT type, count FROM threshold_rule WHERE school_id = :school AND active",
				Map.of("school", schoolId),
				rs -> {
					rules.put(RuleType.valueOf(rs.getString("type")), rs.getInt("count"));
				});
		return rules;
	}

	/** D29: the student's latest alert of this rule, open or closed. */
	private Optional<Previous> previous(UUID schoolId, UUID studentId, RuleType type) {
		return jdbc.query("""
				SELECT as_of, status = 'OPEN' AS open FROM risk_alert
				WHERE school_id = :school AND student_id = :student AND rule_type = :type
				ORDER BY status = 'OPEN' DESC, as_of DESC LIMIT 1""",
				new MapSqlParameterSource("school", schoolId).addValue("student", studentId)
					.addValue("type", type.name()),
				(rs, n) -> new Previous(rs.getObject("as_of", LocalDate.class), rs.getBoolean("open")))
			.stream()
			.findFirst();
	}

	/** The student's facts, read once through the other modules' APIs; school days and timetable cached. */
	private Facts facts(UUID schoolId, UUID studentId, UUID classId, LocalDate date) {
		Term term = calendar.termOf(schoolId, date).orElse(null);
		LocalDate from = date.minusDays(Rules.LOOKBACK_DAYS);
		if (term != null && term.startDate().isBefore(from)) {
			from = term.startDate();
		}
		Map<LocalDate, Boolean> schoolDays = new HashMap<>();
		Map<LocalDate, Set<UUID>> timetables = new HashMap<>();
		Function<LocalDate, Set<UUID>> timetable = day -> timetables.computeIfAbsent(day,
				d -> calendar.periodsOf(schoolId, classId, d));
		Set<LocalDate> lateDays = lateness.lateness(schoolId, studentId, from, date)
			.stream()
			.map(LatenessHistory.Lateness::date)
			.collect(Collectors.toSet());
		return new Facts(date, null, day -> schoolDays.computeIfAbsent(day, d -> calendar.isSchoolDay(schoolId, d)),
				timetable, attendance.absences(schoolId, studentId, from, date), lateDays, term);
	}

	/** {@code false} when another run raised the same alert first (BR-17). */
	private boolean raise(UUID schoolId, UUID studentId, UUID classId, RuleType type, int threshold, LocalDate asOf,
			Finding finding) {
		List<UUID> ids = jdbc.queryForList("""
				INSERT INTO risk_alert (school_id, student_id, class_id, rule_type, threshold, as_of, evidence, raised_at)
				VALUES (:school, :student, :class, :type, :threshold, :asOf, CAST(:evidence AS jsonb), :now)
				ON CONFLICT (student_id, rule_type) WHERE status = 'OPEN' DO NOTHING
				RETURNING id""",
				new MapSqlParameterSource("school", schoolId).addValue("student", studentId)
					.addValue("class", classId)
					.addValue("type", type.name())
					.addValue("threshold", threshold)
					.addValue("asOf", asOf)
					.addValue("evidence", json.writeValueAsString(finding.evidence()))
					.addValue("now", Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))),
				UUID.class);
		if (ids.isEmpty()) {
			return false;
		}
		// NFR-11: raised by the system, no actor
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("studentId", studentId.toString());
		after.put("classId", classId.toString());
		after.put("ruleType", type.name());
		after.put("threshold", threshold);
		after.put("asOf", asOf.toString());
		after.put("evidence", finding.evidence());
		audit.record(new AuditEntry(schoolId, null, AuditAction.CREATE, "risk_alert", ids.get(0).toString(), null,
				after));
		return true;
	}

}
