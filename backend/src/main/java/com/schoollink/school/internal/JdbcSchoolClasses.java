package com.schoollink.school.internal;

import com.schoollink.school.SchoolClasses;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSchoolClasses implements SchoolClasses {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcSchoolClasses(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<UUID> idsInBlocks(UUID schoolId, Set<UUID> blockIds) {
		if (blockIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.queryForList("SELECT id FROM school_class WHERE school_id = :school AND block_id IN (:blocks)",
				Map.of("school", schoolId, "blocks", blockIds), UUID.class));
	}

}
