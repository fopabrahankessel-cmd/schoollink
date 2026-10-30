package com.schoollink.notification.internal;

import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.Templates.Push;
import com.schoollink.people.Guardians;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC-P2: the parent's notifications in the app, and the open receipt for the research metrics
 * (BR-24). Only the rows of the guardian linked to the signed-in account (NFR-8). One entry per
 * event: when an event went by push and by SMS (a summons, BR-23), the push stands for both.
 * Invitations are left out: their row concerned the code, never returned (D13). Each entry is
 * written in the parent's current language (NFR-12), from what the event captured (task 2.5).
 */
@Service
class Inbox {

	static final int DEFAULT_LIMIT = 50;

	static final int MAX_LIMIT = 100;

	private static final Logger log = LoggerFactory.getLogger(Inbox.class);

	/** {@code openedAt} null until the parent opens it in the app. */
	record Entry(UUID id, String eventType, UUID studentId, String title, String body, Instant createdAt,
			Instant openedAt) {
	}

	private record Row(UUID id, EventType type, UUID studentId, String payload, Instant createdAt, Instant openedAt) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Guardians guardians;

	private final Templates templates;

	private final JsonMapper json;

	private final Clock clock;

	Inbox(NamedParameterJdbcTemplate jdbc, Guardians guardians, Templates templates, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.guardians = guardians;
		this.templates = templates;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * Newest first. {@code before}: the id of the last entry already shown, to page back through
	 * older ones (time alone is not enough: the rows of one event share it).
	 */
	@Transactional(readOnly = true)
	List<Entry> entries(CurrentUser user, UUID before, Integer limit) {
		Optional<UUID> guardian = guardianOf(user);
		int size = limit == null ? DEFAULT_LIMIT : limit;
		if (size < 1 || size > MAX_LIMIT) {
			throw ApiException.invalid("validation.max", "limit", "limit", MAX_LIMIT);
		}
		if (guardian.isEmpty()) {
			return List.of();
		}
		MapSqlParameterSource p = new MapSqlParameterSource("school", user.schoolId())
			.addValue("guardian", guardian.get())
			.addValue("before", before)
			.addValue("limit", size);
		List<Row> rows = jdbc.query("""
				SELECT id, event_type, student_id, payload::text AS payload, created_at, opened_at FROM (
				    SELECT DISTINCT ON (event_type, event_id) id, event_type, student_id, payload, created_at, opened_at
				    FROM notification_outbox
				    WHERE school_id = :school AND guardian_id = :guardian AND event_type <> 'INVITATION'
				    ORDER BY event_type, event_id, channel = 'PUSH' DESC) e
				WHERE CAST(:before AS uuid) IS NULL
				   OR (created_at, id) < (SELECT c.created_at, c.id FROM notification_outbox c
				                          WHERE c.id = CAST(:before AS uuid) AND c.guardian_id = :guardian)
				ORDER BY created_at DESC, id DESC LIMIT :limit""", p,
				(rs, n) -> new Row(rs.getObject("id", UUID.class), EventType.valueOf(rs.getString("event_type")),
						rs.getObject("student_id", UUID.class), rs.getString("payload"),
						rs.getTimestamp("created_at").toInstant(),
						rs.getTimestamp("opened_at") == null ? null : rs.getTimestamp("opened_at").toInstant()));
		List<Entry> entries = new ArrayList<>();
		for (Row row : rows) {
			try {
				Push text = templates.push(row.type(), user.language(), json.readTree(row.payload()));
				entries.add(new Entry(row.id(), row.type().name(), row.studentId(), text.title(), text.body(),
						row.createdAt(), row.openedAt()));
			}
			catch (RuntimeException ex) {
				// a kind of event with no app text yet (wave 2): left out rather than failing the inbox
				log.warn("Inbox: notification {} of type {} cannot be shown", row.id(), row.type());
			}
		}
		return entries;
	}

	/**
	 * BR-24: the parent opened it in the app. Sets {@code opened_at} once; a push already sent or
	 * delivered becomes OPENED. Repeating it changes nothing. Another family's notification is not
	 * found (NFR-8).
	 */
	@Transactional
	void opened(CurrentUser user, UUID id) {
		UUID guardian = guardianOf(user).orElseThrow(() -> ApiException.notFound("not_found"));
		int updated = jdbc.update("""
				UPDATE notification_outbox
				SET opened_at = COALESCE(opened_at, :now),
				    status = CASE WHEN status IN ('SENT', 'DELIVERED') THEN 'OPENED' ELSE status END
				WHERE id = :id AND school_id = :school AND guardian_id = :guardian AND event_type <> 'INVITATION'""",
				new MapSqlParameterSource("id", id).addValue("school", user.schoolId())
					.addValue("guardian", guardian)
					.addValue("now", Timestamp.from(clock.instant())));
		if (updated == 0) {
			throw ApiException.notFound("not_found");
		}
	}

	private Optional<UUID> guardianOf(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.PARENT)) {
			throw ApiException.forbidden();
		}
		return guardians.ofAccount(user.schoolId(), user.userId());
	}

}
