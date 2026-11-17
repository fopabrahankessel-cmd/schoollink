package com.schoollink.auth.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.people.ParentPasswordResets;
import com.schoollink.school.SchoolCalendar;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D46: a parent resets a forgotten password at most {@link #MAX_RESETS} times per school year. Only
 * completed resets count (the code was entered and a new password set), so somebody who merely
 * knows the number cannot use them up by asking for codes; the first sign-in, which chooses the
 * first password, is not a reset. The year runs from the start of the school's latest academic year
 * begun, its summer holidays included; resets before the admin's latest allowance do not count.
 */
@Component
class PasswordResetLimit implements ParentPasswordResets {

	static final int MAX_RESETS = 3;

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolCalendar calendar;

	private final AuditLog audit;

	private final Clock clock;

	PasswordResetLimit(NamedParameterJdbcTemplate jdbc, SchoolCalendar calendar, AuditLog audit, Clock clock) {
		this.jdbc = jdbc;
		this.calendar = calendar;
		this.audit = audit;
		this.clock = clock;
	}

	boolean isLocked(UUID schoolId, UUID userId) {
		return !locked(schoolId, Set.of(userId)).isEmpty();
	}

	@Override
	public Set<UUID> locked(UUID schoolId, Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return new HashSet<>(); // asked with null for a guardian without an account
		}
		LocalDate today = clock.instant().atZone(calendar.zone(schoolId)).toLocalDate();
		Instant since = calendar.yearStart(schoolId, today)
			.map(start -> start.atStartOfDay(calendar.zone(schoolId)).toInstant())
			.orElse(Instant.EPOCH);
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("users", userIds)
			.addValue("since", Timestamp.from(since))
			.addValue("max", MAX_RESETS);
		return new HashSet<>(jdbc.queryForList("""
				SELECT s.user_id FROM parent_sign_in s
				WHERE s.school_id = :school AND s.user_id IN (:users) AND s.password_reset AND s.verified_at >= :since
				  AND NOT EXISTS (SELECT 1 FROM password_reset_allowance a
				                  WHERE a.user_id = s.user_id AND a.school_id = s.school_id AND a.created_at >= s.verified_at)
				GROUP BY s.user_id HAVING count(*) >= :max""", p, UUID.class));
	}

	@Override
	public void allowAgain(UUID schoolId, UUID userId, UUID adminId) {
		jdbc.update("""
				INSERT INTO password_reset_allowance (school_id, user_id, allowed_by, created_at)
				VALUES (:school, :user, :admin, :now)""",
				new MapSqlParameterSource("school", schoolId).addValue("user", userId)
					.addValue("admin", adminId)
					.addValue("now", Timestamp.from(clock.instant())));
		audit.record(new AuditEntry(schoolId, adminId, AuditAction.CREATE, "password_reset_allowance", userId.toString(),
				null, Map.of("passwordResetsAllowed", true)));
	}

}
