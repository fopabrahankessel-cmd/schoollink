package com.schoollink.simulation;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.detection.internal.DetectionDriver;
import com.schoollink.notification.internal.NotificationDriver;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 3.13, the phase 3 exit check (SPEC §12): "a full test day produces correct alerts and one
 * digest per student", run over a whole school week through the real endpoints and jobs.
 *
 * <p>
 * Class 6e A of the fictional school X (Africa/Douala, UTC+1) has four lessons a day, Monday to
 * Friday, and the seeded thresholds (D9): 1 full day, 5 late days in a row, 15 unjustified lessons
 * in the term. Each day the gate records lateness ({@code PUT /lateness/{uuid}}), the teacher locks
 * each lesson ({@code PUT /sessions/{uuid}}), the SMS digest runs at 13:00 and 17:00 (BR-22, D6), and
 * the nightly detection at 20:15 (BR-17). Every guardian here has no app, so every absence and
 * lateness waits for the digest.
 *
 * <pre>
 * Awa     absent all day Monday                        -> full-day alert on Monday, at the 4th lesson
 * Bruno   late every day                               -> lateness alert on Friday, at the gate
 * Chloé   late every day but Wednesday                 -> none: Wednesday breaks the streak (D3)
 * Dan     misses lessons 1-3 every day (15)            -> unjustified alert on Friday, at the 3rd lesson
 * Esther  misses lessons 1-3 on Wednesday, whose 4th   -> none on Wednesday: the day is not full (D2);
 *         lesson is never recorded; all day Thursday      full-day alert on Thursday
 * Gaston  late and misses lesson 2 on Monday           -> one SMS with both, no alert
 * Élève   present and on time all week (TestWorld)     -> nothing
 * </pre>
 *
 * No test transaction: detection runs after the real commits. The school is removed afterwards.
 * Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class, NotificationDriver.Config.class,
		DetectionDriver.Config.class })
class FullTestWeekTests {

	static final ZoneId DOUALA = ZoneId.of("Africa/Douala");

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	static final List<LocalDate> WEEK = MONDAY.datesUntil(MONDAY.plusDays(5)).toList();

	static final LocalTime[] LESSON_ENDS = { LocalTime.of(8, 25), LocalTime.of(9, 25), LocalTime.of(10, 25),
			LocalTime.of(11, 25) };

	static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

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
	PlatformTransactionManager transactions;

	@Autowired
	NotificationDriver notifications;

	@Autowired
	DetectionDriver detection;

	TestWorld world;

	/** Lessons 1 to 4. */
	final List<UUID> periods = new ArrayList<>();

	final Map<String, UUID> students = new LinkedHashMap<>();

	/** The script: who is absent from which lessons, who arrives late when. */
	final Map<LocalDate, Map<String, Set<Integer>>> absent = new LinkedHashMap<>();

	final Map<LocalDate, Map<String, LocalTime>> late = new LinkedHashMap<>();

	/** Lessons the teacher never records. */
	final Map<LocalDate, Set<Integer>> unrecorded = new LinkedHashMap<>();

	@BeforeEach
	void createSchool() {
		clock.set(MONDAY.atTime(7, 0).atZone(DOUALA).toInstant());
		world = TestWorld.create(jdbc, encoder);
		notifications.reset();
		periods.add(world.period1);
		periods.add(period(2, "08:30", "09:25"));
		periods.add(period(3, "09:30", "10:25"));
		periods.add(period(4, "10:30", "11:25"));
		// D1: four lessons a day for 6e A, Monday to Friday, all taught by TEACHER_A
		for (int weekday = 1; weekday <= 5; weekday++) {
			for (int lesson = 1; lesson <= 4; lesson++) {
				if (weekday == 1 && lesson == 1) {
					continue; // TestWorld's own slot
				}
				jdbc.update("""
						INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
						VALUES (?, ?, ?, ?, ?, 'Maths')""", world.schoolX, world.classA, weekday,
						periods.get(lesson - 1), world.user(TEACHER_A));
			}
		}
		int n = 0;
		for (String name : List.of("Awa", "Bruno", "Chloé", "Dan", "Esther", "Gaston")) {
			n++;
			UUID student = jdbc.queryForObject("""
					INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
					VALUES (?, ?, ?, ?, 'Fictif', 'F', 2013) RETURNING id""", UUID.class, world.schoolX, world.classA,
					"SIM-000" + n, name);
			// one guardian each, without the app: everything goes by the SMS digest (D7)
			UUID guardian = jdbc.queryForObject("""
					INSERT INTO guardian (school_id, name, phone, language) VALUES (?, 'Parent Fictif', ?, 'FR')
					RETURNING id""", UUID.class, world.schoolX, "+23769000040" + n);
			jdbc.update("""
					INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
					VALUES (?, ?, ?, 'GUARDIAN', true)""", world.schoolX, student, guardian);
			students.put(name, student);
		}
		students.put("Élève", world.studentA);

		for (LocalDate day : WEEK) {
			absent.put(day, new LinkedHashMap<>());
			late.put(day, new LinkedHashMap<>());
			unrecorded.put(day, Set.of());
			late.get(day).put("Bruno", LocalTime.of(7, 41));
			absent.get(day).put("Dan", Set.of(1, 2, 3));
			if (day.getDayOfWeek() != DayOfWeek.WEDNESDAY) {
				late.get(day).put("Chloé", LocalTime.of(7, 44));
			}
		}
		absent.get(MONDAY).put("Awa", Set.of(1, 2, 3, 4));
		absent.get(MONDAY).put("Gaston", Set.of(2));
		late.get(MONDAY).put("Gaston", LocalTime.of(7, 52));
		LocalDate wednesday = MONDAY.plusDays(2);
		absent.get(wednesday).put("Esther", Set.of(1, 2, 3));
		unrecorded.put(wednesday, Set.of(4));
		absent.get(wednesday.plusDays(1)).put("Esther", Set.of(1, 2, 3, 4));
	}

	@AfterEach
	void removeSchool() {
		clock.reset();
		world.remove(transactions);
	}

	@Test
	void aSchoolWeekRaisesExactlyTheExpectedAlertsAndOneDigestPerStudent() throws Exception {
		for (LocalDate day : WEEK) {
			notifications.reset();

			// the gate, before the first lesson ends (UC-AD1)
			at(day, LocalTime.of(7, 58));
			for (Map.Entry<String, LocalTime> arrival : late.get(day).entrySet()) {
				gate(students.get(arrival.getKey()), day, arrival.getValue());
			}

			// each lesson locked by the teacher when it ends (UC-T1, BR-4)
			for (int lesson = 1; lesson <= 4; lesson++) {
				if (unrecorded.get(day).contains(lesson)) {
					continue;
				}
				at(day, LESSON_ENDS[lesson - 1]);
				int number = lesson;
				Set<UUID> absentees = absent.get(day)
					.entrySet()
					.stream()
					.filter(e -> e.getValue().contains(number))
					.map(e -> students.get(e.getKey()))
					.collect(Collectors.toSet());
				session(day, periods.get(lesson - 1), absentees);
				if (day.equals(MONDAY) && lesson == 3) {
					assertThat(alerts()).as("D2: Awa's day is not full before the last lesson is locked").isEmpty();
				}
			}

			// 13:00: one SMS per student with an absence or a lateness this morning (BR-22, D6)
			at(day, LocalTime.of(13, 0));
			int digests = notifications.runDigest();
			notifications.dispatch();
			List<String> expected = expectedSms(day);
			assertThat(digests).as("digests on %s", day).isEqualTo(expected.size());
			assertThat(notifications.sentSms()).extracting(NotificationDriver.Sms::text)
				.as("SMS on %s", day)
				.containsExactlyInAnyOrderElementsOf(expected);

			// 17:00: nothing new since 13:00, so no second SMS for anyone (D6: never an empty digest)
			at(day, LocalTime.of(17, 0));
			assertThat(notifications.runDigest()).as("17:00 digests on %s", day).isZero();
			notifications.dispatch();
			assertThat(notifications.sentSms()).as("SMS after 17:00 on %s", day).hasSize(expected.size());

			// 20:15: the nightly run agrees with the runs on each commit and adds nothing (BR-17)
			at(day, LocalTime.of(20, 15));
			assertThat(detection.nightly(world.schoolX, day)).as("nightly alerts on %s", day).isZero();
		}

		// the exact alerts of the week: who, which rule, which day
		assertThat(alerts()).containsExactlyInAnyOrder("Awa FULL_DAY_ABSENCE 2026-11-02",
				"Esther FULL_DAY_ABSENCE 2026-11-05", "Dan UNJUSTIFIED_ABSENCES 2026-11-06",
				"Bruno CONSECUTIVE_LATENESS 2026-11-06");

		// rules only raise alerts (BR-16 to BR-19): every one waits for the DM, nothing was decided for them
		assertThat(jdbc.queryForList("SELECT DISTINCT status FROM risk_alert WHERE school_id = ?", String.class,
				world.schoolX)).containsExactly("OPEN");
		for (String table : List.of("summons", "disciplinary_record", "counselling_case")) {
			assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE school_id = ?", Integer.class,
					world.schoolX)).as(table).isZero();
		}

		// over the week, each student has exactly as many digests as days with something to report, and
		// Élève none (with the daily checks above: one digest per student per day, the exit criterion)
		Map<String, Integer> expectedDigests = new TreeMap<>();
		for (LocalDate day : WEEK) {
			Set<String> names = new TreeSet<>(absent.get(day).keySet());
			names.addAll(late.get(day).keySet());
			names.forEach(name -> expectedDigests.merge(name, 1, Integer::sum));
		}
		Map<String, Integer> digests = new TreeMap<>();
		jdbc.query("""
				SELECT s.first_name, count(*) FROM notification_outbox o JOIN student s ON s.id = o.student_id
				WHERE o.school_id = ? AND o.event_type = 'DIGEST' GROUP BY s.first_name""",
				rs -> { digests.put(rs.getString(1), rs.getInt(2)); }, world.schoolX);
		assertThat(digests).isEqualTo(expectedDigests)
			.containsEntry("Bruno", 5)
			.containsEntry("Chloé", 4)
			.doesNotContainKey("Élève");
	}

	/** The digest each student's guardian should get that day: absences and lateness in one SMS. */
	private List<String> expectedSms(LocalDate day) {
		Set<String> names = new TreeSet<>(absent.get(day).keySet());
		names.addAll(late.get(day).keySet());
		List<String> texts = new ArrayList<>();
		for (String name : names) {
			List<String> parts = new ArrayList<>();
			Set<Integer> lessons = absent.get(day).getOrDefault(name, Set.of());
			Set<Integer> recorded = lessons.stream()
				.filter(l -> !unrecorded.get(day).contains(l))
				.collect(Collectors.toCollection(TreeSet::new));
			if (!recorded.isEmpty()) {
				int first = ((TreeSet<Integer>) recorded).first();
				int last = ((TreeSet<Integer>) recorded).last();
				parts.add(first == last ? "absence P" + first : "absence P" + first + "-P" + last);
			}
			LocalTime arrival = late.get(day).get(name);
			if (arrival != null) {
				parts.add("retard (" + arrival + ")");
			}
			texts.add("SchoolLink : " + name + " - " + String.join(" et ", parts) + " le " + day.format(DAY_MONTH)
					+ ". Lycée Fictif X");
		}
		return texts;
	}

	/** "Awa FULL_DAY_ABSENCE 2026-11-02" for each alert raised so far. */
	private List<String> alerts() {
		return jdbc.queryForList("""
				SELECT s.first_name || ' ' || a.rule_type || ' ' || a.as_of
				FROM risk_alert a JOIN student s ON s.id = a.student_id
				WHERE a.school_id = ?""", String.class, world.schoolX);
	}

	private void at(LocalDate day, LocalTime time) {
		clock.set(day.atTime(time).atZone(DOUALA).toInstant());
	}

	private void gate(UUID student, LocalDate day, LocalTime arrival) throws Exception {
		Instant recordedAt = day.atTime(arrival).plusMinutes(1).atZone(DOUALA).toInstant();
		Map<String, Object> body = Map.of("studentId", student, "date", day.toString(), "arrivalTime",
				arrival.toString(), "recordedAt", recordedAt.toString());
		mockMvc.perform(put("/api/v1/lateness/{id}", UUID.randomUUID())
			.header("Authorization", "Bearer " + world.token(ASSISTANT_DM_1))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body))).andExpect(status().isCreated());
		detection.awaitRules(); // the rules run on commit, off the request thread (BR-17)
	}

	private void session(LocalDate day, UUID period, Set<UUID> absentees) throws Exception {
		Map<String, Object> body = Map.of("classId", world.classA, "date", day.toString(), "periodId", period,
				"confirmedAt", clock.instant().toString(), "absentStudentIds", absentees);
		mockMvc.perform(put("/api/v1/sessions/{id}", UUID.randomUUID())
			.header("Authorization", "Bearer " + world.token(TEACHER_A))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body))).andExpect(status().isCreated());
		detection.awaitRules(); // the rules run on commit, off the request thread (BR-17)
	}

	private UUID period(int number, String start, String end) {
		return jdbc.queryForObject("""
				INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, ?, ?::time, ?::time)
				RETURNING id""", UUID.class, world.schoolX, number, start, end);
	}

}
