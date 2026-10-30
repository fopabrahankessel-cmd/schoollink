package com.schoollink.people.internal;

import com.schoollink.people.Students;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStudents implements Students {

	private static final String CARD = "SELECT id, matricule, first_name, last_name, class_id FROM student";

	private final NamedParameterJdbcTemplate jdbc;

	JdbcStudents(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Set<UUID> idsInClass(UUID schoolId, UUID classId) {
		return Set.copyOf(jdbc.queryForList("SELECT id FROM student WHERE school_id = :school AND class_id = :class",
				Map.of("school", schoolId, "class", classId), UUID.class));
	}

	@Override
	public Optional<UUID> classOf(UUID schoolId, UUID studentId) {
		return jdbc.queryForList("SELECT class_id FROM student WHERE id = :id AND school_id = :school",
				Map.of("school", schoolId, "id", studentId), UUID.class).stream().findFirst();
	}

	@Override
	public List<Card> inClasses(UUID schoolId, Set<UUID> classIds, String query, int limit) {
		if (classIds.isEmpty()) {
			return List.of();
		}
		MapSqlParameterSource p = new MapSqlParameterSource("school", schoolId).addValue("classes", classIds);
		if (query == null) {
			return jdbc.query(CARD + """
					 WHERE school_id = :school AND class_id IN (:classes)
					ORDER BY lower(last_name), lower(first_name), matricule""", p, JdbcStudents::card);
		}
		String like = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		p.addValue("contains", "%" + like + "%").addValue("prefix", like + "%").addValue("limit", limit);
		return jdbc.query(CARD + """
				 WHERE school_id = :school AND class_id IN (:classes)
				  AND (first_name || ' ' || last_name ILIKE :contains OR last_name || ' ' || first_name ILIKE :contains
				       OR matricule ILIKE :prefix)
				ORDER BY lower(last_name), lower(first_name), matricule LIMIT :limit""", p, JdbcStudents::card);
	}

	@Override
	public Map<UUID, Card> cards(UUID schoolId, Collection<UUID> studentIds) {
		Map<UUID, Card> cards = new HashMap<>();
		if (!studentIds.isEmpty()) {
			jdbc.query(CARD + " WHERE school_id = :school AND id IN (:ids)",
					new MapSqlParameterSource("school", schoolId).addValue("ids", studentIds), rs -> {
						Card card = card(rs, 0);
						cards.put(card.id(), card);
					});
		}
		return cards;
	}

	private static Card card(ResultSet rs, int row) throws SQLException {
		return new Card(rs.getObject("id", UUID.class), rs.getString("matricule"), rs.getString("first_name"),
				rs.getString("last_name"), rs.getObject("class_id", UUID.class));
	}

}
