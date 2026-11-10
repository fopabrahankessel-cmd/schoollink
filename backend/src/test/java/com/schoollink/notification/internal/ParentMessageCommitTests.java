package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.DM_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * D38 with real commits: two sends of the same message that overlap (the app timed out and the DM
 * tapped Send again while the first was still running) send it once, and the second answers 200,
 * not a server error. No test transaction here, so the test removes its own schools afterwards.
 * Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
class ParentMessageCommitTests {

	static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

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

	TestWorld world;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		jdbc.update("UPDATE school SET sms_monthly_cap = 10 WHERE id = ?", world.schoolX);
		UUID guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, user_id, name, phone) VALUES (?, NULL, 'Tuteur Fictif', '+237690000009')
				RETURNING id""", UUID.class, world.schoolX);
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'MOTHER', false)""", world.schoolX, world.studentA, guardian);
	}

	@AfterEach
	void removeWorld() {
		clock.reset();
		world.remove(transactions);
	}

	@Test
	void overlappingSendsOfTheSameIdSendOnce() throws Exception {
		UUID id = UUID.randomUUID();
		String body = json.writeValueAsString(Map.of("id", id, "classId", world.classA,
				"body", "Apportez la tenue de sport demain.", "sms", true, "plainSms", false));
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		try {
			// hold this month's SMS budget (BR-25) so both sends are inside their transaction at once
			Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
				jdbc.queryForObject("SELECT id FROM school WHERE id = ? FOR UPDATE", UUID.class, world.schoolX);
				held.countDown();
				try {
					release.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}));
			assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
			List<Future<Integer>> sends = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				sends.add(pool.submit(() -> mockMvc.perform(post("/api/v1/messages")
					.header("Authorization", "Bearer " + world.token(DM_1))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body)).andReturn().getResponse().getStatus()));
			}
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (waitingOnLocks() < 2 && System.nanoTime() < deadline) {
				Thread.onSpinWait();
			}
			release.countDown();
			holder.get(30, TimeUnit.SECONDS);
			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> send : sends) {
				statuses.add(send.get(30, TimeUnit.SECONDS));
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 200);
		}
		finally {
			release.countDown();
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_message WHERE id = ?", Integer.class, id)).isOne();
	}

	private int waitingOnLocks() {
		return jdbc.queryForObject("""
				SELECT count(*) FROM pg_stat_activity
				WHERE datname = current_database() AND wait_event_type = 'Lock'""", Integer.class);
	}

}
