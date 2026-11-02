package com.schoollink.detection.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.attendance.AttendanceConfirmed;
import com.schoollink.detection.RuleType;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-17 with real commits: the rules run once a session or a lateness is committed, and two runs at
 * the same moment raise a single open alert. No test transaction here, so each test removes its
 * own schools afterwards. Monday 2 November 2026. Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
class DetectionCommitTests {

	static final Instant NOW = Instant.parse("2026-11-02T07:40:00Z");

	static final LocalDate MONDAY = LocalDate.parse("2026-11-02");

	@Autowired
	Detection detection;

	@Autowired
	DetectionListeners listeners;

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
	ApplicationEventPublisher events;

	@Autowired
	PlatformTransactionManager transactions;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
	}

	@AfterEach
	void removeWorld() {
		clock.reset();
		world.remove(transactions);
	}

	@Test
	void aLatenessCommittedAtTheGateRunsTheRule() throws Exception { // BR-17, D3
		jdbc.update("UPDATE threshold_rule SET count = 2 WHERE school_id = ? AND type = 'CONSECUTIVE_LATENESS'",
				world.schoolX);

		gate("2026-10-30", "2026-10-30T06:50:00Z"); // Friday
		assertThat(listeners.awaitIdle(Duration.ofSeconds(30))).isTrue();
		assertThat(openAlerts()).isZero();
		gate("2026-11-02", "2026-11-02T06:48:00Z"); // Monday
		assertThat(listeners.awaitIdle(Duration.ofSeconds(30))).isTrue();

		assertThat(jdbc.queryForList("SELECT rule_type FROM risk_alert WHERE school_id = ? AND status = 'OPEN'",
				String.class, world.schoolX)).containsExactly("CONSECUTIVE_LATENESS");
	}

	@Test
	void aLockedSessionRunsTheRulesOnceCommitted() throws Exception { // BR-17, D2
		UUID session = UUID.randomUUID();
		UUID record = UUID.randomUUID();
		new TransactionTemplate(transactions).executeWithoutResult(status -> {
			// the class has one period on Mondays: missing it is a full day
			jdbc.update("""
					INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)""", session, world.schoolX, world.classA, MONDAY, world.period1,
					world.user(TEACHER_A), Timestamp.from(NOW));
			jdbc.update("""
					INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
					VALUES (?, ?, ?, ?, 'ABSENT')""", record, world.schoolX, session, world.studentA);
			events.publishEvent(new AttendanceConfirmed(session, world.schoolX, world.classA, MONDAY, world.period1,
					world.user(TEACHER_A), NOW, List.of(new AttendanceConfirmed.Absence(record, world.studentA))));
			// not before the commit: the session could still be rolled back
			assertThat(openAlerts()).isZero();
		});
		assertThat(listeners.awaitIdle(Duration.ofSeconds(30))).isTrue(); // on the detection executor

		assertThat(jdbc.queryForList("SELECT rule_type FROM risk_alert WHERE school_id = ? AND status = 'OPEN'",
				String.class, world.schoolX)).containsExactly("FULL_DAY_ABSENCE");
	}

	@Test
	void runsAtTheSameMomentRaiseOneOpenAlert() throws Exception { // BR-17: the partial unique index
		UUID session = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)""", session, world.schoolX, world.classA, MONDAY, world.period1,
				world.user(TEACHER_A), Timestamp.from(NOW));
		jdbc.update("""
				INSERT INTO attendance_record (id, school_id, session_id, student_id, status)
				VALUES (?, ?, ?, ?, 'ABSENT')""", UUID.randomUUID(), world.schoolX, session, world.studentA);

		int runs = 8;
		ExecutorService pool = Executors.newFixedThreadPool(runs);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < runs; i++) {
				Callable<Integer> run = () -> {
					start.await();
					return detection.evaluate(world.schoolX, world.studentA, MONDAY, EnumSet.allOf(RuleType.class));
				};
				results.add(pool.submit(run));
			}
			start.countDown();
			int raised = 0;
			for (Future<Integer> result : results) {
				raised += result.get(30, TimeUnit.SECONDS);
			}
			assertThat(raised).isOne();
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(openAlerts()).isOne();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE school_id = ? AND entity = 'risk_alert'",
				Integer.class, world.schoolX)).isOne();
	}

	private int openAlerts() {
		return jdbc.queryForObject("SELECT count(*) FROM risk_alert WHERE school_id = ? AND status = 'OPEN'",
				Integer.class, world.schoolX);
	}

	private void gate(String date, String recordedAt) throws Exception {
		Map<String, Object> body = Map.of("studentId", world.studentA, "date", date, "arrivalTime", "07:50",
				"recordedAt", recordedAt);
		mockMvc.perform(put("/api/v1/lateness/{id}", UUID.randomUUID())
			.header("Authorization", "Bearer " + world.token(ASSISTANT_DM_1))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json.writeValueAsString(body))).andExpect(status().isCreated());
	}

}
