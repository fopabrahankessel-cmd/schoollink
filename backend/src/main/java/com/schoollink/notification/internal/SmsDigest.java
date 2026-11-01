package com.schoollink.notification.internal;

import com.schoollink.people.Guardians;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolSummary;
import com.schoollink.school.SchoolTimes;
import com.schoollink.school.SchoolTimes.Times;
import com.schoollink.school.Schools;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
 * BR-22, D6, D33: the daily SMS digest. At each of the school's digest times (UC-D7), the absence
 * and lateness SMS HELD since the last run become one SMS per guardian, student and day, queued for
 * the dispatcher, which applies the monthly cap (BR-25). A guardian who opened the related push in
 * the app does not get that event by SMS: the row is SKIPPED. Nothing new, no SMS (D6).
 *
 * <p>A run is recorded per school, day and digest time, in the transaction that builds its
 * digests, so it happens once even after a restart or on two instances. A run missed while the
 * server was down happens when it comes back, or is taken up by the next one (NFR-5).
 */
@Component
class SmsDigest {

	private static final Logger log = LoggerFactory.getLogger(SmsDigest.class);

	private record Held(UUID id, UUID guardianId, UUID studentId, String type, JsonNode payload, boolean pushOpened) {
	}

	private record Key(UUID guardianId, UUID studentId, String date) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final Schools schools;

	private final SchoolTimes times;

	private final SchoolCalendar calendar;

	private final Guardians guardians;

	private final JsonMapper json;

	private final Clock clock;

	SmsDigest(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
			Schools schools, SchoolTimes times, SchoolCalendar calendar, Guardians guardians,
			JsonMapper json, Clock clock) {
		this.jdbc = jdbc;
		this.transactions = new TransactionTemplate(transactionManager);
		this.schools = schools;
		this.times = times;
		this.calendar = calendar;
		this.guardians = guardians;
		this.json = json;
		this.clock = clock;
	}

	/** Every school whose digest time has come and not yet run today. Returns the digests queued. */
	int runDue() {
		int queued = 0;
		for (SchoolSummary school : schools.listForPicker()) {
			try {
				ZonedDateTime local = clock.instant().atZone(calendar.zone(school.id()));
				LocalTime slot = due(times.times(school.id()), local.toLocalTime());
				if (slot != null) {
					queued += run(school.id(), local.toLocalDate(), slot);
				}
			}
			catch (RuntimeException ex) {
				log.error("SMS digest failed for school {}", school.id(), ex);
			}
		}
		return queued;
	}

	/** The latest digest time already passed today, or {@code null} before the first. */
	static LocalTime due(Times times, LocalTime now) {
		if (times.digestSecond() != null && !now.isBefore(times.digestSecond())) {
			return times.digestSecond();
		}
		return now.isBefore(times.digestFirst()) ? null : times.digestFirst();
	}

	/** One school's run for that day and digest time; 0 if it already ran. Its own transaction. */
	int run(UUID schoolId, LocalDate date, LocalTime slot) {
		Integer queued = transactions.execute(tx -> {
			Timestamp now = Timestamp.from(clock.instant());
			MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("date", date)
				.addValue("slot", Time.valueOf(slot))
				.addValue("now", now);
			if (jdbc.update("""
					INSERT INTO sms_digest_run (school_id, run_date, slot, ran_at) VALUES (:school, :date, :slot, :now)
					ON CONFLICT DO NOTHING""", p) == 0) {
				return 0;
			}
			List<Held> held = jdbc.query("""
					SELECT o.id, o.guardian_id, o.student_id, o.event_type, o.payload::text AS payload,
					       EXISTS (SELECT 1 FROM notification_outbox push
					               WHERE push.event_type = o.event_type AND push.event_id = o.event_id
					                 AND push.guardian_id = o.guardian_id AND push.channel = 'PUSH'
					                 AND push.opened_at IS NOT NULL) AS push_opened
					FROM notification_outbox o
					WHERE o.school_id = :school AND o.status = 'HELD'
					ORDER BY o.created_at, o.id
					FOR UPDATE OF o""", p,
					(rs, n) -> new Held(rs.getObject("id", UUID.class), rs.getObject("guardian_id", UUID.class),
							rs.getObject("student_id", UUID.class), rs.getString("event_type"),
							json.readTree(rs.getString("payload")), rs.getBoolean("push_opened")));
			List<UUID> skipped = held.stream().filter(Held::pushOpened).map(Held::id).toList();
			if (!skipped.isEmpty()) {
				jdbc.update("UPDATE notification_outbox SET status = 'SKIPPED' WHERE id IN (:ids)",
						new MapSqlParameterSource("ids", skipped));
			}
			Map<Key, List<Held>> digests = new LinkedHashMap<>();
			held.stream()
				.filter(row -> !row.pushOpened())
				.forEach(row -> digests.computeIfAbsent(
						new Key(row.guardianId(), row.studentId(), row.payload().get("date").asString()),
						k -> new ArrayList<>())
					.add(row));
			digests.forEach((key, rows) -> queue(schoolId, key, rows, now));
			jdbc.update("""
					UPDATE sms_digest_run SET digests = :digests
					WHERE school_id = :school AND run_date = :date AND slot = :slot""",
					p.addValue("digests", digests.size()));
			return digests.size();
		});
		log.info("SMS digest {} {} for school {}: {} queued", date, slot, schoolId, queued);
		return queued;
	}

	/** One digest SMS, in the guardian's language now (BR-20), and its rows DIGESTED. */
	private void queue(UUID schoolId, Key key, List<Held> rows, Timestamp now) {
		TreeSet<Integer> periods = new TreeSet<>();
		String arrival = null;
		for (Held row : rows) {
			if ("ABSENCE".equals(row.type())) {
				periods.add(row.payload().get("periodNumber").asInt());
			}
			else if ("LATENESS".equals(row.type())) {
				String time = row.payload().get("arrivalTime").asString();
				arrival = arrival == null || time.compareTo(arrival) < 0 ? time : arrival;
			}
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("studentId", key.studentId().toString());
		payload.put("firstName", rows.getLast().payload().get("firstName").asString());
		payload.put("date", key.date());
		payload.put("absentPeriods", List.copyOf(periods));
		if (arrival != null) {
			payload.put("arrivalTime", arrival);
		}
		UUID digest = jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel,
				    language, payload, status, next_attempt_at)
				VALUES (:school, :guardian, :student, 'DIGEST', gen_random_uuid(), 'SMS', :language,
				    CAST(:payload AS jsonb), 'QUEUED', :now)
				RETURNING id""",
				new MapSqlParameterSource("school", schoolId).addValue("guardian", key.guardianId())
					.addValue("student", key.studentId())
					.addValue("language", guardians.language(schoolId, key.guardianId()))
					.addValue("payload", json.writeValueAsString(payload))
					.addValue("now", now),
				UUID.class);
		jdbc.update("UPDATE notification_outbox SET status = 'DIGESTED', digest_id = :digest WHERE id IN (:ids)",
				new MapSqlParameterSource("digest", digest).addValue("ids", rows.stream().map(Held::id).toList()));
	}

}
