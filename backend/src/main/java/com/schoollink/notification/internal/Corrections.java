package com.schoollink.notification.internal;

import com.schoollink.notification.internal.Outbox.Channel;
import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.Outbox.Row;
import com.schoollink.notification.internal.Outbox.Status;
import com.schoollink.people.Guardians;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * BR-14, D11: what the parents hear when the DM cancels an absence or a lateness. For each guardian
 * and channel of the original notification:
 * <ul>
 * <li>not sent yet (a push QUEUED, an SMS HELD for the digest): WITHDRAWN, the parent never hears
 * of the error;</li>
 * <li>on its way or delivered (SENDING, SENT, DELIVERED, OPENED): a correction on the same channel,
 * at once, also by SMS (D11: it does not wait for the digest);</li>
 * <li>FAILED: nothing, the parent never got it.</li>
 * </ul>
 * Runs in the cancelling transaction (NFR-6). The correction's event id is the record's, so it is
 * written once per guardian and channel.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class Corrections {

	private record Notified(UUID guardianId, UUID studentId, Channel channel, JsonNode payload) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final Outbox outbox;

	private final Guardians guardians;

	private final JsonMapper json;

	Corrections(NamedParameterJdbcTemplate jdbc, Outbox outbox, Guardians guardians, JsonMapper json) {
		this.jdbc = jdbc;
		this.outbox = outbox;
		this.guardians = guardians;
		this.json = json;
	}

	/** {@code original}: ABSENCE or LATENESS; {@code recordId}: the cancelled record, its event id. */
	void correct(UUID schoolId, EventType original, UUID recordId) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("type", original.name())
			.addValue("event", recordId);
		// a row the dispatcher has claimed is SENDING already, so it is not withdrawn but corrected
		jdbc.update("""
				UPDATE notification_outbox SET status = 'WITHDRAWN'
				WHERE school_id = :school AND event_type = :type AND event_id = :event AND status IN ('QUEUED', 'HELD')""",
				p);
		List<Notified> notified = jdbc.query("""
				SELECT guardian_id, student_id, channel, payload::text AS payload FROM notification_outbox
				WHERE school_id = :school AND event_type = :type AND event_id = :event
				  AND status IN ('SENDING', 'SENT', 'DELIVERED', 'OPENED')""", p,
				(rs, n) -> new Notified(rs.getObject("guardian_id", UUID.class), rs.getObject("student_id", UUID.class),
						Channel.valueOf(rs.getString("channel")), json.readTree(rs.getString("payload"))));
		outbox.write(notified.stream().map(n -> {
			// what the correction templates need (see Templates)
			Map<String, Object> payload = new HashMap<>();
			payload.put("studentId", n.studentId().toString());
			payload.put("firstName", n.payload().get("firstName").asString());
			payload.put("date", n.payload().get("date").asString());
			payload.put("kind", original.name());
			if (n.payload().has("periodNumber")) {
				payload.put("periodNumber", n.payload().get("periodNumber").asInt());
			}
			// BR-20: in the guardian's language now
			return new Row(schoolId, n.guardianId(), n.studentId(), EventType.CORRECTION, recordId, n.channel(),
					guardians.language(schoolId, n.guardianId()), payload, Status.QUEUED);
		}).toList());
	}

}
