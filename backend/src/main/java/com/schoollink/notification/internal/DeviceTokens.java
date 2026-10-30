package com.schoollink.notification.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * FCM registration tokens, one per installed app (BR-21, V15). NFR-9: a token is never logged.
 */
@Repository
class DeviceTokens {

	/** FCM sends to at most 500 tokens in one multicast. */
	static final int MAX_PER_USER = 500;

	/** {@code previousUser} is null for a new token, the user itself when it only refreshed. */
	record Registered(UUID id, UUID previousUser) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	DeviceTokens(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Stores the token for this user, taking it from whoever had it before (the phone changed hands). */
	Registered register(UUID schoolId, UUID userId, String token, String platform, Instant now) {
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("user", userId)
			.addValue("token", token)
			.addValue("platform", platform)
			.addValue("now", Timestamp.from(now));
		return jdbc.queryForObject("""
				WITH old AS (SELECT user_id FROM device_token WHERE token = :token)
				INSERT INTO device_token (school_id, user_id, token, platform, created_at, last_seen)
				VALUES (:school, :user, :token, :platform, :now, :now)
				ON CONFLICT (token) DO UPDATE
				SET school_id = EXCLUDED.school_id, user_id = EXCLUDED.user_id, platform = EXCLUDED.platform,
				    last_seen = EXCLUDED.last_seen
				RETURNING id, (SELECT user_id FROM old) AS previous_user""", p,
				(rs, n) -> new Registered(rs.getObject("id", UUID.class), rs.getObject("previous_user", UUID.class)));
	}

	/** The user's tokens, most recently seen first. */
	List<String> tokensOf(UUID userId) {
		return jdbc.queryForList("""
				SELECT token FROM device_token WHERE user_id = :user ORDER BY last_seen DESC LIMIT :max""",
				new MapSqlParameterSource("user", userId).addValue("max", MAX_PER_USER), String.class);
	}

	/** Deletes tokens FCM no longer accepts (the app was uninstalled, or its data cleared). */
	void forget(Collection<String> tokens) {
		if (!tokens.isEmpty()) {
			jdbc.update("DELETE FROM device_token WHERE token IN (:tokens)", new MapSqlParameterSource("tokens", tokens));
		}
	}

}
