package com.schoollink.school.internal;

import com.schoollink.school.SchoolClasses;
import java.util.HashMap;
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

	@Override
	public Set<UUID> blocksOf(UUID schoolId, Set<UUID> classIds) {
		if (classIds.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.queryForList(
				"SELECT DISTINCT block_id FROM school_class WHERE school_id = :school AND id IN (:classes)",
				Map.of("school", schoolId, "classes", classIds), UUID.class));
	}

	@Override
	public Map<UUID, String> names(UUID schoolId, Set<UUID> classIds) {
		Map<UUID, String> names = new HashMap<>();
		if (!classIds.isEmpty()) {
			jdbc.query("SELECT id, name FROM school_class WHERE school_id = :school AND id IN (:classes)",
					Map.of("school", schoolId, "classes", classIds),
					rs -> {
						names.put(rs.getObject("id", UUID.class), rs.getString("name"));
					});
		}
		return names;
	}

	@Override
	public boolean classExists(UUID schoolId, UUID classId) {
		return exists("SELECT 1 FROM school_class WHERE id = :id AND school_id = :school", schoolId, classId);
	}

	@Override
	public boolean yearExists(UUID schoolId, UUID academicYearId) {
		return exists("SELECT 1 FROM academic_year WHERE id = :id AND school_id = :school", schoolId, academicYearId);
	}

	@Override
	public Map<String, UUID> classesInYear(UUID schoolId, UUID academicYearId) {
		Map<String, UUID> classes = new HashMap<>();
		jdbc.query("SELECT id, name FROM school_class WHERE school_id = :school AND academic_year_id = :year",
				Map.of("school", schoolId, "year", academicYearId),
				rs -> {
					classes.put(rs.getString("name"), rs.getObject("id", UUID.class));
				});
		return Map.copyOf(classes);
	}

	@Override
	public boolean blockExists(UUID schoolId, UUID blockId) {
		return exists("SELECT 1 FROM block WHERE id = :id AND school_id = :school", schoolId, blockId);
	}

	@Override
	public String language(UUID schoolId) {
		return jdbc.queryForObject("SELECT language FROM school WHERE id = :school", Map.of("school", schoolId),
				String.class);
	}

	private boolean exists(String sql, UUID schoolId, UUID id) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + sql + ")",
				Map.of("school", schoolId, "id", id), Boolean.class));
	}

}
