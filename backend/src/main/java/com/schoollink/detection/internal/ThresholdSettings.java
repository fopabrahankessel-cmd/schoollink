package com.schoollink.detection.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import com.schoollink.detection.RuleType;
import com.schoollink.school.SchoolTimes;
import com.schoollink.school.SchoolTimes.Times;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D7: the DM's settings, one set per school: the threshold rules (BR-16, D9, D28), the school
 * start time (BR-7) and the SMS digest times (BR-22, D6). Any DM of the school reads and changes
 * them; each change is audited with both versions (NFR-11).
 */
@Service
class ThresholdSettings {

	/** BR-7: a start time outside these is a typing error. */
	static final LocalTime EARLIEST_START = LocalTime.of(5, 0);

	static final LocalTime LATEST_START = LocalTime.of(12, 0);

	/** BR-22: no SMS to parents at night. */
	static final LocalTime EARLIEST_DIGEST = LocalTime.of(6, 0);

	static final LocalTime LATEST_DIGEST = LocalTime.of(20, 0);

	record RuleRequest(@NotNull RuleType type, @NotNull Integer count, @NotNull Boolean active) {
	}

	/** {@code digestTimes}: one or two times, in order (D6). */
	record SettingsRequest(@NotNull LocalTime startTime,
			@NotNull @Size(min = 1, max = 2) List<@NotNull LocalTime> digestTimes,
			@NotNull @Size(max = 10) List<@NotNull @Valid RuleRequest> rules) {
	}

	/** {@code min} and {@code max}: the counts the DM may choose, for the settings screens. */
	record RuleView(RuleType type, int count, RuleType.Unit unit, RuleType.Window window, boolean active, int min,
			int max, Instant updatedAt, UUID updatedBy) {
	}

	record SettingsView(LocalTime startTime, List<LocalTime> digestTimes, List<RuleView> rules) {
	}

	private final NamedParameterJdbcTemplate jdbc;

	private final SchoolTimes schoolTimes;

	private final AuditLog audit;

	private final Clock clock;

	ThresholdSettings(NamedParameterJdbcTemplate jdbc, SchoolTimes schoolTimes, AuditLog audit, Clock clock) {
		this.jdbc = jdbc;
		this.schoolTimes = schoolTimes;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	SettingsView get(CurrentUser user) {
		return view(dmSchool(user), false);
	}

	@Transactional
	SettingsView update(CurrentUser user, SettingsRequest request) {
		UUID school = dmSchool(user);
		validate(request);
		// locked: two DMs saving at once are applied one after the other
		SettingsView before = view(school, true);

		List<LocalTime> digests = request.digestTimes();
		Times times = new Times(request.startTime(), digests.get(0), digests.size() > 1 ? digests.get(1) : null);
		if (!times.equals(timesOf(before))) {
			schoolTimes.update(school, times);
		}
		Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
		for (RuleRequest rule : request.rules()) {
			jdbc.update("""
					UPDATE threshold_rule SET count = :count, active = :active, updated_at = :now, updated_by = :by
					WHERE school_id = :school AND type = :type AND (count <> :count OR active <> :active)""",
					new MapSqlParameterSource("school", school).addValue("type", rule.type().name())
						.addValue("count", rule.count())
						.addValue("active", rule.active())
						.addValue("now", Timestamp.from(now))
						.addValue("by", user.userId()));
		}
		SettingsView after = view(school, false);
		// NFR-11: only a real change is recorded
		if (!snapshot(before).equals(snapshot(after))) {
			audit.record(new AuditEntry(school, user.userId(), AuditAction.UPDATE, "settings", school.toString(),
					snapshot(before), snapshot(after)));
		}
		return after;
	}

	/** UC-D7: the DM only; settings are the school's, not a block's, so any DM of the school. */
	private static UUID dmSchool(CurrentUser user) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		return user.schoolId();
	}

	private static void validate(SettingsRequest request) {
		LocalTime start = request.startTime();
		if (start.isBefore(EARLIEST_START) || start.isAfter(LATEST_START)) {
			throw ApiException.invalid("settings.start_time", "startTime", EARLIEST_START, LATEST_START);
		}
		List<LocalTime> digests = request.digestTimes();
		boolean ordered = digests.size() < 2 || digests.get(1).isAfter(digests.get(0));
		boolean daytime = digests.stream().noneMatch(t -> t.isBefore(EARLIEST_DIGEST) || t.isAfter(LATEST_DIGEST));
		if (!ordered || !daytime) {
			throw ApiException.invalid("settings.digest_times", "digestTimes", EARLIEST_DIGEST, LATEST_DIGEST);
		}
		// BR-16: each rule exactly once, so a rule is never dropped by mistake
		Set<RuleType> seen = EnumSet.noneOf(RuleType.class);
		boolean once = request.rules().stream().allMatch(r -> seen.add(r.type()));
		if (!once || seen.size() != RuleType.values().length) {
			throw ApiException.invalid("settings.rules_incomplete", "rules");
		}
		for (int i = 0; i < request.rules().size(); i++) {
			RuleRequest rule = request.rules().get(i);
			if (rule.count() < rule.type().min() || rule.count() > rule.type().max()) {
				throw ApiException.invalid("settings.count_out_of_range", "rules[" + i + "].count", rule.type().min(),
						rule.type().max());
			}
		}
	}

	private SettingsView view(UUID school, boolean lock) {
		Times times = schoolTimes.times(school);
		List<RuleView> rules = new ArrayList<>(jdbc.query("""
				SELECT type, count, active, updated_at, updated_by FROM threshold_rule WHERE school_id = :school"""
				+ (lock ? " FOR UPDATE" : ""), Map.of("school", school), (rs, n) -> {
					RuleType type = RuleType.valueOf(rs.getString("type"));
					return new RuleView(type, rs.getInt("count"), type.unit(), type.window(), rs.getBoolean("active"),
							type.min(), type.max(), rs.getTimestamp("updated_at").toInstant(),
							rs.getObject("updated_by", UUID.class));
				}));
		// in the order of RuleType, as the screens list them
		rules.sort(Comparator.comparing(RuleView::type));
		List<LocalTime> digests = times.digestSecond() == null ? List.of(times.digestFirst())
				: List.of(times.digestFirst(), times.digestSecond());
		return new SettingsView(times.startTime(), digests, List.copyOf(rules));
	}

	private static Times timesOf(SettingsView view) {
		List<LocalTime> digests = view.digestTimes();
		return new Times(view.startTime(), digests.get(0), digests.size() > 1 ? digests.get(1) : null);
	}

	/** What the audit keeps: the settings, without who changed each rule last. */
	private static Map<String, Object> snapshot(SettingsView view) {
		Map<String, Object> rules = new LinkedHashMap<>();
		for (RuleView rule : view.rules()) {
			rules.put(rule.type().name(), Map.of("count", rule.count(), "active", rule.active()));
		}
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("startTime", view.startTime().toString());
		snapshot.put("digestTimes", view.digestTimes().stream().map(LocalTime::toString).toList());
		snapshot.put("rules", rules);
		return snapshot;
	}

}
