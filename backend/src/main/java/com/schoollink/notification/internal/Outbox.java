package com.schoollink.notification.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@code notification_outbox} rows (NFR-6). Only ever inside the transaction of the event
 * that causes them, so a rolled-back event leaves no row and a committed one is never lost. One row
 * per guardian per channel per event (BR-20): writing the same row again changes nothing.
 */
@Repository
class Outbox {

	enum EventType {

		ABSENCE, LATENESS, CORRECTION, SUMMONS, DM_MESSAGE, ANNOUNCEMENT, INVITATION,

		/** BR-22: one SMS for a guardian, student and day, built by the digest run (task 3.8). */
		DIGEST

	}

	enum Channel {

		PUSH, SMS

	}

	/** HELD: an SMS waiting for the daily digest (BR-22); QUEUED: for the dispatcher (task 2.6). */
	enum Status {

		HELD, QUEUED

	}

	/** {@code studentId} null when the notification is about the guardian (an invitation). */
	record Row(UUID schoolId, UUID guardianId, UUID studentId, EventType eventType, UUID eventId, Channel channel,
			String language, Map<String, Object> payload, Status status) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final JsonMapper json;

	Outbox(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	void write(List<Row> rows) {
		if (rows.isEmpty()) {
			return;
		}
		jdbc.batchUpdate("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language, payload, status)
				VALUES (:school, :guardian, :student, :type, :event, :channel, :language, CAST(:payload AS jsonb), :status)
				ON CONFLICT (event_type, event_id, guardian_id, channel) DO NOTHING""", rows.stream()
			.map(row -> new MapSqlParameterSource("school", row.schoolId()).addValue("guardian", row.guardianId())
				.addValue("student", row.studentId())
				.addValue("type", row.eventType().name())
				.addValue("event", row.eventId())
				.addValue("channel", row.channel().name())
				.addValue("language", row.language())
				.addValue("payload", json.writeValueAsString(row.payload()))
				.addValue("status", row.status().name()))
			.toArray(SqlParameterSource[]::new));
	}

}
