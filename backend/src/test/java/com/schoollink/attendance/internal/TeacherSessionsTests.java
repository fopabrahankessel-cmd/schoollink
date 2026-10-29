package com.schoollink.attendance.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-T3 {@code GET /me/sessions} (task 2.2, D1, D3, D10, NFR-8) against a fake timetable. Generated
 * data only.
 *
 * <pre>
 * Monday 2026-11-02, TEACHER_A: P1 6e A (TestWorld), P2 5e B, P3 6e A; another teacher has P2 in 6e A
 * Tuesday: P1 6e A. Wednesday: nothing. Holiday: 9-13 November. Term 1 ends 18 December.
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class TeacherSessionsTests {

	static final String MONDAY = "2026-11-02";

	/** 08:40 in Douala (UTC+1), during period 2. */
	static final Instant MONDAY_0840 = Instant.parse("2026-11-02T07:40:00Z");

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

	TestWorld world;

	UUID period2;

	UUID period3;

	UUID colleague;

	@BeforeEach
	void createTimetable() {
		world = TestWorld.create(jdbc, encoder);
		period2 = period(2, "08:25", "09:20");
		period3 = period(3, "09:20", "10:15");
		UUID teacher = world.user(TEACHER_A);
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Maths')",
				world.schoolX, teacher, world.classB);
		slot(world.classB, 1, period2, teacher, "Maths");
		slot(world.classA, 1, period3, teacher, "Maths");
		slot(world.classA, 2, world.period1, teacher, "Maths");
		colleague = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, email) VALUES (?, 'Collègue', 'Fictif', ?) RETURNING id""",
				UUID.class, world.schoolX, "colleague-" + UUID.randomUUID() + "@example.test");
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Physique')",
				world.schoolX, colleague, world.classA);
		slot(world.classA, 1, period2, colleague, "Physique");
		jdbc.update("""
				INSERT INTO holiday (school_id, term_id, label, start_date, end_date)
				VALUES (?, ?, 'Congé fictif', '2026-11-09', '2026-11-13')""", world.schoolX, world.termX);
		clock.set(MONDAY_0840);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@TestFactory
	Stream<DynamicTest> onlyTeachersReadTheirSessions() { // NFR-8, D17
		return PermissionMatrix.row("GET my sessions", w -> get("/api/v1/me/sessions").param("date", MONDAY))
			.expect(200, TEACHER_A)
			.expect(403, ADMIN_X, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void todaysSessionsWithTheCurrentPeriodFirst() throws Exception { // UC-T1 step 1, UC-T3
		JsonNode day = mySessions(get("/api/v1/me/sessions"));

		assertThat(day.get("date").asString()).isEqualTo(MONDAY);
		assertThat(day.get("schoolDay").asBoolean()).isTrue();
		assertThat(summary(day)).containsExactly("P2 5e B Maths current", "P1 6e A Maths", "P3 6e A Maths");
		JsonNode current = day.get("sessions").get(0);
		assertThat(current.get("startTime").asString()).startsWith("08:25");
		assertThat(current.get("recorded").asBoolean()).isFalse();
		assertThat(current.get("sessionId").isNull()).isTrue();
	}

	@Test
	void beforeTheFirstPeriodNothingIsCurrent() throws Exception {
		clock.set(Instant.parse("2026-11-02T06:00:00Z")); // 07:00 in Douala

		assertThat(summary(mySessions(get("/api/v1/me/sessions")))).containsExactly("P1 6e A Maths", "P2 5e B Maths",
				"P3 6e A Maths");
	}

	@Test
	void todayIsReadInTheSchoolsTimeZone() throws Exception {
		clock.set(Instant.parse("2026-11-01T23:30:00Z")); // Sunday in UTC, Monday 00:30 in Douala

		assertThat(mySessions(get("/api/v1/me/sessions")).get("date").asString()).isEqualTo(MONDAY);
	}

	@Test
	void anotherDayHasNoCurrentPeriod() throws Exception {
		JsonNode tuesday = mySessions(get("/api/v1/me/sessions").param("date", "2026-11-03"));

		assertThat(summary(tuesday)).containsExactly("P1 6e A Maths");
	}

	@Test
	void recordedSessionsShowWhenAndHowManyAbsent() throws Exception { // UC-T3, BR-15
		UUID sessionId = UUID.randomUUID();
		// recorded by a colleague standing in: it still counts as recorded
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?::date, ?, ?, '2026-11-02T06:50:00Z')""", sessionId, world.schoolX, world.classA, MONDAY,
				world.period1, colleague);
		UUID second = student("TW-0102");
		UUID third = student("TW-0103");
		record(sessionId, world.studentA, "ABSENT");
		record(sessionId, second, "PRESENT");
		UUID cancelled = record(sessionId, third, "ABSENT");
		jdbc.update("UPDATE attendance_record SET cancelled_at = now(), cancelled_by = ?, cancel_reason = 'Erreur' WHERE id = ?",
				world.user(DM_1), cancelled);

		JsonNode p1 = mySessions(get("/api/v1/me/sessions")).get("sessions").get(1);

		assertThat(p1.get("periodNumber").asInt()).isEqualTo(1);
		assertThat(p1.get("recorded").asBoolean()).isTrue();
		assertThat(p1.get("sessionId").asString()).isEqualTo(sessionId.toString());
		assertThat(Instant.parse(p1.get("confirmedAt").asString())).isEqualTo(Instant.parse("2026-11-02T06:50:00Z"));
		assertThat(p1.get("absentCount").asInt()).isEqualTo(1);
	}

	@Test
	void lessonsOfAnotherYearsClassesAreNotExpected() throws Exception { // D1: the date's term picks the year
		UUID lastYear = jdbc.queryForObject("""
				INSERT INTO academic_year (school_id, label, start_date, end_date)
				VALUES (?, '2025-2026', '2025-09-08', '2026-06-30') RETURNING id""", UUID.class, world.schoolX);
		UUID oldClass = jdbc.queryForObject("""
				INSERT INTO school_class (school_id, academic_year_id, block_id, name, level)
				VALUES (?, ?, ?, '6e A (2025)', '6e') RETURNING id""", UUID.class, world.schoolX, lastYear, world.block1);
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Maths')",
				world.schoolX, world.user(TEACHER_A), oldClass);
		UUID period4 = period(4, "10:30", "11:25");
		slot(oldClass, 1, period4, world.user(TEACHER_A), "Maths");

		assertThat(summary(mySessions(get("/api/v1/me/sessions")))).containsExactly("P2 5e B Maths current",
				"P1 6e A Maths", "P3 6e A Maths");
	}

	@Test
	void aSchoolDayWithoutLessonsIsEmpty() throws Exception {
		JsonNode wednesday = mySessions(get("/api/v1/me/sessions").param("date", "2026-11-04"));

		assertThat(wednesday.get("schoolDay").asBoolean()).isTrue();
		assertThat(wednesday.get("sessions")).isEmpty();
	}

	@Test
	void nothingIsExpectedOnWeekendsHolidaysOrOutsideTerms() throws Exception { // D3, D10
		for (String date : List.of("2026-11-07", "2026-11-09", "2026-12-21", "2026-09-01")) {
			JsonNode day = mySessions(get("/api/v1/me/sessions").param("date", date));

			assertThat(day.get("schoolDay").asBoolean()).as(date).isFalse();
			assertThat(day.get("sessions")).as(date).isEmpty();
		}
	}

	@Test
	void aMalformedDateIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/me/sessions").param("date", "02/11/2026")
			.header("Authorization", "Bearer " + world.token(TEACHER_A)))
			.andExpect(status().isBadRequest());
	}

	private JsonNode mySessions(MockHttpServletRequestBuilder request) throws Exception {
		String body = mockMvc.perform(request.header("Authorization", "Bearer " + world.token(TEACHER_A)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(body);
	}

	/** "P2 5e B Maths current", in the order returned. */
	private static List<String> summary(JsonNode day) {
		return day.get("sessions")
			.valueStream()
			.map(s -> "P" + s.get("periodNumber").asInt() + " " + s.get("className").asString() + " "
					+ s.get("subject").asString() + (s.get("current").asBoolean() ? " current" : ""))
			.toList();
	}

	private UUID period(int number, String start, String end) {
		return jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, ?, ?::time, ?::time) RETURNING id""",
				UUID.class, world.schoolX, number, start, end);
	}

	private void slot(UUID classId, int weekday, UUID period, UUID teacher, String subject) {
		jdbc.update("""
				INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
				VALUES (?, ?, ?, ?, ?, ?)""", world.schoolX, classId, weekday, period, teacher, subject);
	}

	private UUID student(String matricule) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, 'Élève', 'Fictif', 'M', 2013) RETURNING id""", UUID.class, world.schoolX, world.classA,
				matricule);
	}

	private UUID record(UUID session, UUID student, String status) {
		return jdbc.queryForObject("""
				INSERT INTO attendance_record (school_id, session_id, student_id, status) VALUES (?, ?, ?, ?) RETURNING id""",
				UUID.class, world.schoolX, session, student, status);
	}

}
