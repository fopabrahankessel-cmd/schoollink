package com.schoollink.school.internal;

import com.schoollink.school.SchoolSummary;
import com.schoollink.school.Schools;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSchools implements Schools {

	private final JdbcTemplate jdbc;

	JdbcSchools(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<SchoolSummary> listForPicker() {
		return jdbc.query("SELECT id, name FROM school ORDER BY name",
				(rs, row) -> new SchoolSummary(rs.getObject("id", UUID.class), rs.getString("name")));
	}

	@Override
	public String name(UUID schoolId) {
		return jdbc.queryForObject("SELECT name FROM school WHERE id = ?", String.class, schoolId);
	}

}
