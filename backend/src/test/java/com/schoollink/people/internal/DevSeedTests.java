package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.audit.AuditLog;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Task 1.14: the dev profile seed gives the expected counts and only fake data. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DevSeedTests {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder passwords;

	@Autowired
	AuditLog audit;

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JsonMapper json;

	@Autowired
	ApplicationContext context;

	DevSeed seed;

	@BeforeEach
	void createSeed() {
		seed = new DevSeed(jdbc, passwords, audit);
	}

	@Test
	void runsOnlyUnderTheDevProfile() {
		assertThat(context.getBeanNamesForType(DevSeed.class)).isEmpty();
	}

	@Test
	void seedsTwoSchoolsWithTheExpectedCounts() {
		assertThat(seed.seed()).isTrue();

		for (DevSeed.Demo demo : DevSeed.Demo.values()) {
			UUID school = jdbc.queryForObject("SELECT id FROM school WHERE name = ?", UUID.class, demo.name);
			assertThat(jdbc.queryForObject("SELECT language FROM school WHERE id = ?", String.class, school))
				.isEqualTo(demo.language);
			assertThat(count("academic_year", school)).isOne();
			assertThat(count("term", school)).isEqualTo(3);
			assertThat(count("holiday", school)).isEqualTo(2);
			assertThat(count("period", school)).isEqualTo(6);
			assertThat(count("block", school)).isEqualTo(2);
			assertThat(count("school_class", school)).isEqualTo(6);
			// admin, 2 DMs, 2 assistant DMs, counsellor, 8 teachers, the shared teacher
			assertThat(count("app_user", school)).isEqualTo(15);
			assertThat(count("role_assignment", school)).isEqualTo(15);
			assertThat(count("teaching_assignment", school)).isEqualTo(6 * 8 + 1);
			assertThat(count("timetable_slot", school)).isEqualTo(6 * 5 * 6);
			assertThat(count("student", school)).isEqualTo(180);
			// 180 primary guardians minus 30 shared by siblings, plus 60 second guardians
			assertThat(count("guardian", school)).isEqualTo(210);
			assertThat(count("student_guardian", school)).isEqualTo(240);
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM student s WHERE school_id = ?
					AND (SELECT count(*) FROM student_guardian sg WHERE sg.student_id = s.id AND sg.is_primary) <> 1""",
					Integer.class, school)).as("one primary guardian per student").isZero();
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM guardian g WHERE school_id = ?
					AND (SELECT count(*) FROM student_guardian sg WHERE sg.guardian_id = g.id) = 2""", Integer.class,
					school)).as("guardians of two siblings").isEqualTo(30);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity = 'dev_seed' AND school_id = ?",
					Integer.class, school)).isOne();
		}
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM app_user u JOIN role_assignment r ON r.user_id = u.id
				WHERE u.email = ? AND r.role = 'RESEARCHER' AND u.school_id IS NULL""", Integer.class, DevSeed.RESEARCHER))
			.isOne();
	}

	@Test
	void aSecondRunCreatesNothing() {
		assertThat(seed.seed()).isTrue();
		int students = jdbc.queryForObject("SELECT count(*) FROM student", Integer.class);

		assertThat(seed.seed()).isFalse();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM student", Integer.class)).isEqualTo(students);
	}

	@Test
	void onlyFakePhonesAndEmails() { // CLAUDE.md: no real data
		seed.seed();
		String demoSchools = "(SELECT id FROM school WHERE name IN ('%s', '%s'))".formatted(DevSeed.Demo.FR.name,
				DevSeed.Demo.EN.name);

		assertThat(jdbc.queryForList("SELECT phone FROM guardian WHERE school_id IN " + demoSchools, String.class))
			.hasSize(420)
			.allMatch(p -> p.matches("\\+237600[12]\\d{5}"))
			.doesNotHaveDuplicates();
		assertThat(jdbc.queryForList("SELECT phone FROM app_user WHERE phone IS NOT NULL AND school_id IN " + demoSchools,
				String.class))
			.allMatch(p -> p.matches("\\+237601[12]\\d{5}"));
		assertThat(jdbc.queryForList("""
				SELECT email FROM app_user WHERE school_id IN %s OR email = '%s'""".formatted(demoSchools,
				DevSeed.RESEARCHER), String.class))
			.hasSize(31)
			.allMatch(e -> e.endsWith(".test"));
	}

	@Test
	void seededAccountsSignInWithTheDevPassword() throws Exception {
		seed.seed();
		UUID schoolA = jdbc.queryForObject("SELECT id FROM school WHERE name = ?", UUID.class, DevSeed.Demo.FR.name);
		UUID schoolB = jdbc.queryForObject("SELECT id FROM school WHERE name = ?", UUID.class, DevSeed.Demo.EN.name);

		login(schoolA, "admin@lycee-demo-a.test").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists());
		login(schoolB, "dm1@demo-b.test").andExpect(status().isOk());
		// D16: the shared teacher has one account per school
		login(schoolA, DevSeed.SHARED_TEACHER).andExpect(status().isOk());
		login(schoolB, DevSeed.SHARED_TEACHER).andExpect(status().isOk());
	}

	private ResultActions login(UUID school, String login) throws Exception {
		var body = json.createObjectNode()
			.put("schoolId", school.toString())
			.put("login", login)
			.put("password", DevSeed.PASSWORD);
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body.toString()));
	}

	private int count(String table, UUID school) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE school_id = ?", Integer.class, school);
	}

}
