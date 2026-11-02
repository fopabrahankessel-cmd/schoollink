package com.schoollink.attendance.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.attendance.AttendanceHistory;
import com.schoollink.attendance.AttendanceHistory.Day;
import com.schoollink.attendance.AttendanceHistory.DayAbsence;
import com.schoollink.testsupport.TestWorld;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.annotation.Transactional;

import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * UC-D9, D36: one day of a set of classes as the full-day absence report reads it: the periods with
 * a session per class, and the live absences (BR-15). Monday 2 November 2026. Generated data only.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceDayTests {

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	@Autowired
	AttendanceHistory history;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	TestWorld world;

	UUID period2;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time)
				VALUES (?, 2, '08:25', '09:20') RETURNING id""", UUID.class, world.schoolX);
	}

	@Test
	void dayListsRecordedPeriodsAndLiveAbsencesOfTheClasses() {
		UUID other = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0301', 'Autre', 'Fictif', 'M', 2012) RETURNING id""", UUID.class, world.schoolX,
				world.classA);
		UUID justification = jdbc.queryForObject("""
				INSERT INTO justification (school_id, reason, recorded_by) VALUES (?, 'ILLNESS', ?) RETURNING id""",
				UUID.class, world.schoolX, world.user(DM_1));
		UUID first = session(world.classA, world.period1);
		UUID second = session(world.classA, period2);
		jdbc.update("""
				INSERT INTO attendance_record (school_id, session_id, student_id, status, justification_id)
				VALUES (?, ?, ?, 'ABSENT', ?)""", world.schoolX, first, world.studentA, justification);
		absent(second, world.studentA);
		jdbc.update("""
				INSERT INTO attendance_record (school_id, session_id, student_id, status, cancelled_at, cancelled_by, cancel_reason)
				VALUES (?, ?, ?, 'ABSENT', now(), ?, 'Erreur de saisie')""", world.schoolX, first, other,
				world.user(DM_1));
		// another class of the school, not asked for
		UUID classB = session(world.classB, world.period1);
		absent(classB, other);

		Day day = history.day(world.schoolX, Set.of(world.classA), MONDAY);

		assertThat(day.recordedPeriods()).isEqualTo(Map.of(world.classA, Set.of(world.period1, period2)));
		assertThat(day.absences()).containsExactlyInAnyOrder(
				new DayAbsence(world.studentA, world.classA, world.period1, true),
				new DayAbsence(world.studentA, world.classA, period2, false));
	}

	@Test
	void anotherSchoolOrNoClassGivesNothing() {
		absent(session(world.classA, world.period1), world.studentA);

		assertThat(history.day(world.schoolY, Set.of(world.classA), MONDAY).absences()).isEmpty();
		assertThat(history.day(world.schoolX, Set.of(), MONDAY).recordedPeriods()).isEmpty();
	}

	private UUID session(UUID classId, UUID periodId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, now())""", id, world.schoolX, classId, MONDAY, periodId, world.user(TEACHER_A));
		return id;
	}

	private void absent(UUID session, UUID student) {
		jdbc.update("INSERT INTO attendance_record (school_id, session_id, student_id, status) VALUES (?, ?, ?, 'ABSENT')",
				world.schoolX, session, student);
	}

}
