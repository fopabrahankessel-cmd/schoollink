package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a pilot school and its first administrator from configuration, once (README, Phase 1
 * walkthrough). Set {@code SCHOOLLINK_BOOTSTRAP_SCHOOL_NAME}, {@code ..._SCHOOL_LANGUAGE},
 * {@code ..._ADMIN_EMAIL} and {@code ..._ADMIN_PASSWORD}; nothing happens without a school name, or
 * when a school of that name exists already. The password is temporary: it must be changed at the
 * first sign-in (D18) and is never logged. Everything else is then set up by the admin in the web
 * app. Incomplete settings stop the startup rather than create half a school.
 */
@Component
@EnableConfigurationProperties(SchoolBootstrap.Settings.class)
class SchoolBootstrap implements ApplicationRunner {

	@ConfigurationProperties("schoollink.bootstrap")
	record Settings(String schoolName, String schoolLanguage, String adminEmail, String adminPassword,
			String adminFirstName, String adminLastName) {
	}

	private static final Logger log = LoggerFactory.getLogger(SchoolBootstrap.class);

	private static final Set<String> LANGUAGES = Set.of("FR", "EN");

	private final Settings settings;

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final AuditLog audit;

	SchoolBootstrap(Settings settings, JdbcTemplate jdbc, PasswordEncoder passwords, AuditLog audit) {
		this.settings = settings;
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.audit = audit;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		bootstrap();
	}

	/** Returns the new school's id, or null when there was nothing to do. */
	UUID bootstrap() {
		String name = blankToNull(settings.schoolName());
		if (name == null) {
			return null;
		}
		if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM school WHERE name = ?)", Boolean.class,
				name))) {
			log.info("Bootstrap: the school '{}' exists already, nothing to do", name);
			return null;
		}
		String language = valueOr(settings.schoolLanguage(), "FR").toUpperCase(Locale.ROOT);
		String email = valueOr(settings.adminEmail(), "").toLowerCase(Locale.ROOT);
		String password = settings.adminPassword() == null ? "" : settings.adminPassword();
		require(LANGUAGES.contains(language), "SCHOOLLINK_BOOTSTRAP_SCHOOL_LANGUAGE must be FR or EN");
		require(email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"), "SCHOOLLINK_BOOTSTRAP_ADMIN_EMAIL must be an email address");
		require(password.length() >= 8 && password.length() <= 72,
				"SCHOOLLINK_BOOTSTRAP_ADMIN_PASSWORD must be 8 to 72 characters");

		UUID school = jdbc.queryForObject("INSERT INTO school (name, language) VALUES (?, ?) RETURNING id", UUID.class,
				name, language);
		String firstName = valueOr(settings.adminFirstName(), language.equals("FR") ? "Administrateur" : "Administrator");
		String lastName = valueOr(settings.adminLastName(), name);
		UUID admin = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, email, language, password_hash, password_change_required)
				VALUES (?, ?, ?, ?, ?, ?, true) RETURNING id""", UUID.class, school, firstName, lastName, email, language,
				passwords.encode(password));
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, 'ADMIN')", school, admin);
		// NFR-11: a system action; never the password
		audit.record(new AuditEntry(school, null, AuditAction.CREATE, "school_bootstrap", school.toString(), null,
				Map.of("schoolName", name, "language", language, "adminId", admin, "adminEmail", email)));
		log.info("Bootstrap: created the school '{}' and its administrator {} (password to change at first sign-in)",
				name, email);
		return school;
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			throw new IllegalStateException("School bootstrap: " + message);
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static String valueOr(String value, String fallback) {
		String stripped = blankToNull(value);
		return stripped == null ? fallback : stripped;
	}

}
