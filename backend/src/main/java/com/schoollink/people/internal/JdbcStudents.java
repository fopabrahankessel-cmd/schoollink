package com.schoollink.people.internal;

import com.schoollink.people.Students;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStudents implements Students {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcStudents(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<UUID> idsInClass(UUID schoolId, UUID classId) {
		return Set.copyOf(jdbc.queryForList("SELECT id FROM student WHERE school_id = :school AND class_id = :class",
				Map.of("school", schoolId, "class", classId), UUID.class));
	}

}
