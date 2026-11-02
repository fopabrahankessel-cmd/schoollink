package com.schoollink.reporting.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static com.schoollink.testsupport.Actor.ADMIN_X;
import static com.schoollink.testsupport.Actor.ADMIN_Y;
import static com.schoollink.testsupport.Actor.ANONYMOUS;
import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.COUNSELLOR_X;
import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.DM_2;
import static com.schoollink.testsupport.Actor.PARENT_A;
import static com.schoollink.testsupport.Actor.RESEARCHER;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D9, D36: the students of the DM's blocks absent in every timetabled period of a day (D2), none
 * of those absences cancelled (BR-15) or justified. Class 6e A has two lessons on Mondays. The clock
 * is Tuesday 3 November 2026, 18:00 in Douala, unless a test says otherwise. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class FullDayReportTests {

	static final String REPORT = "/api/v1/reports/full-day-absences";

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	static final Instant TUESDAY_18H = Instant.parse("2026-11-03T17:00:00Z");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	TestClock clock;

	TestWorld world;

	UUID period2;

	@BeforeEach
	void createWorld() {
		clock.set(TUESDAY_18H);
		world = TestWorld.create(jdbc, encoder);
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time)
				VALUES (?, 2, '08:25', '09:20') RETURNING id""", UUID.class, world.schoolX);
		slot(world.classA, period2);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@TestFactory
	Stream<DynamicTest> onlyTheDmMayAskForTheReport() {
		return PermissionMatrix.row("GET report", w -> get(REPORT).param("date", MONDAY.toString()))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheDmMayAskForTheDefaultDay() {
		return PermissionMatrix.row("GET default day", w -> get(REPORT + "/default-date"))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void listsStudentsAbsentInEveryTimetabledPeriod() throws Exception {
		UUID once = student(world.classA, "TW-0402", "Bruno", "Abena");
		UUID first = session(world.classA, world.period1);
		UUID second = session(world.classA, period2);
		absent(first, world.studentA);
		absent(second, world.studentA);
		absent(first, once);

		report(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("2026-11-02"))
			.andExpect(jsonPath("$.studentCount").value(1))
			.andExpect(jsonPath("$.classCount").value(1))
			.andExpect(jsonPath("$.blocks", hasSize(1)))
			.andExpect(jsonPath("$.blocks[0].blockName").value("Bloc 1"))
			.andExpect(jsonPath("$.blocks[0].classes[0].className").value("6e A"))
			.andExpect(jsonPath("$.blocks[0].classes[0].students", hasSize(1)))
			.andExpect(jsonPath("$.blocks[0].classes[0].students[0].id").value(world.studentA.toString()))
			.andExpect(jsonPath("$.blocks[0].classes[0].students[0].lastName").value("Fictif"))
			.andExpect(jsonPath("$.blocks[0].classes[0].students[0].firstName").value("Élève"))
			.andExpect(jsonPath("$.blocks[0].classes[0].students[0].matricule").value("TW-0001"))
			.andExpect(jsonPath("$.unrecorded", empty()));
	}

	@Test
	void studentsAreByLastThenFirstName() throws Exception {
		UUID zoe = student(world.classA, "TW-0403", "Zoé", "Abena");
		UUID ali = student(world.classA, "TW-0404", "Ali", "abena");
		UUID first = session(world.classA, world.period1);
		UUID second = session(world.classA, period2);
		for (UUID student : new UUID[] { world.studentA, zoe, ali }) {
			absent(first, student);
			absent(second, student);
		}

		report(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[0].classes[0].students[*].firstName", contains("Ali", "Zoé", "Élève")));
	}

	@Test
	void cancelledOrJustifiedAbsenceKeepsAStudentOff() throws Exception { // BR-15, D36
		UUID other = student(world.classA, "TW-0405", "Chloé", "Bella");
		UUID first = session(world.classA, world.period1);
		UUID second = session(world.classA, period2);
		absent(first, world.studentA);
		jdbc.update("""
				INSERT INTO attendance_record (school_id, session_id, student_id, status, cancelled_at, cancelled_by, cancel_reason)
				VALUES (?, ?, ?, 'ABSENT', now(), ?, 'Erreur de saisie')""", world.schoolX, second, world.studentA,
				world.user(DM_1));
		UUID justification = jdbc.queryForObject("""
				INSERT INTO justification (school_id, reason, recorded_by) VALUES (?, 'ILLNESS', ?) RETURNING id""",
				UUID.class, world.schoolX, world.user(DM_1));
		absent(first, other);
		jdbc.update("""
				INSERT INTO attendance_record (school_id, session_id, student_id, status, justification_id)
				VALUES (?, ?, ?, 'ABSENT', ?)""", world.schoolX, second, other, justification);

		report(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.studentCount").value(0))
			.andExpect(jsonPath("$.blocks", empty()));
	}

	@Test
	void aClassWithALessonNotRecordedListsNobodyAndIsNamed() throws Exception { // D2
		absent(session(world.classA, world.period1), world.studentA);

		report(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks", empty()))
			.andExpect(jsonPath("$.unrecorded", hasSize(1)))
			.andExpect(jsonPath("$.unrecorded[0].classId").value(world.classA.toString()))
			.andExpect(jsonPath("$.unrecorded[0].className").value("6e A"))
			.andExpect(jsonPath("$.unrecorded[0].periods", contains(2)));
	}

	@Test
	void refusesDaysThatAreNotFinishedOrNotSchoolDays() throws Exception {
		report(DM_1, LocalDate.parse("2026-10-31")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("report.not_a_school_day"));
		report(DM_1, LocalDate.parse("2026-11-04")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("report.date_in_future"));

		clock.set(Instant.parse("2026-11-03T15:59:00Z")); // Tuesday 16:59 in Douala
		report(DM_1, LocalDate.parse("2026-11-03")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("report.day_not_finished"))
			.andExpect(jsonPath("$.message")
				.value("La journée n'est pas terminée : le rapport du jour est disponible à partir de 17 h."));
		clock.set(Instant.parse("2026-11-03T16:00:00Z"));
		report(DM_1, LocalDate.parse("2026-11-03")).andExpect(status().isOk());
	}

	@Test
	void defaultDateIsTodayFrom17hElseThePreviousSchoolDay() throws Exception {
		clock.set(Instant.parse("2026-11-03T15:59:00Z"));
		defaultDate().andExpect(jsonPath("$.date").value("2026-11-02"));
		clock.set(Instant.parse("2026-11-03T16:00:00Z"));
		defaultDate().andExpect(jsonPath("$.date").value("2026-11-03"));
		clock.set(Instant.parse("2026-11-02T07:00:00Z")); // Monday 08:00
		defaultDate().andExpect(jsonPath("$.date").value("2026-10-30"));
	}

	@Test
	void aDmWithTwoBlocksGetsBothAndNothingElse() throws Exception { // NFR-8, D36
		UUID block3 = jdbc.queryForObject("INSERT INTO block (school_id, name) VALUES (?, 'Bloc 3') RETURNING id",
				UUID.class, world.schoolX);
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role, block_id) VALUES (?, ?, 'DM', ?)",
				world.schoolX, world.user(DM_1), block3);
		UUID classD = jdbc.queryForObject("""
				INSERT INTO school_class (school_id, academic_year_id, block_id, name, level)
				VALUES (?, ?, ?, '4e D', '4e') RETURNING id""", UUID.class, world.schoolX, world.yearX, block3);
		slot(classD, world.period1);
		slot(world.classB, world.period1);
		UUID inD = student(classD, "TW-0406", "Daniel", "Djeumo");
		UUID inB = student(world.classB, "TW-0407", "Estelle", "Essomba");
		absent(session(world.classA, world.period1), world.studentA);
		absent(session(world.classA, period2), world.studentA);
		absent(session(classD, world.period1), inD);
		absent(session(world.classB, world.period1), inB);

		report(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.studentCount").value(2))
			.andExpect(jsonPath("$.classCount").value(2))
			.andExpect(jsonPath("$.blocks[*].blockName", contains("Bloc 1", "Bloc 3")))
			.andExpect(jsonPath("$.blocks[1].classes[0].students[0].lastName").value("Djeumo"));
		report(DM_2, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.blocks[*].blockName", contains("Bloc 2")))
			.andExpect(jsonPath("$.blocks[0].classes[0].students[0].lastName").value("Essomba"));
	}

	@Test
	void generatingIsAuditedWithoutNames() throws Exception { // NFR-11
		absent(session(world.classA, world.period1), world.studentA);
		absent(session(world.classA, period2), world.studentA);

		report(DM_1, MONDAY).andExpect(status().isOk());

		Map<String, Object> row = jdbc.queryForMap("""
				SELECT action, entity_id, actor_id, after::text AS after FROM audit_log
				WHERE school_id = ? AND entity = 'full_day_report'""", world.schoolX);
		assertThat(row.get("action")).isEqualTo("EXPORT");
		assertThat(row.get("entity_id")).isEqualTo("2026-11-02");
		assertThat(row.get("actor_id")).isEqualTo(world.user(DM_1));
		assertThat((String) row.get("after")).contains("\"students\": 1", "\"classes\": 1", "\"date\": \"2026-11-02\"")
			.doesNotContain("Fictif", "TW-0001");
	}

	private ResultActions report(Actor actor, LocalDate date) throws Exception {
		return mockMvc.perform(get(REPORT).param("date", date.toString())
			.header("Authorization", "Bearer " + world.token(actor)));
	}

	private ResultActions defaultDate() throws Exception {
		return mockMvc.perform(get(REPORT + "/default-date").header("Authorization", "Bearer " + world.token(DM_1)))
			.andExpect(status().isOk());
	}

	private void slot(UUID classId, UUID periodId) {
		jdbc.update("""
				INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Maths')
				ON CONFLICT DO NOTHING""", world.schoolX, world.user(TEACHER_A), classId);
		jdbc.update("""
				INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
				VALUES (?, ?, 1, ?, ?, 'Maths')""", world.schoolX, classId, periodId, world.user(TEACHER_A));
	}

	private UUID student(UUID classId, String matricule, String first, String last) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, ?, 'F', 2012) RETURNING id""", UUID.class, world.schoolX, classId, matricule, first,
				last);
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
