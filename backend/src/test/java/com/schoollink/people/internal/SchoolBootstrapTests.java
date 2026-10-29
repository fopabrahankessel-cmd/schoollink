package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.audit.AuditLog;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pilot school bootstrap from configuration (task 1.20, D18, NFR-11). Generated data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SchoolBootstrapTests {

	static final String NAME = "Lycée Bootstrap (fictif)";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder passwords;

	@Autowired
	AuditLog audit;

	SchoolBootstrap bootstrap(String name, String language, String email, String password) {
		return new SchoolBootstrap(new SchoolBootstrap.Settings(name, language, email, password, null, null), jdbc,
				passwords, audit);
	}

	@Test
	void doesNothingWithoutASchoolName() {
		assertThat(bootstrap(" ", "FR", "admin@bootstrap.test", "Temp-Pass-1").bootstrap()).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = 'admin@bootstrap.test'", Integer.class))
			.isZero();
	}

	@Test
	void createsTheSchoolAndAnAdminWhoMustChangeThePasswordOnce() throws Exception {
		UUID school = bootstrap(NAME, "en", "Admin@Bootstrap.TEST", "Temp-Pass-1").bootstrap();

		assertThat(school).isNotNull();
		assertThat(jdbc.queryForObject("SELECT language FROM school WHERE id = ?", String.class, school)).isEqualTo("EN");
		var admin = jdbc.queryForMap("""
				SELECT u.first_name, u.password_change_required, u.password_hash, r.role FROM app_user u
				JOIN role_assignment r ON r.user_id = u.id WHERE u.school_id = ? AND u.email = 'admin@bootstrap.test'""",
				school);
		assertThat(admin.get("role")).isEqualTo("ADMIN");
		assertThat(admin.get("first_name")).isEqualTo("Administrator");
		assertThat(admin.get("password_change_required")).isEqualTo(true); // D18
		assertThat((String) admin.get("password_hash")).doesNotContain("Temp-Pass-1");
		// NFR-11: audited as a system action, without the password
		String after = jdbc.queryForObject("SELECT after::text FROM audit_log WHERE entity = 'school_bootstrap' AND entity_id = ?",
				String.class, school.toString());
		assertThat(after).contains("admin@bootstrap.test").doesNotContain("Temp-Pass-1");

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{\"login\":\"admin@bootstrap.test\",\"password\":\"Temp-Pass-1\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
			.andExpect(jsonPath("$.user.passwordChangeRequired").value(true));

		// a restart with the same settings changes nothing
		assertThat(bootstrap(NAME, "EN", "other@bootstrap.test", "Other-Pass-2").bootstrap()).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM school WHERE name = ?", Integer.class, NAME)).isOne();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE school_id = ?", Integer.class, school)).isOne();
	}

	@Test
	void incompleteSettingsStopTheStartupWithoutCreatingAnything() {
		assertThatThrownBy(() -> bootstrap(NAME, "FR", "admin@bootstrap.test", "short").bootstrap())
			.hasMessageContaining("SCHOOLLINK_BOOTSTRAP_ADMIN_PASSWORD");
		assertThatThrownBy(() -> bootstrap(NAME, "FR", null, "Temp-Pass-1").bootstrap())
			.hasMessageContaining("SCHOOLLINK_BOOTSTRAP_ADMIN_EMAIL");
		assertThatThrownBy(() -> bootstrap(NAME, "DE", "admin@bootstrap.test", "Temp-Pass-1").bootstrap())
			.hasMessageContaining("SCHOOLLINK_BOOTSTRAP_SCHOOL_LANGUAGE");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM school WHERE name = ?", Integer.class, NAME)).isZero();
	}

}
