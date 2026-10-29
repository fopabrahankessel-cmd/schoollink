package com.schoollink.attendance;

import com.schoollink.TestcontainersConfiguration;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
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
 * Flyway V11 (attendance, lateness, justification) on a real PostgreSQL 16: the constraints behind
 * BR-1 to BR-15, NFR-8 and decision D3. Fake data only.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceMigrationTests {

	/** D3: a Monday; the Saturday after it is not a school day. */
	static final String MONDAY = "2026-11-02";

	static final String SATURDAY = "2026-11-07";

	@Autowired
	JdbcTemplate jdbc;

	UUID school;

	UUID schoolClass;

	UUID period;

	UUID teacher;

	UUID dm;

	UUID paul;

	UUID marie;

	@BeforeEach
	void createSchool() {
		school = school("Collège Test A");
		schoolClass = schoolClass(school, "4e A");
		period = period(school, 1);
		teacher = user(school);
		dm = user(school);
		paul = student(school, schoolClass, "M-001");
		marie = student(school, schoolClass, "M-002");
	}

	@Test
	void migrationV11IsApplied() {
		assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '11'", Boolean.class))
			.isTrue();
	}

	@Test
	void theSessionIdComesFromTheDevice() { // BR-6: no server default, PUT /sessions/{uuid} is idempotent on it
		assertRejected(() -> jdbc.update("""
				INSERT INTO attendance_session (school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?::date, ?, ?, now())""", school, schoolClass, MONDAY, period, teacher));

		UUID id = UUID.randomUUID();
		session(id, school, schoolClass, MONDAY, period);
		assertThat(jdbc.queryForObject("SELECT synced_at IS NOT NULL FROM attendance_session WHERE id = ?", Boolean.class,
				id))
			.isTrue();
	}

	@Test
	void oneSessionPerClassDateAndPeriod() { // BR-1
		session(UUID.randomUUID(), school, schoolClass, MONDAY, period);

		assertRejected(() -> session(UUID.randomUUID(), school, schoolClass, MONDAY, period));

		session(UUID.randomUUID(), school, schoolClass, MONDAY, period(school, 2));
		session(UUID.randomUUID(), school, schoolClass, "2026-11-03", period);
	}

	@Test
	void sessionsAndLatenessAreOnSchoolDaysOnly() { // D3
		assertRejected(() -> session(UUID.randomUUID(), school, schoolClass, SATURDAY, period));
		assertRejected(() -> lateness(UUID.randomUUID(), school, paul, SATURDAY));
	}

	@Test
	void aSessionCannotUseAnotherSchoolsClassPeriodOrTeacher() { // NFR-8
		UUID schoolB = school("Collège Test B");
		UUID classB = schoolClass(schoolB, "4e B");

		assertRejected(() -> session(UUID.randomUUID(), school, classB, MONDAY, period));
		assertRejected(() -> session(UUID.randomUUID(), schoolB, classB, MONDAY, period));
		assertRejected(() -> jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?::date, ?, ?, now())""", UUID.randomUUID(), schoolB, classB, MONDAY, period(schoolB, 1),
				teacher));
	}

	@Test
	void aSessionWithoutAbsenteesIsStored() { // BR-2, BR-5
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);
		record(session, paul, "PRESENT");
		record(session, marie, "PRESENT");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE session_id = ? AND status = 'PRESENT'",
				Integer.class, session))
			.isEqualTo(2);
	}

	@Test
	void oneRecordPerStudentPerSession() {
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);
		record(session, paul, "ABSENT");

		assertRejected(() -> record(session, paul, "PRESENT"));
		assertRejected(() -> record(session, marie, "LATE"));
	}

	@Test
	void aRecordCannotNameAnotherSchoolsStudent() { // NFR-8
		UUID schoolB = school("Collège Test B");
		UUID studentB = student(schoolB, schoolClass(schoolB, "4e B"), "M-001");
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);

		assertRejected(() -> record(session, studentB, "ABSENT"));
	}

	@Test
	void aLockedSessionNeverChanges() { // BR-4
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);

		assertRejected(() -> jdbc.update("UPDATE attendance_session SET confirmed_at = now() WHERE id = ?", session));
		assertRejected(() -> jdbc.update("UPDATE attendance_session SET teacher_id = ? WHERE id = ?", dm, session));
		assertRejected(() -> jdbc.update("DELETE FROM attendance_session WHERE id = ?", session));
		assertRejected(() -> jdbc.execute("TRUNCATE attendance_session CASCADE"));
	}

	@Test
	void aRecordedStatusNeverChanges() { // BR-4
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);
		UUID absence = record(session, paul, "ABSENT");

		assertRejected(() -> jdbc.update("UPDATE attendance_record SET status = 'PRESENT' WHERE id = ?", absence));
		assertRejected(() -> jdbc.update("UPDATE attendance_record SET student_id = ? WHERE id = ?", marie, absence));
		assertRejected(() -> jdbc.update("DELETE FROM attendance_record WHERE id = ?", absence));
	}

	@Test
	void anAbsenceIsJustifiedLater() { // BR-10, BR-11
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);
		UUID absence = record(session, paul, "ABSENT");
		UUID present = record(session, marie, "PRESENT");
		assertThat(jdbc.queryForObject("SELECT justification_id FROM attendance_record WHERE id = ?", UUID.class, absence))
			.isNull();

		UUID justification = justification(school, "ILLNESS");
		jdbc.update("UPDATE attendance_record SET justification_id = ? WHERE id = ?", justification, absence);

		assertRejected(() -> jdbc.update("UPDATE attendance_record SET justification_id = ? WHERE id = ?", justification,
				present));
	}

	@Test
	void oneJustificationCoversAbsencesAndLateness() { // BR-12
		UUID justification = justification(school, "FAMILY");
		UUID monday = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);
		UUID tuesday = session(UUID.randomUUID(), school, schoolClass, "2026-11-03", period);
		UUID late = lateness(UUID.randomUUID(), school, paul, "2026-11-04");

		jdbc.update("UPDATE attendance_record SET justification_id = ? WHERE id IN (?, ?)", justification,
				record(monday, paul, "ABSENT"), record(tuesday, paul, "ABSENT"));
		jdbc.update("UPDATE lateness_record SET justification_id = ? WHERE id = ?", justification, late);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_record WHERE justification_id = ?", Integer.class,
				justification))
			.isEqualTo(2);
	}

	@Test
	void justificationReasonsAreFixed() { // BR-11, NFR-10: "illness", no health detail
		for (String reason : new String[] { "ILLNESS", "FAMILY", "SCHOOL_ACTIVITY", "OTHER" }) {
			justification(school, reason);
		}
		assertRejected(() -> justification(school, "MALARIA"));
	}

	@Test
	void anotherSchoolsJustificationCannotBeUsed() { // NFR-8
		UUID schoolB = school("Collège Test B");
		UUID justificationB = jdbc.queryForObject(
				"INSERT INTO justification (school_id, reason, recorded_by) VALUES (?, 'OTHER', ?) RETURNING id", UUID.class,
				schoolB, user(schoolB));
		UUID absence = record(session(UUID.randomUUID(), school, schoolClass, MONDAY, period), paul, "ABSENT");

		assertRejected(() -> jdbc.update("UPDATE attendance_record SET justification_id = ? WHERE id = ?", justificationB,
				absence));
	}

	@Test
	void onlyAnAbsenceIsCancelledAlwaysWithAReason() { // BR-13
		UUID session = session(UUID.randomUUID(), school, schoolClass, MONDAY, period);
		UUID absence = record(session, paul, "ABSENT");
		UUID present = record(session, marie, "PRESENT");

		assertRejected(() -> cancel("attendance_record", absence, null));
		assertRejected(() -> cancel("attendance_record", absence, "  "));
		assertRejected(() -> jdbc.update("UPDATE attendance_record SET cancelled_at = now() WHERE id = ?", absence));
		assertRejected(() -> cancel("attendance_record", present, "Erreur de saisie"));

		cancel("attendance_record", absence, "Erreur de saisie");
	}

	@Test
	void aCancellationStaysAndIsNeverUndone() { // BR-13, BR-15
		UUID absence = record(session(UUID.randomUUID(), school, schoolClass, MONDAY, period), paul, "ABSENT");
		cancel("attendance_record", absence, "Erreur de saisie");

		assertRejected(() -> jdbc.update(
				"UPDATE attendance_record SET cancelled_at = NULL, cancelled_by = NULL, cancel_reason = NULL WHERE id = ?",
				absence));
		assertRejected(() -> cancel("attendance_record", absence, "Autre motif"));
		assertRejected(() -> jdbc.update("DELETE FROM attendance_record WHERE id = ?", absence));
		// still justifiable afterwards: the guard only protects what was recorded
		jdbc.update("UPDATE attendance_record SET justification_id = ? WHERE id = ?", justification(school, "OTHER"),
				absence);
	}

	@Test
	void oneLiveLatenessPerStudentPerDay() { // BR-7, BR-15
		UUID first = lateness(UUID.randomUUID(), school, paul, MONDAY);
		lateness(UUID.randomUUID(), school, marie, MONDAY);
		lateness(UUID.randomUUID(), school, paul, "2026-11-03");

		assertRejected(() -> lateness(UUID.randomUUID(), school, paul, MONDAY));

		cancel("lateness_record", first, "Mauvais élève");
		lateness(UUID.randomUUID(), school, paul, MONDAY);
	}

	@Test
	void recordedLatenessNeverChanges() { // BR-4 applied to the gate record
		UUID late = lateness(UUID.randomUUID(), school, paul, MONDAY);

		assertRejected(() -> jdbc.update("UPDATE lateness_record SET arrival_time = '07:35' WHERE id = ?", late));
		assertRejected(() -> jdbc.update("DELETE FROM lateness_record WHERE id = ?", late));
		assertRejected(() -> cancel("lateness_record", late, ""));
		assertRejected(() -> jdbc.execute("TRUNCATE lateness_record"));
	}

	@Test
	void latenessCannotNameAnotherSchoolsStudent() { // NFR-8
		UUID schoolB = school("Collège Test B");
		UUID studentB = student(schoolB, schoolClass(schoolB, "4e B"), "M-001");

		assertRejected(() -> lateness(UUID.randomUUID(), school, studentB, MONDAY));
	}

	@Test
	void attendanceAndLatenessAreNotLinked() { // BR-8, BR-9: no reconciliation, no shared column
		var record = columns("attendance_record");
		var lateness = columns("lateness_record");

		assertThat(record).noneMatch(column -> column.contains("lateness"));
		assertThat(lateness).noneMatch(column -> column.contains("attendance") || column.contains("session"));
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

	private List<String> columns(String table) {
		return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = ?", String.class,
				table);
	}

	private UUID school(String name) {
		return jdbc.queryForObject("INSERT INTO school (name) VALUES (?) RETURNING id", UUID.class, name);
	}

	private UUID schoolClass(UUID school, String name) {
		UUID year = jdbc.queryForObject(
				"INSERT INTO academic_year (school_id, label, start_date, end_date) VALUES (?, '2026-2027', '2026-09-01', '2027-07-31') RETURNING id",
				UUID.class, school);
		UUID block = jdbc.queryForObject("INSERT INTO block (school_id, name) VALUES (?, 'Bloc 1') RETURNING id",
				UUID.class, school);
		return jdbc.queryForObject(
				"INSERT INTO school_class (school_id, academic_year_id, block_id, name, level) VALUES (?, ?, ?, ?, '4e') RETURNING id",
				UUID.class, school, year, block, name);
	}

	private UUID period(UUID school, int number) {
		LocalTime start = LocalTime.of(7, 30).plusMinutes(55L * (number - 1));
		return jdbc.queryForObject(
				"INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, ?, ?, ?) RETURNING id", UUID.class,
				school, number, start, start.plusMinutes(55));
	}

	private UUID user(UUID school) {
		return jdbc.queryForObject(
				"INSERT INTO app_user (school_id, first_name, last_name, email) VALUES (?, 'Agent', 'Test', ?) RETURNING id",
				UUID.class, school, "user-" + UUID.randomUUID() + "@example.test");
	}

	private UUID student(UUID school, UUID schoolClass, String matricule) {
		return jdbc.queryForObject(
				"INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year) VALUES (?, ?, ?, 'Élève', 'Test', 'M', 2012) RETURNING id",
				UUID.class, school, schoolClass, matricule);
	}

	private UUID session(UUID id, UUID school, UUID schoolClass, String date, UUID period) {
		jdbc.update("""
				INSERT INTO attendance_session (id, school_id, class_id, date, period_id, teacher_id, confirmed_at)
				VALUES (?, ?, ?, ?::date, ?, ?, now())""", id, school, schoolClass, date, period, teacher);
		return id;
	}

	private UUID record(UUID session, UUID student, String status) {
		return jdbc.queryForObject(
				"INSERT INTO attendance_record (school_id, session_id, student_id, status) VALUES (?, ?, ?, ?) RETURNING id",
				UUID.class, school, session, student, status);
	}

	private UUID lateness(UUID id, UUID school, UUID student, String date) {
		jdbc.update("""
				INSERT INTO lateness_record (id, school_id, student_id, date, arrival_time, recorded_by, recorded_at)
				VALUES (?, ?, ?, ?::date, '07:48', ?, now())""", id, school, student, date, dm);
		return id;
	}

	private UUID justification(UUID school, String reason) {
		return jdbc.queryForObject(
				"INSERT INTO justification (school_id, reason, note, recorded_by) VALUES (?, ?, 'Mot des parents', ?) RETURNING id",
				UUID.class, school, reason, dm);
	}

	private void cancel(String table, UUID id, String reason) {
		jdbc.update("UPDATE " + table + " SET cancelled_at = now(), cancelled_by = ?, cancel_reason = ? WHERE id = ?", dm,
				reason, id);
	}

}
