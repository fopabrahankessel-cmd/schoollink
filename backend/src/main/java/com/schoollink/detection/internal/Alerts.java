package com.schoollink.detection.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.detection.RuleType;
import com.schoollink.discipline.DisciplineOutcomes;
import com.schoollink.discipline.Summonses;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC-D2, BR-18: the DM in charge of the student's class reviews the risk alerts and decides on
 * each one, once: dismiss (with a reason), summon the parent, record a disciplinary issue, or refer
 * to the counsellor. The decision closes the alert and keeps who, when and why (NFR-11).
 */
@Service
class Alerts {

	static final int MAX_NOTE = 500;

	/** The closed alerts shown, newest first: the open ones are all shown. */
	static final int CLOSED_SHOWN = 200;

	enum Status {
		OPEN, CLOSED
	}

	enum Decision {
		DISMISS, SUMMON, DISCIPLINARY, REFER
	}

	/**
	 * {@code note}: the reason to dismiss, the issue to record, what the counsellor should know, or
	 * why the parent is summoned. {@code meetingDate} and {@code meetingTime}: for SUMMON only; left
	 * out, the next school day at the start of school (UC-D3: one tap after a full-day absence, D31).
	 */
	record DecisionRequest(@NotNull Decision decision, @Size(max = MAX_NOTE) String note, LocalDate meetingDate,
			LocalTime meetingTime) {
	}

	record AlertView(UUID id, UUID studentId, String firstName, String lastName, String matricule, UUID classId,
			String className, RuleType ruleType, int threshold, LocalDate asOf, JsonNode evidence, Status status,
			Instant raisedAt, String decision, String decisionNote, UUID decidedBy, Instant decidedAt,
			UUID outcomeId) {
	}

	private record Row(UUID id, UUID studentId, UUID classId, RuleType ruleType, int threshold, LocalDate asOf,
			JsonNode evidence, Status status, Instant raisedAt, String decision, String decisionNote, UUID decidedBy,
			Instant decidedAt, UUID outcomeId) {
	}

	private static final String COLUMNS = """
			id, student_id, class_id, rule_type, threshold, as_of, evidence::text AS evidence, status, raised_at,
			decision, decision_note, closed_by, closed_at, outcome_id""";

	private final NamedParameterJdbcTemplate jdbc;

	private final Students students;

	private final SchoolClasses classes;

	private final DisciplineOutcomes outcomes;

	private final Summonses summonses;

	private final AuditLog audit;

	private final JsonMapper json;

	private final Clock clock;

	Alerts(NamedParameterJdbcTemplate jdbc, Students students, SchoolClasses classes, DisciplineOutcomes outcomes,
			Summonses summonses, AuditLog audit, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.students = students;
		this.classes = classes;
		this.outcomes = outcomes;
		this.summonses = summonses;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/** BR-18: the alerts of the DM's classes only; open ones oldest first, closed ones newest first. */
	@Transactional(readOnly = true)
	List<AlertView> list(CurrentUser user, Status status) {
		UUID school = dmSchool(user);
		Set<UUID> scope = user.classesAs(Role.DM);
		if (scope.isEmpty()) {
			return List.of();
		}
		String order = status == Status.OPEN ? "raised_at, id" : "closed_at DESC, id LIMIT " + CLOSED_SHOWN;
		List<Row> rows = jdbc.query("""
				SELECT %s FROM risk_alert WHERE school_id = :school AND class_id IN (:classes) AND status = :status
				ORDER BY %s""".formatted(COLUMNS, order),
				new MapSqlParameterSource("school", school).addValue("classes", scope).addValue("status", status.name()),
				(rs, n) -> row(rs));
		return views(school, rows);
	}

	@Transactional
	AlertView decide(CurrentUser user, UUID id, DecisionRequest request) {
		UUID school = dmSchool(user);
		String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
		// BR-18: a dismissal says why; a disciplinary issue says what happened; a summons has the alert
		if (note == null && (request.decision() == Decision.DISMISS || request.decision() == Decision.DISCIPLINARY)) {
			throw ApiException.invalid("alert.note_required", "note");
		}
		// locked: two decisions on the same alert are taken one after the other
		Row alert = jdbc.query("SELECT " + COLUMNS + " FROM risk_alert WHERE id = :id AND school_id = :school FOR UPDATE",
				new MapSqlParameterSource("id", id).addValue("school", school), (rs, n) -> row(rs))
			.stream()
			.findFirst()
			// D17: another school's, or outside the DM's classes, is not found
			.filter(a -> user.classesAs(Role.DM).contains(a.classId()))
			.orElseThrow(() -> ApiException.notFound("not_found"));
		if (alert.status() == Status.CLOSED) {
			throw ApiException.conflict("alert.already_decided");
		}

		UUID outcome = switch (request.decision()) {
			case DISMISS -> null;
			// BR-23: sent at once, in this transaction
			case SUMMON -> summonses.issue(school, alert.studentId(), id, user.userId(), request.meetingDate(),
					request.meetingTime(), note);
			case DISCIPLINARY -> outcomes.recordDisciplinary(school, alert.studentId(), id, user.userId(), note);
			case REFER -> outcomes.refer(school, alert.studentId(), id, user.userId(), note);
		};
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("""
				UPDATE risk_alert SET status = 'CLOSED', decision = :decision, decision_note = :note, outcome_id = :outcome,
				       closed_by = :by, closed_at = :now
				WHERE id = :id AND school_id = :school""",
				new MapSqlParameterSource("id", id).addValue("school", school)
					.addValue("decision", request.decision().name())
					.addValue("note", note)
					.addValue("outcome", outcome)
					.addValue("by", user.userId())
					.addValue("now", Timestamp.from(now)));
		// NFR-11: who, when and why
		Map<String, Object> before = new HashMap<>();
		before.put("status", Status.OPEN.name());
		before.put("decision", null);
		Map<String, Object> after = new HashMap<>();
		after.put("status", Status.CLOSED.name());
		after.put("decision", request.decision().name());
		after.put("decisionNote", note);
		after.put("outcomeId", outcome == null ? null : outcome.toString());
		after.put("closedAt", now.toString());
		audit.record(new AuditEntry(school, user.userId(), AuditAction.DECIDE, "risk_alert", id.toString(), before,
				after));
		Row decided = new Row(alert.id(), alert.studentId(), alert.classId(), alert.ruleType(), alert.threshold(),
				alert.asOf(), alert.evidence(), Status.CLOSED, alert.raisedAt(), request.decision().name(), note,
				user.userId(), now, outcome);
		return views(school, List.of(decided)).get(0);
	}

	/** UC-D2: the DM only (D17: other roles 403). */
	private static UUID dmSchool(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	private Row row(ResultSet rs) throws SQLException {
		Timestamp closedAt = rs.getTimestamp("closed_at");
		return new Row(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
				rs.getObject("class_id", UUID.class), RuleType.valueOf(rs.getString("rule_type")), rs.getInt("threshold"),
				rs.getObject("as_of", LocalDate.class), json.readTree(rs.getString("evidence")),
				Status.valueOf(rs.getString("status")), rs.getTimestamp("raised_at").toInstant(), rs.getString("decision"),
				rs.getString("decision_note"), rs.getObject("closed_by", UUID.class),
				closedAt == null ? null : closedAt.toInstant(), rs.getObject("outcome_id", UUID.class));
	}

	/** With the student's name and class, as the DM knows them (NFR-9: no guardian, no phone). */
	private List<AlertView> views(UUID school, List<Row> rows) {
		Map<UUID, Card> cards = students.cards(school, rows.stream().map(Row::studentId).collect(Collectors.toSet()));
		Map<UUID, String> classNames = classes.names(school,
				rows.stream().map(Row::classId).collect(Collectors.toSet()));
		return rows.stream().map(r -> {
			Card card = cards.get(r.studentId());
			return new AlertView(r.id(), r.studentId(), card == null ? null : card.firstName(),
					card == null ? null : card.lastName(), card == null ? null : card.matricule(), r.classId(),
					classNames.get(r.classId()), r.ruleType(), r.threshold(), r.asOf(), r.evidence(), r.status(),
					r.raisedAt(), r.decision(), r.decisionNote(), r.decidedBy(), r.decidedAt(), r.outcomeId());
		}).toList();
	}

}
