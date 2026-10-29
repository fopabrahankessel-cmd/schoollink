package com.schoollink.people.internal;

import com.schoollink.audit.AuditAction;
import com.schoollink.audit.AuditEntry;
import com.schoollink.audit.AuditLog;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generated demo data for local development (task 1.14): two fictional schools, one in French and
 * one in English, with their structure, staff, students and guardians. Names come from a faker
 * with a fixed seed; phone numbers use the +237 60x range and emails the reserved {@code .test}
 * domain, so nothing can reach a real person. Never real student data (CLAUDE.md).
 *
 * <p>Runs at startup under the {@code dev} profile only. Like TestWorld in the tests, this fixture
 * writes the school structure rows directly instead of going through the school module.
 */
@Component
@Profile("dev")
class DevSeed implements ApplicationRunner {

	/** Every seeded account signs in with this password. Development only. */
	static final String PASSWORD = "schoollink-dev";

	static final int CLASSES = 6;

	static final int STUDENTS_PER_CLASS = 30;

	static final int TEACHERS = 8;

	static final int PERIODS = 6;

	static final int WEEKDAYS = 5;

	/** Years of schooling of each class above the first year (both schools: two first-year classes). */
	private static final int[] LEVEL = { 0, 0, 1, 2, 3, 4 };

	private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

	/** The two schools; {@code digit} keeps their phone numbers apart. */
	enum Demo {

		FR("Lycée Démo A (fictif)", "FR", "lycee-demo-a.test", "DA", 1, "Bloc", "Trimestre",
				new String[][] { { "6e A", "6e" }, { "6e B", "6e" }, { "5e A", "5e" }, { "4e A", "4e" }, { "3e A", "3e" },
						{ "2nde A", "2nde" } },
				new String[] { "Mathématiques", "Français", "Anglais", "Histoire-Géographie", "SVT", "Physique-Chimie",
						"EPS", "Informatique" },
				new String[] { "admin", "sg1", "sg2", "sga1", "sga2", "conseiller", "prof" },
				new String[] { "Fête de la Jeunesse", "Fête nationale" }),

		EN("Demo High School B (fictional)", "EN", "demo-b.test", "DB", 2, "Block", "Term",
				new String[][] { { "Form 1 A", "Form 1" }, { "Form 1 B", "Form 1" }, { "Form 2 A", "Form 2" },
						{ "Form 3 A", "Form 3" }, { "Form 4 A", "Form 4" }, { "Form 5 A", "Form 5" } },
				new String[] { "Mathematics", "English", "French", "History", "Biology", "Physics", "Chemistry",
						"Computer Science" },
				new String[] { "admin", "dm1", "dm2", "adm1", "adm2", "counsellor", "teacher" },
				new String[] { "Youth Day", "National Day" });

		final String name;

		final String language;

		final String domain;

		final String matriculePrefix;

		final int digit;

		final String blockWord;

		final String termWord;

		final String[][] classes;

		final String[] subjects;

		/** admin, DM 1, DM 2, assistant DM 1, 2, counsellor, teacher prefix. */
		final String[] logins;

		final String[] holidays;

		Demo(String name, String language, String domain, String matriculePrefix, int digit, String blockWord,
				String termWord, String[][] classes, String[] subjects, String[] logins, String[] holidays) {
			this.name = name;
			this.language = language;
			this.domain = domain;
			this.matriculePrefix = matriculePrefix;
			this.digit = digit;
			this.blockWord = blockWord;
			this.termWord = termWord;
			this.classes = classes;
			this.subjects = subjects;
			this.logins = logins;
			this.holidays = holidays;
		}

		String email(String login) {
			return login + "@" + domain;
		}

	}

	/** Signs in at both schools, to show the school picker (D16). */
	static final String SHARED_TEACHER = "enseignant.partage@demo.test";

	static final String RESEARCHER = "chercheur@demo.test";

	private final JdbcTemplate jdbc;

	private final PasswordEncoder passwords;

	private final AuditLog audit;

	DevSeed(JdbcTemplate jdbc, PasswordEncoder passwords, AuditLog audit) {
		this.jdbc = jdbc;
		this.passwords = passwords;
		this.audit = audit;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seed();
	}

	/** Seeds both schools, unless they exist already. Returns whether anything was created. */
	boolean seed() {
		if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM school WHERE name IN (?, ?))",
				Boolean.class, Demo.FR.name, Demo.EN.name))) {
			log.info("Dev seed: the demo schools exist already, nothing to do");
			return false;
		}
		String hash = passwords.encode(PASSWORD);
		for (Demo demo : Demo.values()) {
			school(demo, hash);
		}
		UUID researcher = user(null, "Chercheur", "Démo", null, RESEARCHER, hash, "FR");
		role(null, researcher, "RESEARCHER", null);
		log.info("Dev seed: password for every account below is '{}'", PASSWORD);
		for (Demo demo : Demo.values()) {
			// e.g. "admin, sg1, ..., prof1..prof8 @lycee-demo-a.test"
			log.info("Dev seed: {}: {}, {}1..{}{} @{}", demo.name, String.join(", ", List.of(demo.logins).subList(0, 6)),
					demo.logins[6], demo.logins[6], TEACHERS, demo.domain);
		}
		log.info("Dev seed: {} teaches in both schools; researcher: {}", SHARED_TEACHER, RESEARCHER);
		return true;
	}

	private void school(Demo demo, String hash) {
		Faker faker = new Faker(Locale.forLanguageTag(demo.language.toLowerCase(Locale.ROOT)), new Random(demo.digit));
		UUID school = id("INSERT INTO school (name, language) VALUES (?, ?) RETURNING id", demo.name, demo.language);
		UUID year = id("""
				INSERT INTO academic_year (school_id, label, start_date, end_date)
				VALUES (?, '2026-2027', '2026-09-07', '2027-06-30') RETURNING id""", school);
		LocalDate[][] terms = { { LocalDate.of(2026, 9, 7), LocalDate.of(2026, 12, 18) },
				{ LocalDate.of(2027, 1, 4), LocalDate.of(2027, 3, 26) },
				{ LocalDate.of(2027, 4, 12), LocalDate.of(2027, 6, 30) } };
		LocalDate[] holidays = { LocalDate.of(2027, 2, 11), LocalDate.of(2027, 5, 20) };
		for (int t = 0; t < terms.length; t++) {
			UUID term = id("""
					INSERT INTO term (school_id, academic_year_id, label, start_date, end_date)
					VALUES (?, ?, ?, ?, ?) RETURNING id""", school, year, demo.termWord + " " + (t + 1), terms[t][0],
					terms[t][1]);
			if (t > 0) {
				jdbc.update("""
						INSERT INTO holiday (school_id, term_id, label, start_date, end_date)
						VALUES (?, ?, ?, ?, ?)""", school, term, demo.holidays[t - 1], holidays[t - 1], holidays[t - 1]);
			}
		}
		List<UUID> periods = new ArrayList<>();
		LocalTime start = LocalTime.of(7, 30);
		for (int p = 1; p <= PERIODS; p++) {
			if (p == 4) {
				start = start.plusMinutes(15); // break
			}
			periods.add(id("""
					INSERT INTO period (school_id, number, start_time, end_time)
					VALUES (?, ?, ?, ?) RETURNING id""", school, p, start, start.plusMinutes(55)));
			start = start.plusMinutes(55);
		}
		UUID[] blocks = { id("INSERT INTO block (school_id, name) VALUES (?, ?) RETURNING id", school, demo.blockWord + " A"),
				id("INSERT INTO block (school_id, name) VALUES (?, ?) RETURNING id", school, demo.blockWord + " B") };
		List<UUID> classes = new ArrayList<>();
		for (int c = 0; c < CLASSES; c++) {
			classes.add(id("""
					INSERT INTO school_class (school_id, academic_year_id, block_id, name, level)
					VALUES (?, ?, ?, ?, ?) RETURNING id""", school, year, blocks[c < CLASSES / 2 ? 0 : 1],
					demo.classes[c][0], demo.classes[c][1]));
		}

		staff(demo, faker, school, blocks, classes, periods, hash);
		int[] people = students(demo, faker, school, classes);
		// NFR-11: a system action, counts only
		audit.record(new AuditEntry(school, null, AuditAction.CREATE, "dev_seed", school.toString(), null,
				Map.of("classes", CLASSES, "students", people[0], "guardians", people[1])));
	}

	private void staff(Demo demo, Faker faker, UUID school, UUID[] blocks, List<UUID> classes, List<UUID> periods,
			String hash) {
		int[] seq = { 0 };
		role(school, staffUser(demo, faker, school, demo.logins[0], seq, hash), "ADMIN", null);
		role(school, staffUser(demo, faker, school, demo.logins[1], seq, hash), "DM", blocks[0]);
		role(school, staffUser(demo, faker, school, demo.logins[2], seq, hash), "DM", blocks[1]);
		role(school, staffUser(demo, faker, school, demo.logins[3], seq, hash), "ASSISTANT_DM", blocks[0]);
		role(school, staffUser(demo, faker, school, demo.logins[4], seq, hash), "ASSISTANT_DM", blocks[1]);
		role(school, staffUser(demo, faker, school, demo.logins[5], seq, hash), "COUNSELLOR", null);

		List<UUID> teachers = new ArrayList<>();
		for (int t = 0; t < TEACHERS; t++) {
			UUID teacher = staffUser(demo, faker, school, demo.logins[6] + (t + 1), seq, hash);
			role(school, teacher, "TEACHER", null);
			teachers.add(teacher);
		}
		// Teacher (class + period + weekday) mod 8: every teacher teaches every class, and with
		// fewer classes than teachers no teacher is in two classes at once.
		List<Object[]> teaching = new ArrayList<>();
		List<Object[]> slots = new ArrayList<>();
		for (int c = 0; c < CLASSES; c++) {
			for (int t = 0; t < TEACHERS; t++) {
				teaching.add(new Object[] { school, teachers.get(t), classes.get(c), demo.subjects[t] });
			}
			for (int d = 0; d < WEEKDAYS; d++) {
				for (int p = 0; p < PERIODS; p++) {
					int t = (c + p + d) % TEACHERS;
					slots.add(new Object[] { school, classes.get(c), d + 1, periods.get(p), teachers.get(t),
							demo.subjects[t] });
				}
			}
		}
		UUID shared = user(school, "Enseignant", "Partagé", null, SHARED_TEACHER, hash, demo.language);
		role(school, shared, "TEACHER", null);
		teaching.add(new Object[] { school, shared, classes.getFirst(), demo.subjects[TEACHERS - 1] });
		jdbc.batchUpdate("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, ?)",
				teaching);
		jdbc.batchUpdate("""
				INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject)
				VALUES (?, ?, ?, ?, ?, ?)""", slots);
	}

	private UUID staffUser(Demo demo, Faker faker, UUID school, String login, int[] seq, String hash) {
		return user(school, faker.name().firstName(), faker.name().lastName(), phone(1, demo, ++seq[0]),
				demo.email(login), hash, demo.language);
	}

	/**
	 * Returns {students, guardians}. Every sixth student is the younger sibling of the previous one
	 * and shares their guardian (D15); every third student also has a second guardian.
	 */
	private int[] students(Demo demo, Faker faker, UUID school, List<UUID> classes) {
		List<Object[]> students = new ArrayList<>();
		List<Object[]> guardians = new ArrayList<>();
		List<Object[]> links = new ArrayList<>();
		Map<String, Object> previous = new LinkedHashMap<>();
		String other = demo.language.equals("FR") ? "EN" : "FR";
		for (int i = 0; i < CLASSES * STUDENTS_PER_CLASS; i++) {
			int c = i / STUDENTS_PER_CLASS;
			UUID student = UUID.randomUUID();
			boolean sibling = i % 6 == 5;
			String lastName = sibling ? (String) previous.get("lastName") : faker.name().lastName();
			String sex = faker.random().nextBoolean() ? "M" : "F";
			int birthYear = 2015 - LEVEL[c] - (faker.random().nextBoolean() ? 1 : 0);
			students.add(new Object[] { student, school, classes.get(c), "%s-%04d".formatted(demo.matriculePrefix, i + 1),
					faker.name().firstName(), lastName, sex, birthYear });
			UUID primary;
			String relation = i % 2 == 0 ? "MOTHER" : "FATHER";
			if (sibling) {
				primary = (UUID) previous.get("guardian");
				relation = (String) previous.get("relation");
			}
			else {
				primary = UUID.randomUUID();
				int n = guardians.size() + 1;
				guardians.add(new Object[] { primary, school, faker.name().firstName() + " " + lastName,
						phone(0, demo, n), n % 4 == 0 ? other : demo.language });
			}
			links.add(new Object[] { school, student, primary, relation, true });
			if (i % 3 == 0) {
				UUID second = UUID.randomUUID();
				int n = guardians.size() + 1;
				guardians.add(new Object[] { second, school, faker.name().fullName(), phone(0, demo, n),
						demo.language });
				links.add(new Object[] { school, student, second, relation.equals("MOTHER") ? "FATHER" : "MOTHER", false });
			}
			previous.put("lastName", lastName);
			previous.put("guardian", primary);
			previous.put("relation", relation);
		}
		jdbc.batchUpdate("""
				INSERT INTO student (id, school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", students);
		jdbc.batchUpdate("""
				INSERT INTO guardian (id, school_id, name, phone, language)
				VALUES (?, ?, ?, ?, ?)""", guardians);
		jdbc.batchUpdate("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, ?, ?)""", links);
		return new int[] { students.size(), guardians.size() };
	}

	/** +237 60k s nnnnn: k = 0 guardians, 1 staff; s = the school; n = a sequence number. */
	static String phone(int kind, Demo demo, int n) {
		return "+23760%d%d%05d".formatted(kind, demo.digit, n);
	}

	private UUID user(UUID school, String firstName, String lastName, String phone, String email, String hash,
			String language) {
		return id("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, email, password_hash, language)
				VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", school, firstName, lastName, phone, email, hash, language);
	}

	private void role(UUID school, UUID user, String role, UUID block) {
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role, block_id) VALUES (?, ?, ?, ?)", school, user,
				role, block);
	}

	private UUID id(String sql, Object... args) {
		return jdbc.queryForObject(sql, UUID.class, args);
	}

}
