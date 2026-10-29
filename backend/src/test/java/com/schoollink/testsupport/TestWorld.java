package com.schoollink.testsupport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Two fake schools with one user per {@link Actor}, for scope and permission tests (NFR-8).
 * Generated data only.
 *
 * <pre>
 * School X: block 1 -> class A (student SA, guardian = PARENT_A), block 2 -> class B
 *           ADMIN_X, TEACHER_A (teaches A), ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, PARENT_A
 * School Y: class C, ADMIN_Y
 * RESEARCHER: no school
 * </pre>
 *
 * Create it inside a {@code @Transactional} test so that everything is rolled back.
 */
public final class TestWorld {

	public final UUID schoolX;

	public final UUID schoolY;

	public final UUID block1;

	public final UUID block2;

	public final UUID classA;

	public final UUID classB;

	public final UUID classC;

	public final UUID studentA;

	private final Map<Actor, UUID> users = new EnumMap<>(Actor.class);

	private final JdbcTemplate jdbc;

	private final JwtEncoder encoder;

	private TestWorld(JdbcTemplate jdbc, JwtEncoder encoder) {
		this.jdbc = jdbc;
		this.encoder = encoder;
		schoolX = id("INSERT INTO school (name) VALUES ('Lycée Fictif X') RETURNING id");
		schoolY = id("INSERT INTO school (name, language) VALUES ('Fictional High School Y', 'EN') RETURNING id");
		UUID yearX = year(schoolX);
		UUID yearY = year(schoolY);
		block1 = id("INSERT INTO block (school_id, name) VALUES (?, 'Bloc 1') RETURNING id", schoolX);
		block2 = id("INSERT INTO block (school_id, name) VALUES (?, 'Bloc 2') RETURNING id", schoolX);
		UUID blockY = id("INSERT INTO block (school_id, name) VALUES (?, 'Block Y') RETURNING id", schoolY);
		classA = schoolClass(schoolX, yearX, block1, "6e A", "6e");
		classB = schoolClass(schoolX, yearX, block2, "5e B", "5e");
		classC = schoolClass(schoolY, yearY, blockY, "Form 1 C", "Form 1");

		user(Actor.ADMIN_X, schoolX, null);
		user(Actor.TEACHER_A, schoolX, null);
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Maths')",
				schoolX, users.get(Actor.TEACHER_A), classA);
		user(Actor.ASSISTANT_DM_1, schoolX, block1);
		user(Actor.DM_1, schoolX, block1);
		user(Actor.DM_2, schoolX, block2);
		user(Actor.COUNSELLOR_X, schoolX, null);
		user(Actor.PARENT_A, schoolX, null);
		user(Actor.RESEARCHER, null, null);
		user(Actor.ADMIN_Y, schoolY, null);

		studentA = id("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0001', 'Élève', 'Fictif', 'F', 2012) RETURNING id""", schoolX, classA);
		UUID guardian = id("""
				INSERT INTO guardian (school_id, user_id, name, phone)
				VALUES (?, ?, 'Parent Fictif', '+237690000001') RETURNING id""", schoolX, users.get(Actor.PARENT_A));
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'MOTHER', true)""", schoolX, studentA, guardian);
	}

	public static TestWorld create(JdbcTemplate jdbc, JwtEncoder encoder) {
		return new TestWorld(jdbc, encoder);
	}

	public UUID user(Actor actor) {
		return users.get(actor);
	}

	/**
	 * A valid access token for the actor, shaped like the ones login issues; {@code null} for
	 * {@link Actor#ANONYMOUS}.
	 */
	public String token(Actor actor) {
		if (actor == Actor.ANONYMOUS) {
			return null;
		}
		UUID id = users.get(actor);
		UUID school = jdbc.queryForObject("SELECT school_id FROM app_user WHERE id = ?", UUID.class, id);
		Instant now = Instant.now();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("schoollink")
			.subject(id.toString())
			.issuedAt(now)
			.expiresAt(now.plus(15, ChronoUnit.MINUTES))
			.claim("roles", List.of(actor.role.name()))
			.claim("lang", "FR");
		if (school != null) {
			claims.claim("school", school.toString());
		}
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
			.getTokenValue();
	}

	private void user(Actor actor, UUID school, UUID block) {
		String email = actor.name().toLowerCase().replace('_', '-') + "-" + UUID.randomUUID() + "@example.test";
		UUID id = id("""
				INSERT INTO app_user (school_id, first_name, last_name, email, password_hash)
				VALUES (?, 'Test', ?, ?, 'not-a-real-hash') RETURNING id""", school, actor.name(), email);
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role, block_id) VALUES (?, ?, ?, ?)", school, id,
				actor.role.name(), block);
		users.put(actor, id);
	}

	private UUID year(UUID school) {
		return id("""
				INSERT INTO academic_year (school_id, label, start_date, end_date)
				VALUES (?, '2026-2027', '2026-09-07', '2027-06-30') RETURNING id""", school);
	}

	private UUID schoolClass(UUID school, UUID year, UUID block, String name, String level) {
		return id("""
				INSERT INTO school_class (school_id, academic_year_id, block_id, name, level)
				VALUES (?, ?, ?, ?, ?) RETURNING id""", school, year, block, name, level);
	}

	private UUID id(String sql, Object... args) {
		return jdbc.queryForObject(sql, UUID.class, args);
	}

}
