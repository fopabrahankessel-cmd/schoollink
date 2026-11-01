package com.schoollink.discipline.internal;

import com.schoollink.discipline.Summonses;
import com.schoollink.people.Students;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-D3: the DM in charge of the student's class summons a parent, on their own initiative (from
 * an alert, the DM decides SUMMON on the alert, task 3.5). BR-23: sent at once.
 */
@Service
class SummonsService {

	/** {@code meetingDate} and {@code meetingTime} may be left out: the next school day at the start (D31). */
	record SummonsRequest(@NotNull UUID studentId, LocalDate meetingDate, LocalTime meetingTime, String reason) {
	}

	record SummonsView(UUID id, UUID studentId, UUID alertId, LocalDate meetingDate, LocalTime meetingTime,
			String reason, UUID sentBy, Instant sentAt) {
	}

	private final Summonses summonses;

	private final Students students;

	private final NamedParameterJdbcTemplate jdbc;

	SummonsService(Summonses summonses, Students students, NamedParameterJdbcTemplate jdbc) {
		this.summonses = summonses;
		this.students = students;
		this.jdbc = jdbc;
	}

	@Transactional
	SummonsView summon(CurrentUser user, SummonsRequest request) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		// BR-18, D17: the DM in charge of the student's class; another school's or out of scope is not found
		boolean inScope = students.classOf(school, request.studentId())
			.filter(user.classesAs(Role.DM)::contains)
			.isPresent();
		if (!inScope) {
			throw ApiException.notFound("not_found");
		}
		UUID id = summonses.issue(school, request.studentId(), null, user.userId(), request.meetingDate(),
				request.meetingTime(), request.reason());
		return jdbc.queryForObject("""
				SELECT id, student_id, alert_id, meeting_date, meeting_time, reason, sent_by, sent_at FROM summons
				WHERE id = :id AND school_id = :school""", Map.of("id", id, "school", school),
				(rs, n) -> new SummonsView(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
						rs.getObject("alert_id", UUID.class), rs.getObject("meeting_date", LocalDate.class),
						rs.getObject("meeting_time", LocalTime.class), rs.getString("reason"),
						rs.getObject("sent_by", UUID.class), rs.getObject("sent_at", Timestamp.class).toInstant()));
	}

}
