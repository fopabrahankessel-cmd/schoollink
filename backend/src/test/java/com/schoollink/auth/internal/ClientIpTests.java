package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.shared.ratelimit.RateLimiter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-7: rate limits key on the real client IP. Through a real HTTP port (Tomcat's RemoteIpValve
 * does not run under MockMvc): X-Forwarded-For counts only when the request comes from a trusted
 * proxy, which by default is loopback only. Fake data only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ClientIpTests {

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder passwords;

	@Autowired
	RateLimiter limiter;

	@Autowired
	Environment environment;

	private final HttpClient http = HttpClient.newHttpClient();

	String phone;

	@BeforeEach
	void createUser() {
		limiter.clear();
		// committed data: the server handles requests on its own threads
		phone = String.format("+2376995%05d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000));
		UUID school = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test IP') RETURNING id", UUID.class);
		UUID user = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, password_hash)
				VALUES (?, 'Agent', 'Test', ?, ?) RETURNING id""", UUID.class, school, phone,
				passwords.encode("Right-Pass1"));
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'ADMIN')", school, user);
	}

	@Test
	void behindATrustedProxyTheForwardedClientIpIsUsed() throws Exception {
		for (int i = 0; i < 5; i++) {
			assertThat(login("wrong", "203.0.113.7")).isEqualTo(401);
		}

		assertThat(login("Right-Pass1", "203.0.113.7")).isEqualTo(429);
		// another client behind the same proxy is a different source
		assertThat(login("Right-Pass1", "198.51.100.4")).isEqualTo(200);
	}

	@Test
	void onlyLoopbackIsTrustedByDefault() {
		assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");
		Pattern trusted = Pattern.compile(environment.getProperty("server.tomcat.remoteip.internal-proxies"));

		assertThat(trusted.matcher("127.0.0.1").matches()).isTrue();
		assertThat(trusted.matcher("0:0:0:0:0:0:0:1").matches()).isTrue();
		assertThat(trusted.matcher("127a0b0c1").matches()).as("dots are literal").isFalse();
		assertThat(trusted.matcher("10.0.0.1").matches()).isFalse();
		assertThat(trusted.matcher("192.168.1.10").matches()).isFalse();
		assertThat(trusted.matcher("203.0.113.7").matches()).isFalse();
	}

	private int login(String password, String forwardedFor) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-Forwarded-For", forwardedFor)
			.POST(HttpRequest.BodyPublishers.ofString(
					"{\"login\":\"" + phone + "\",\"password\":\"" + password + "\"}"))
			.build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}

}
