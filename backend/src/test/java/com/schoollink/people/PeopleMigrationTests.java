package com.schoollink.people;

import com.schoollink.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway V2 (people) on a real PostgreSQL 16: the constraints behind UC-A2..A4, BR-1, NFR-8,
 * NFR-10 and decisions D5, D7, D15. Fake data only.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PeopleMigrationTests {

	private static final AtomicInteger phones = new AtomicInteger();

	@Autowired
	JdbcTemplate jdbc;

	UUID schoolA;

	UUID yearA;

	UUID blockA;

	UUID classA;

	@BeforeEach
	void createSchoolA() {
		schoolA = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test A') RETURNING id", UUID.class);
		yearA = year(schoolA);
		blockA = block(schoolA, "Bloc 1");
		classA = schoolClass(schoolA, yearA, blockA, "4e A");
	}

	@Test
	void migrationV2IsApplied() {
		assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '2'", Boolean.class))
			.isTrue();
	}

	@Test
	void studentHasNoBirthDateOrPhotoColumn() { // NFR-10
		var columns = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'student'", String.class);
		assertThat(columns).contains("birth_year").doesNotContain("birth_date", "date_of_birth", "photo");
	}

	@Test
	void studentsAndGuardiansAreManyToMany() {
		UUID paul = student(schoolA, classA, "M-001");
		UUID marie = student(schoolA, classA, "M-002");
		UUID mother = guardian(schoolA);
		UUID father = guardian(schoolA);

		link(schoolA, paul, mother, true);
		link(schoolA, paul, father, false);
		link(schoolA, marie, mother, true);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM student_guardian", Integer.class)).isEqualTo(3);
	}

	@Test
	void onePrimaryGuardianPerStudent() {
		UUID paul = student(schoolA, classA, "M-001");
		link(schoolA, paul, guardian(schoolA), true);

		assertRejected(() -> link(schoolA, paul, guardian(schoolA), true));
	}

	@Test
	void matriculeIsUniquePerSchoolOnly() {
		student(schoolA, classA, "M-001");
		assertRejected(() -> student(schoolA, classA, "M-001"));

		UUID schoolB = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test B') RETURNING id", UUID.class);
		UUID classB = schoolClass(schoolB, year(schoolB), block(schoolB, "Bloc B"), "4e B");
		student(schoolB, classB, "M-001");
	}

	@Test
	void studentCannotBeEnrolledInAnotherSchoolsClass() { // NFR-8
		UUID schoolB = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test B') RETURNING id", UUID.class);

		assertRejected(() -> student(schoolB, classA, "M-001"));
	}

	@Test
	void phonesMustBeCameroonE164() {
		assertRejected(() -> guardianWithPhone(schoolA, "699000001"));
		assertRejected(() -> guardianWithPhone(schoolA, "+33612345678"));
		guardianWithPhone(schoolA, "+237699000001");
	}

	@Test
	void phoneAndEmailIdentifyOneUserPerSchool() { // D15
		user(schoolA, "+237699100001", "parent@example.test");

		assertRejected(() -> user(schoolA, "+237699100001", null));
		assertRejected(() -> user(schoolA, null, "parent@example.test"));
	}

	@Test
	void sameParentCanHaveAnAccountInEachSchool() { // D15
		UUID schoolB = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test B') RETURNING id", UUID.class);
		user(schoolA, "+237699100001", "parent@example.test");

		user(schoolB, "+237699100001", "parent@example.test");
	}

	@Test
	void usersWithoutPhoneOrWithoutEmailDoNotClash() {
		user(schoolA, null, "a@example.test");
		user(schoolA, null, "b@example.test");
		user(schoolA, "+237699100001", null);
		user(schoolA, "+237699100002", null);
	}

	@Test
	void emailIsStoredLowercase() {
		assertRejected(() -> user(schoolA, null, "DM@Example.test"));
	}

	@Test
	void oneDmPerBlockButSeveralAssistants() { // D5
		role(schoolA, user(schoolA), "DM", blockA);
		role(schoolA, user(schoolA), "ASSISTANT_DM", blockA);
		role(schoolA, user(schoolA), "ASSISTANT_DM", blockA);

		assertRejected(() -> role(schoolA, user(schoolA), "DM", blockA));
	}

	@Test
	void dmScopeIsABlockAndOtherRolesHaveNone() { // SPEC §3, D5
		assertRejected(() -> role(schoolA, user(schoolA), "DM", null));
		assertRejected(() -> role(schoolA, user(schoolA), "TEACHER", blockA));
	}

	@Test
	void dmCannotBeAssignedAnotherSchoolsBlock() { // NFR-8
		UUID schoolB = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test B') RETURNING id", UUID.class);
		UUID blockB = block(schoolB, "Bloc B");

		assertRejected(() -> role(schoolA, user(schoolA), "DM", blockB));
	}

	@Test
	void onlyTheResearcherHasNoSchool() {
		UUID researcher = user(null);
		role(null, researcher, "RESEARCHER", null);

		assertRejected(() -> role(null, user(null), "ADMIN", null));
		assertRejected(() -> role(schoolA, user(schoolA), "RESEARCHER", null));
	}

	@Test
	void timetableSlotNeedsATeachingAssignment() { // BR-1
		UUID teacher = user(schoolA);
		UUID period = jdbc.queryForObject(
				"INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, 1, '07:30', '08:25') RETURNING id",
				UUID.class, schoolA);
		String insertSlot = "INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject) VALUES (?, ?, 1, ?, ?, 'Physique')";

		assertRejected(() -> jdbc.update(insertSlot, schoolA, classA, period, teacher));

		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, 'Physique')",
				schoolA, teacher, classA);
		jdbc.update(insertSlot, schoolA, classA, period, teacher);
	}

	@Test
	void guardianWithoutAccountIsAllowedAndGetsAnAccountOnActivation() { // D7: no account = SMS, account = push
		UUID guardian = guardian(schoolA);
		assertThat(jdbc.queryForObject("SELECT user_id FROM guardian WHERE id = ?", UUID.class, guardian)).isNull();

		jdbc.update("UPDATE guardian SET user_id = ? WHERE id = ?", user(schoolA), guardian);
	}

	@Test
	void guardianAccountMustBelongToTheSameSchool() { // NFR-8
		UUID schoolB = jdbc.queryForObject("INSERT INTO school (name) VALUES ('Collège Test B') RETURNING id", UUID.class);
		UUID guardian = guardian(schoolA);
		UUID userInB = user(schoolB);

		assertRejected(() -> jdbc.update("UPDATE guardian SET user_id = ? WHERE id = ?", userInB, guardian));
	}

	@Test
	void guardianHasNoSmsOnlyFlag() { // D7: the channel follows from user_id
		var columns = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'guardian'", String.class);
		assertThat(columns).contains("user_id").doesNotContain("sms_only");
	}

	@Test
	void oneLiveInvitationCodePerGuardian() { // UC-A4
		UUID guardian = guardian(schoolA);
		UUID first = code(schoolA, guardian, "hash-1");

		assertRejected(() -> code(schoolA, guardian, "hash-2"));

		jdbc.update("UPDATE invitation_code SET revoked_at = now() WHERE id = ?", first);
		code(schoolA, guardian, "hash-2");
	}

	/**
	 * A rejected statement aborts the PostgreSQL transaction (25P02), so it runs inside a savepoint
	 * and the test can go on after it.
	 */
	private void assertRejected(ThrowingCallable statement) {
		jdbc.execute("SAVEPOINT expected_failure");
		assertThatThrownBy(statement).isInstanceOf(DataIntegrityViolationException.class);
		jdbc.execute("ROLLBACK TO SAVEPOINT expected_failure");
	}

	private UUID year(UUID school) {
		return jdbc.queryForObject(
				"INSERT INTO academic_year (school_id, label, start_date, end_date) VALUES (?, '2026-2027', '2026-09-01', '2027-07-31') RETURNING id",
				UUID.class, school);
	}

	private UUID block(UUID school, String name) {
		return jdbc.queryForObject("INSERT INTO block (school_id, name) VALUES (?, ?) RETURNING id", UUID.class, school,
				name);
	}

	private UUID schoolClass(UUID school, UUID year, UUID block, String name) {
		return jdbc.queryForObject(
				"INSERT INTO school_class (school_id, academic_year_id, block_id, name, level) VALUES (?, ?, ?, ?, '4e') RETURNING id",
				UUID.class, school, year, block, name);
	}

	private UUID student(UUID school, UUID schoolClass, String matricule) {
		return jdbc.queryForObject(
				"INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year) VALUES (?, ?, ?, 'Élève', 'Test', 'M', 2012) RETURNING id",
				UUID.class, school, schoolClass, matricule);
	}

	private UUID guardian(UUID school) {
		return jdbc.queryForObject("INSERT INTO guardian (school_id, name, phone) VALUES (?, 'Parent Test', ?) RETURNING id",
				UUID.class, school, nextPhone());
	}

	private UUID guardianWithPhone(UUID school, String phone) {
		return jdbc.queryForObject("INSERT INTO guardian (school_id, name, phone) VALUES (?, 'Parent Test', ?) RETURNING id",
				UUID.class, school, phone);
	}

	private void link(UUID school, UUID student, UUID guardian, boolean primary) {
		jdbc.update(
				"INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary) VALUES (?, ?, ?, 'GUARDIAN', ?)",
				school, student, guardian, primary);
	}

	private UUID user(UUID school) {
		return user(school, null, "user-" + UUID.randomUUID() + "@example.test");
	}

	private UUID user(UUID school, String phone, String email) {
		return jdbc.queryForObject(
				"INSERT INTO app_user (school_id, first_name, last_name, phone, email) VALUES (?, 'Agent', 'Test', ?, ?) RETURNING id",
				UUID.class, school, phone, email);
	}

	private void role(UUID school, UUID user, String role, UUID block) {
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role, block_id) VALUES (?, ?, ?, ?)", school, user,
				role, block);
	}

	private UUID code(UUID school, UUID guardian, String hash) {
		return jdbc.queryForObject(
				"INSERT INTO invitation_code (school_id, guardian_id, code_hash, expires_at) VALUES (?, ?, ?, now() + interval '30 days') RETURNING id",
				UUID.class, school, guardian, hash);
	}

	private static String nextPhone() {
		return String.format("+2376990%05d", phones.incrementAndGet());
	}

}
