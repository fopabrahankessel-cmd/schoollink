package com.schoollink.school.internal;

import com.schoollink.school.SchoolTimes;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSchoolTimes implements SchoolTimes {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcSchoolTimes(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Times times(UUID schoolId) {
		return jdbc.queryForObject("SELECT start_time, digest_first, digest_second FROM school WHERE id = :school",
				Map.of("school", schoolId), (rs, n) -> new Times(rs.getObject("start_time", LocalTime.class),
						rs.getObject("digest_first", LocalTime.class), rs.getObject("digest_second", LocalTime.class)));
	}

	@Override
	public void update(UUID schoolId, Times times) {
		jdbc.update("""
				UPDATE school SET start_time = :start, digest_first = :first, digest_second = :second WHERE id = :school""",
				new MapSqlParameterSource("school", schoolId).addValue("start", Time.valueOf(times.startTime()))
					.addValue("first", Time.valueOf(times.digestFirst()))
					.addValue("second", times.digestSecond() == null ? null : Time.valueOf(times.digestSecond()),
							Types.TIME));
	}

}
