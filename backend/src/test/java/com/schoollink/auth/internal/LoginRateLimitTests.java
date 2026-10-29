package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.shared.ratelimit.RateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NFR-7: rate limits on login. 5 failures per login, 30 requests per minute per IP. Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginRateLimitTests {

	static final String PHONE = "+237699300001";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder passwords;

	@Autowired
	RateLimiter limiter;

	@Autowired
	JsonMapper json;

	@BeforeEach
	void setUp() {
		limiter.clear();
		UUID school = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test') RETURNING id", UUID.class);
		for (String phone : new String[] { PHONE, "+237699300002" }) {
			UUID user = jdbc.queryForObject("""
					INSERT INTO app_user (school_id, first_name, last_name, phone, password_hash)
					VALUES (?, 'Agent', 'Test', ?, ?) RETURNING id""", UUID.class, school, phone,
					passwords.encode("Right-Pass1"));
			jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'ADMIN')", school, user);
		}
	}

	@Test
	void sixthAttemptAfterFiveFailuresIsRejectedEvenWithTheRightPassword() throws Exception {
		for (int i = 0; i < 5; i++) {
			login(PHONE, "wrong", "10.0.0.1").andExpect(status().isUnauthorized());
		}

		login(PHONE, "Right-Pass1", "10.0.0.1").andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "180"))
			.andExpect(jsonPath("$.code").value("rate_limited"))
			.andExpect(jsonPath("$.message").value("Trop de tentatives. Réessayez dans 3 minutes."));
	}

	@Test
	void theLimitFollowsTheLoginWhateverItsFormatAndWhateverTheIp() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("699 30 00 01", "wrong", "10.0.0." + i).andExpect(status().isUnauthorized());
		}

		login(PHONE, "Right-Pass1", "10.0.0.99").andExpect(status().isTooManyRequests());
	}

	@Test
	void otherLoginsAreNotBlocked() throws Exception {
		for (int i = 0; i < 6; i++) {
			login(PHONE, "wrong", "10.0.0.1");
		}

		login("+237699300002", "Right-Pass1", "10.0.0.1").andExpect(status().isOk());
	}

	@Test
	void successResetsTheFailureCount() throws Exception {
		for (int i = 0; i < 4; i++) {
			login(PHONE, "wrong", "10.0.0.1").andExpect(status().isUnauthorized());
		}
		login(PHONE, "Right-Pass1", "10.0.0.1").andExpect(status().isOk());

		for (int i = 0; i < 5; i++) {
			login(PHONE, "wrong", "10.0.0.1").andExpect(status().isUnauthorized());
		}
	}

	@Test
	void unknownLoginsAreLimitedToo() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("+237699399999", "x", "10.0.0.1").andExpect(status().isUnauthorized());
		}

		login("+237699399999", "x", "10.0.0.1").andExpect(status().isTooManyRequests());
	}

	@Test
	void oneIpGetsABurstOfThirtyThenIsRejected() throws Exception {
		for (int i = 0; i < 30; i++) {
			login(String.format("+2376994%05d", i), "x", "10.9.9.9").andExpect(status().isUnauthorized());
		}

		// The bucket refills by one request every 2 s, so a request or two may still get through
		// while the burst runs; the next ones are rejected.
		int extraAllowed = 0;
		while (login("+237699300002", "Right-Pass1", "10.9.9.9").andReturn().getResponse().getStatus() != 429) {
			assertThat(++extraAllowed).as("requests allowed after the burst of 30").isLessThanOrEqualTo(3);
		}
		login("+237699300002", "Right-Pass1", "10.9.9.9").andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"));
		login("+237699300002", "Right-Pass1", "10.9.9.10").andExpect(status().isOk());
	}

	private ResultActions login(String login, String password, String ip) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.header("Accept-Language", "fr")
			.with(request -> {
				request.setRemoteAddr(ip);
				return request;
			})
			.content(json.createObjectNode().put("login", login).put("password", password).toString()));
	}

}
