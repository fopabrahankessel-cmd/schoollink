package com.schoollink.reporting.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D10, D40: one day of attendance in the DM's classes, period by period. 6e A has period 1
 * (07:30) and period 2 (08:30) on Mondays, 5e B has period 2. The clock is Monday 2 November 2026,
 * 18:00 in Douala, unless a test says otherwise. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class DayAttendanceTests {

	static final String DAY = "/api/v1/attendance/day";

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	static final Instant MONDAY_18H = Instant.parse("2026-11-02T17:00:00Z");

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

	UUID awa;

	UUID bruno;

	@BeforeEach
	void createWorld() {
		clock.set(MONDAY_18H);
		world = TestWorld.create(jdbc, encoder);
		period2 = jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time)
				VALUES (?, 2, '08:30', '09:25') RETURNING id""", UUID.class, world.schoolX);
		slot(world.classA, period2);
		slot(world.classB, period2);
		awa = student(world.classA, "TW-0502", "Awa", "Fictif");
		bruno = student(world.classA, "TW-0503", "Bruno", "Abena");
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@TestFactory
	Stream<DynamicTest> theDayIsForTheDmOnly() {
		return PermissionMatrix.row("GET day", w -> get(DAY).param("date", MONDAY.toString()))
			.expect(200, DM_1, DM_2)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, ADMIN_X, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void aDmSeesOnlyTheClassesOfTheirBlocks() throws Exception {
		day(DM_1, MONDAY).andExpect(status().isOk()).andExpect(jsonPath("$.classes[*].className", contains("6e A")));
		day(DM_2, MONDAY).andExpect(status().isOk()).andExpect(jsonPath("$.classes[*].className", contains("5e B")));
	}

	@Test
	void aRecordedPeriodListsItsAbsenteesWithTheirState() throws Exception {
		UUID session = session(world.classA, world.period1);
		UUID awaRecord = absent(session, awa);
		UUID brunoRecord = absent(session, bruno);
		UUID cancelled = absent(session, world.studentA);
		justify(brunoRecord);
		cancel(cancelled);

		day(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("2026-11-02"))
			.andExpect(jsonPath("$.schoolDay").value(true))
			.andExpect(jsonPath("$.classes[0].periods[0].number").value(1))
			.andExpect(jsonPath("$.classes[0].periods[0].status").value("RECORDED"))
			.andExpect(jsonPath("$.classes[0].periods[0].subject").value("Maths"))
			.andExpect(jsonPath("$.classes[0].periods[0].teacherName").value("Test TEACHER_A"))
			.andExpect(jsonPath("$.classes[0].periods[0].absentCount").value(2))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[*].lastName", contains("Abena", "Fictif", "Fictif")))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[*].firstName", contains("Bruno", "Awa", "Élève")))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[*].state",
					contains("JUSTIFIED", "UNJUSTIFIED", "CANCELLED")))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[1].recordId").value(awaRecord.toString()))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[1].studentId").value(awa.toString()))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[1].matricule").value("TW-0502"));
	}

	@Test
	void aCancelledAbsenceIsListedButNotCounted() throws Exception {
		UUID session = session(world.classA, world.period1);
		UUID awaRecord = absent(session, awa);
		absent(session, bruno);
		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].periods[0].absentCount").value(2))
			.andExpect(jsonPath("$.totals.absentStudents").value(2));

		mockMvc.perform(withToken(DM_1, post("/api/v1/records/{id}/cancel", awaRecord))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Erreur de saisie\"}")).andExpect(status().is2xxSuccessful());

		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].periods[0].absentCount").value(1))
			.andExpect(jsonPath("$.classes[0].periods[0].absences", hasSize(2)))
			.andExpect(jsonPath("$.classes[0].periods[0].absences[1].state").value("CANCELLED"))
			.andExpect(jsonPath("$.totals.absentStudents").value(1));
	}

	@Test
	void aPastPeriodWithoutRollCallIsNotRecorded() throws Exception {
		clock.set(Instant.parse("2026-11-03T09:00:00Z"));
		session(world.classA, world.period1);

		day(DM_1, MONDAY).andExpect(status().isOk())
			.andExpect(jsonPath("$.classes[0].periods[*].status", contains("RECORDED", "NOT_RECORDED")))
			.andExpect(jsonPath("$.classes[0].periods[1].teacherName").value("Test TEACHER_A"))
			.andExpect(jsonPath("$.classes[0].periods[1].absences", empty()))
			.andExpect(jsonPath("$.totals.notRecorded").value(1));
	}

	@Test
	void todayAPeriodIsUpcomingUntilItStarts() throws Exception {
		clock.set(Instant.parse("2026-11-02T06:29:00Z"));
		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].periods[*].status", contains("UPCOMING", "UPCOMING")))
			.andExpect(jsonPath("$.totals.notRecorded").value(0));

		clock.set(Instant.parse("2026-11-02T06:31:00Z"));
		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].periods[*].status", contains("NOT_RECORDED", "UPCOMING")))
			.andExpect(jsonPath("$.totals.notRecorded").value(1));
	}

	@Test
	void withoutDateItIsTodayAtSchool() throws Exception {
		clock.set(Instant.parse("2026-11-03T23:30:00Z"));
		mockMvc.perform(withToken(DM_1, get(DAY)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.date").value("2026-11-04"));
	}

	@Test
	void aSessionOutsideTheTimetableIsStillShown() throws Exception {
		UUID session = session(world.classA, world.period1);
		absent(session, awa);
		jdbc.update("DELETE FROM timetable_slot WHERE id = ?", world.slotA);

		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].periods[0].number").value(1))
			.andExpect(jsonPath("$.classes[0].periods[0].status").value("RECORDED"))
			.andExpect(jsonPath("$.classes[0].periods[0].subject").value(nullValue()))
			.andExpect(jsonPath("$.classes[0].periods[0].teacherName").value("Test TEACHER_A"))
			.andExpect(jsonPath("$.classes[0].periods[0].absentCount").value(1));
	}

	@Test
	void aStudentAbsentAllDayCountsOnce() throws Exception {
		absent(session(world.classA, world.period1), awa);
		absent(session(world.classA, period2), awa);

		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].periods[0].absences", hasSize(1)))
			.andExpect(jsonPath("$.classes[0].periods[1].absences", hasSize(1)))
			.andExpect(jsonPath("$.totals.absentStudents").value(1));
	}

	@Test
	void theDaysLatenessIsUnderTheStudentsClass() throws Exception {
		UUID late = lateness(bruno, "08:05");
		UUID cancelled = lateness(awa, "07:48");
		jdbc.update("UPDATE lateness_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur' WHERE id = ?",
				world.user(DM_1), cancelled);

		day(DM_1, MONDAY).andExpect(jsonPath("$.classes[0].lateness[*].firstName", contains("Awa", "Bruno")))
			.andExpect(jsonPath("$.classes[0].lateness[*].state", contains("CANCELLED", "UNJUSTIFIED")))
			.andExpect(jsonPath("$.classes[0].lateness[1].recordId").value(late.toString()))
			.andExpect(jsonPath("$.classes[0].lateness[1].arrivalTime").value("08:05:00"))
			.andExpect(jsonPath("$.totals.lateness").value(1));
	}

	@Test
	void aFutureDateIsRefused() throws Exception {
		day(DM_1, MONDAY.plusDays(1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("day.date_in_future"));
	}

	@Test
	void aWeekendIsNotASchoolDay() throws Exception {
		clock.set(Instant.parse("2026-11-09T09:00:00Z"));
		day(DM_1, LocalDate.parse("2026-11-07")).andExpect(status().isOk())
			.andExpect(jsonPath("$.schoolDay").value(false))
			.andExpect(jsonPath("$.classes", empty()))
			.andExpect(jsonPath("$.totals.notRecorded").value(0))
			.andExpect(jsonPath("$.totals.absentStudents").value(0))
			.andExpect(jsonPath("$.totals.lateness").value(0));
	}

	private ResultActions day(Actor actor, LocalDate date) throws Exception {
		return mockMvc.perform(withToken(actor, get(DAY).param("date", date.toString())));
	}

	private MockHttpServletRequestBuilder withToken(Actor actor, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + world.token(actor));
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

	private UUID absent(UUID session, UUID student) {
		return jdbc.queryForObject("""
				INSERT INTO attendance_record (school_id, session_id, student_id, status) VALUES (?, ?, ?, 'ABSENT')
				RETURNING id""", UUID.class, world.schoolX, session, student);
	}

	private void justify(UUID record) {
		UUID justification = jdbc.queryForObject("""
				INSERT INTO justification (school_id, reason, recorded_by) VALUES (?, 'ILLNESS', ?) RETURNING id""",
				UUID.class, world.schoolX, world.user(DM_1));
		jdbc.update("UPDATE attendance_record SET justification_id = ? WHERE id = ?", justification, record);
	}

	private void cancel(UUID record) {
		jdbc.update("UPDATE attendance_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur' WHERE id = ?",
				world.user(DM_1), record);
	}

	private UUID lateness(UUID student, String arrival) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?, ?::time, ?, now())""", id, world.schoolX, student, MONDAY, arrival,
				world.user(ASSISTANT_DM_1));
		return id;
	}

}
