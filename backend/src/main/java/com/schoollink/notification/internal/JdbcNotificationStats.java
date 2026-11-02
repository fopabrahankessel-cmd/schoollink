package com.schoollink.notification.internal;

import com.schoollink.notification.NotificationStats;
import com.schoollink.school.Schools;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNotificationStats implements NotificationStats {

	private static final ChannelDelivery NONE = new ChannelDelivery(0, 0, 0, 0, 0);

	private final NamedParameterJdbcTemplate jdbc;

	private final SmsUsage usage;

	private final Schools schools;

	JdbcNotificationStats(NamedParameterJdbcTemplate jdbc, SmsUsage usage, Schools schools) {
		this.jdbc = jdbc;
		this.usage = usage;
		this.schools = schools;
	}

	@Override
	public Delivery delivery(UUID schoolId, Instant from, Instant to) {
		Map<String, ChannelDelivery> byChannel = new HashMap<>();
		// HELD, DIGESTED, SKIPPED (D33) and WITHDRAWN (BR-14) are not sends
		jdbc.query("""
				SELECT channel, count(*) FILTER (WHERE status IN ('QUEUED', 'SENDING')) AS pending,
				       count(*) FILTER (WHERE status = 'SENT') AS sent,
				       count(*) FILTER (WHERE status = 'DELIVERED') AS delivered,
				       count(*) FILTER (WHERE status = 'OPENED') AS opened,
				       count(*) FILTER (WHERE status = 'FAILED') AS failed
				FROM notification_outbox
				WHERE school_id = :school AND created_at >= :from AND created_at < :to
				GROUP BY channel""",
				new MapSqlParameterSource("school", schoolId).addValue("from", Timestamp.from(from))
					.addValue("to", Timestamp.from(to)),
				rs -> {
					byChannel.put(rs.getString("channel"), new ChannelDelivery(rs.getInt("pending"), rs.getInt("sent"),
							rs.getInt("delivered"), rs.getInt("opened"), rs.getInt("failed")));
				});
		return new Delivery(byChannel.getOrDefault("PUSH", NONE), byChannel.getOrDefault("SMS", NONE));
	}

	@Override
	public SmsCounter smsCounter(UUID schoolId) {
		return new SmsCounter(usage.sentThisMonth(schoolId), schools.smsMonthlyCap(schoolId));
	}

}
