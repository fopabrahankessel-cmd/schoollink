package com.schoollink.notification.internal;

import com.schoollink.notification.internal.Outbox.Channel;
import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.PushProvider.PushMessage;
import com.schoollink.notification.internal.SendResult.Rejected;
import com.schoollink.notification.internal.SendResult.Retry;
import com.schoollink.notification.internal.SendResult.Sent;
import com.schoollink.notification.internal.SmsProvider.SmsMessage;
import com.schoollink.notification.internal.Templates.Push;
import com.schoollink.notification.internal.Templates.School;
import com.schoollink.people.Guardians;
import com.schoollink.people.Guardians.Contact;
import com.schoollink.people.Invitations;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.Schools;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the QUEUED outbox rows (BR-21, BR-23, BR-24, NFR-5, NFR-6). Each round claims a batch with
 * {@code FOR UPDATE SKIP LOCKED}, so several instances never take the same row, and marks it
 * SENDING; the sends happen outside any transaction; each outcome is then recorded on its own.
 * A row left SENDING for longer than {@link #LEASE} (the server stopped mid-send) is claimed again:
 * a notification may go twice, never zero times. HELD rows wait for the SMS digest and are never
 * taken here (BR-22).
 *
 * <p>BR-24: a failed send is retried with backoff, up to 3 times ({@link #MAX_ATTEMPTS} in all),
 * then the row is FAILED. A refusal that cannot pass (no app account any more, an unknown number)
 * fails it at once. An invitation code leaves the row once the SMS is sent or has failed, and a
 * code revoked or expired in the meantime is not sent (D13, NFR-9).
 */
@Component
class OutboxDispatcher {

	/** The first try and 3 retries (BR-24). */
	static final int MAX_ATTEMPTS = 4;

	/** Wait before retry 1, 2 and 3. */
	static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10));

	/** How long a claimed row may stay SENDING before another round takes it again. */
	static final Duration LEASE = Duration.ofMinutes(2);

	static final int BATCH = 50;

	private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

	/** A claimed row; {@code attempt} counts this one (1 for the first try). */
	record Claimed(UUID id, UUID schoolId, UUID guardianId, EventType eventType, UUID eventId, Channel channel,
			String language, String payload, int attempt, Instant claimedAt) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final Templates templates;

	private final PushProvider push;

	private final SmsProvider sms;

	private final Guardians guardians;

	private final Invitations invitations;

	private final Schools schools;

	private final SchoolCalendar calendar;

	private final JsonMapper json;

	private final Clock clock;

	OutboxDispatcher(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
			Templates templates, PushProvider push, SmsProvider sms, Guardians guardians, Invitations invitations,
			Schools schools, SchoolCalendar calendar, JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.transactions = new TransactionTemplate(transactionManager);
		this.templates = templates;
		this.push = push;
		this.sms = sms;
		this.guardians = guardians;
		this.invitations = invitations;
		this.schools = schools;
		this.calendar = calendar;
		this.json = json;
		this.clock = clock;
	}

	/** One round: claims what is due and sends it. Returns how many rows it claimed. */
	int dispatchDue() {
		List<Claimed> rows = claim();
		for (Claimed row : rows) {
			complete(row, send(row));
		}
		return rows.size();
	}

	/** Marks a batch of due rows SENDING, in a short transaction of its own. */
	List<Claimed> claim() {
		Instant now = clock.instant();
		MapSqlParameterSource p = new MapSqlParameterSource("now", Timestamp.from(now))
			.addValue("stale", Timestamp.from(now.minus(LEASE)))
			.addValue("batch", BATCH);
		return transactions.execute(tx -> jdbc.query("""
				UPDATE notification_outbox o SET status = 'SENDING', claimed_at = :now, attempts = o.attempts + 1
				FROM (SELECT id FROM notification_outbox
				      WHERE (status = 'QUEUED' AND next_attempt_at <= :now) OR (status = 'SENDING' AND claimed_at <= :stale)
				      ORDER BY next_attempt_at, created_at LIMIT :batch FOR UPDATE SKIP LOCKED) due
				WHERE o.id = due.id
				RETURNING o.id, o.school_id, o.guardian_id, o.event_type, o.event_id, o.channel, o.language,
				          o.payload::text AS payload, o.attempts""", p,
				(rs, n) -> new Claimed(rs.getObject("id", UUID.class), rs.getObject("school_id", UUID.class),
						rs.getObject("guardian_id", UUID.class), EventType.valueOf(rs.getString("event_type")),
						rs.getObject("event_id", UUID.class), Channel.valueOf(rs.getString("channel")),
						rs.getString("language"), rs.getString("payload"), rs.getInt("attempts"), now)));
	}

	/** Renders and sends one row, outside any transaction. Never throws. */
	SendResult send(Claimed row) {
		if (row.attempt() > MAX_ATTEMPTS) {
			// claimed again after a stop mid-send, with no attempt left
			return new Rejected("no attempt left");
		}
		Contact contact;
		String text;
		Push message;
		try {
			JsonNode payload = json.readTree(row.payload());
			contact = guardians.contact(row.schoolId(), row.guardianId());
			if (row.channel() == Channel.PUSH) {
				if (contact.userId() == null) {
					return new Rejected("the guardian has no app account");
				}
				message = templates.push(row.eventType(), row.language(), payload);
				text = null;
			}
			else {
				if (row.eventType() == EventType.INVITATION && !invitations.isLive(row.schoolId(), row.eventId())) {
					return new Rejected("the invitation code was revoked or has expired");
				}
				School school = new School(schools.name(row.schoolId()), calendar.zone(row.schoolId()));
				text = templates.sms(row.eventType(), row.language(), payload, school);
				message = null;
			}
		}
		catch (RuntimeException ex) {
			// a bug or missing data, not a passing failure: retrying would not help
			log.error("Outbox row {} cannot be rendered", row.id(), ex);
			return new Rejected("cannot be rendered: " + ex.getClass().getSimpleName());
		}
		try {
			return row.channel() == Channel.PUSH
					? push.send(new PushMessage(row.id(), contact.userId(), message.title(), message.body()))
					: sms.send(new SmsMessage(row.id(), contact.phone(), text));
		}
		catch (RuntimeException ex) {
			// NFR-9: the exception's message may hold the phone or the token; keep its type only
			log.warn("Outbox row {}: provider failed with {}", row.id(), ex.getClass().getName());
			return new Retry("provider error: " + ex.getClass().getSimpleName());
		}
	}

	/**
	 * Records the outcome, if the row is still this round's claim: after the lease, another round
	 * may have taken it again, and its outcome wins.
	 */
	void complete(Claimed row, SendResult result) {
		Instant now = clock.instant();
		MapSqlParameterSource p = new MapSqlParameterSource("id", row.id()).addValue("now", Timestamp.from(now))
			.addValue("claimedAt", Timestamp.from(row.claimedAt()))
			// D13, NFR-9: the code leaves the row once it is no longer needed
			.addValue("eraseCode", row.eventType() == EventType.INVITATION);
		String outcome = switch (result) {
			case Sent sent -> {
				p.addValue("status", "SENT").addValue("next", null).addValue("error", null);
				yield "sent_at = :now, payload = CASE WHEN :eraseCode THEN payload - 'code' ELSE payload END";
			}
			case Retry retry when row.attempt() < MAX_ATTEMPTS -> {
				p.addValue("status", "QUEUED")
					.addValue("next", Timestamp.from(now.plus(BACKOFF.get(row.attempt() - 1))))
					.addValue("error", truncate(retry.error()));
				yield "payload = payload";
			}
			case Retry retry -> {
				p.addValue("status", "FAILED").addValue("next", null).addValue("error", truncate(retry.error()));
				yield "payload = CASE WHEN :eraseCode THEN payload - 'code' ELSE payload END";
			}
			case Rejected rejected -> {
				p.addValue("status", "FAILED").addValue("next", null).addValue("error", truncate(rejected.error()));
				yield "payload = CASE WHEN :eraseCode THEN payload - 'code' ELSE payload END";
			}
		};
		int updated = transactions.execute(tx -> jdbc.update("""
				UPDATE notification_outbox
				SET status = :status, claimed_at = NULL, last_error = :error,
				    next_attempt_at = COALESCE(CAST(:next AS timestamptz), next_attempt_at), %s
				WHERE id = :id AND status = 'SENDING' AND claimed_at = :claimedAt""".formatted(outcome), p));
		if (updated == 0) {
			log.warn("Outbox row {} was claimed again before its outcome was recorded", row.id());
		}
	}

	private static String truncate(String error) {
		return error == null || error.length() <= 200 ? error : error.substring(0, 200);
	}

}
