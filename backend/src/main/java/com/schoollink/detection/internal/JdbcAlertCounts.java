package com.schoollink.detection.internal;

import com.schoollink.detection.AlertCounts;
import com.schoollink.detection.RuleType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAlertCounts implements AlertCounts {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcAlertCounts(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<RuleCount> byRule(UUID schoolId, Set<UUID> classIds, Instant from, Instant to) {
		Map<RuleType, RuleCount> counts = new EnumMap<>(RuleType.class);
		if (!classIds.isEmpty()) {
			jdbc.query("""
					SELECT rule_type, count(*) FILTER (WHERE status = 'OPEN') AS open,
					       count(*) FILTER (WHERE raised_at >= :from AND raised_at < :to) AS raised,
					       count(*) FILTER (WHERE closed_at >= :from AND closed_at < :to) AS closed
					FROM risk_alert WHERE school_id = :school AND class_id IN (:classes)
					GROUP BY rule_type""",
					new MapSqlParameterSource("school", schoolId).addValue("classes", classIds)
						.addValue("from", Timestamp.from(from))
						.addValue("to", Timestamp.from(to)),
					rs -> {
						RuleType rule = RuleType.valueOf(rs.getString("rule_type"));
						counts.put(rule, new RuleCount(rule, rs.getInt("open"), rs.getInt("raised"), rs.getInt("closed")));
					});
		}
		return Arrays.stream(RuleType.values())
			.map(rule -> counts.getOrDefault(rule, new RuleCount(rule, 0, 0, 0)))
			.toList();
	}

}
