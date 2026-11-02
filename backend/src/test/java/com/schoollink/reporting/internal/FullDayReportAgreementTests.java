package com.schoollink.reporting.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.detection.internal.DetectionDriver;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D2, D36: on the same records, the report lists exactly the students the full-day absence rule
 * raises an alert for (no justification involved). Real commits; the schools are removed afterwards.
 * Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, DetectionDriver.Config.class })
class FullDayReportAgreementTests {

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	TestClock clock;

	@Autowired
	DetectionDriver detection;

	@Autowired
	PlatformTransactionManager transactions;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		clock.set(Instant.parse("2026-11-02T19:15:00Z")); // Monday 20:15, the nightly run
		world = TestWorld.create(jdbc, encoder);
	}

	@AfterEach
	void removeWorld() {
		clock.reset();
		world.remove(transactions);
	}

	@Test
	void theReportAgreesWithTheFullDayAlert() throws Exception {
		UUID period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time)
				VALUES (?, 2, '08:25', '09:20') RETURNING id""", UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
				VALUES (?, ?, 1, ?, ?, 'Maths')""", world.schoolX, world.classA, period2, world.user(TEACHER_A));
		UUID halfDay = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0501', 'Fabrice', 'Fouda', 'M', 2012) RETURNING id""", UUID.class, world.schoolX,
				world.classA);
		UUID first = session(world.period1);
		UUID second = session(period2);
		absent(first, world.studentA);
		absent(second, world.studentA);
		absent(first, halfDay);

		detection.nightly(world.schoolX, MONDAY);
		List<UUID> alerted = jdbc.queryForList("""
				SELECT student_id FROM risk_alert WHERE school_id = ? AND rule_type = 'FULL_DAY_ABSENCE'""", UUID.class,
				world.schoolX);

		String body = mockMvc.perform(get("/api/v1/reports/full-day-absences").param("date", MONDAY.toString())
			.header("Authorization", "Bearer " + world.token(DM_1)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		// only students carry an "id" (blocks and classes have blockId and classId)
		List<UUID> listed = json.readTree(body).findValues("id").stream().map(n -> UUID.fromString(n.asString())).toList();

		assertThat(alerted).containsExactly(world.studentA);
		assertThat(listed).containsExactlyInAnyOrderElementsOf(alerted);
	}

	private UUID session(UUID periodId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, now())""", id, world.schoolX, world.classA, MONDAY, periodId,
				world.user(TEACHER_A));
		return id;
	}

	private void absent(UUID session, UUID student) {
		jdbc.update("INSERT INTO attendance_record (school_id, session_id, student_id, status) VALUES (?, ?, ?, 'ABSENT')",
				world.schoolX, session, student);
	}

}
