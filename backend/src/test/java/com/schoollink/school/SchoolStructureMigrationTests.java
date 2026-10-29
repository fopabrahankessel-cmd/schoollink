package com.schoollink.school;

import com.schoollink.TestcontainersConfiguration;
import java.util.UUID;
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
 * Flyway V1 (school structure) on a real PostgreSQL 16: the migration applies, and the
 * constraints behind UC-A1, NFR-8 and decisions D1, D3, D5 hold. Fake data only.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchoolStructureMigrationTests {

	@Autowired
	JdbcTemplate jdbc;

	UUID schoolA;

	UUID yearA;

	UUID blockA;

	UUID classA;

	UUID periodA1;

	@BeforeEach
	void createSchoolA() {
		schoolA = school("Collège Test A");
		yearA = year(schoolA, "2026-2027");
		blockA = block(schoolA, "Bloc 1");
		classA = schoolClass(schoolA, yearA, blockA, "4e A");
		periodA1 = period(schoolA, 1, "07:30", "08:25");
	}

	@Test
	void migrationV1IsApplied() {
		Boolean success = jdbc.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
		assertThat(success).isTrue();
	}

	@Test
	void schoolDefaultsToFrenchAndStartsAt0730() {
		var row = jdbc.queryForMap("SELECT language, start_time::text AS start, timezone FROM school WHERE id = ?",
				schoolA);
		assertThat(row).containsEntry("language", "FR")
			.containsEntry("start", "07:30:00")
			.containsEntry("timezone", "Africa/Douala");
	}

	@Test
	void fullStructureCanBeCreated() {
		UUID term = term(schoolA, yearA, "Trimestre 1", "2026-09-07", "2026-12-18");
		jdbc.update("INSERT INTO holiday (school_id, term_id, label, start_date, end_date) VALUES (?, ?, ?, ?::date, ?::date)",
				schoolA, term, "Fête de test", "2026-11-11", "2026-11-11");
		slot(schoolA, classA, 1, periodA1);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM timetable_slot", Integer.class)).isEqualTo(1);
	}

	@Test
	void classCannotReferenceAnotherSchoolsBlock() { // NFR-8
		UUID schoolB = school("Collège Test B");
		UUID blockB = block(schoolB, "Bloc B");

		assertThatThrownBy(() -> schoolClass(schoolA, yearA, blockB, "4e B"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void timetableSlotCannotUseAnotherSchoolsPeriod() { // NFR-8
		UUID schoolB = school("Collège Test B");
		UUID periodB1 = period(schoolB, 1, "07:30", "08:25");

		assertThatThrownBy(() -> slot(schoolA, classA, 1, periodB1))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void termsOfOneSchoolCannotOverlap() { // BR-16: "current term" must be unambiguous
		term(schoolA, yearA, "Trimestre 1", "2026-09-07", "2026-12-18");

		assertThatThrownBy(() -> term(schoolA, yearA, "Trimestre 2", "2026-12-18", "2027-03-26"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void termsOfDifferentSchoolsMayOverlap() {
		UUID schoolB = school("Collège Test B");
		UUID yearB = year(schoolB, "2026-2027");
		term(schoolA, yearA, "Trimestre 1", "2026-09-07", "2026-12-18");

		term(schoolB, yearB, "Trimestre 1", "2026-09-07", "2026-12-18");
	}

	@Test
	void periodNumberIsUniquePerSchool() {
		assertThatThrownBy(() -> period(schoolA, 1, "08:25", "09:20"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void periodMustEndAfterItStarts() {
		assertThatThrownBy(() -> period(schoolA, 2, "09:20", "08:25"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void timetableHasNoSaturday() { // D3: school days are Monday to Friday
		assertThatThrownBy(() -> slot(schoolA, classA, 6, periodA1))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void oneTimetableSlotPerClassWeekdayAndPeriod() { // D1
		slot(schoolA, classA, 1, periodA1);

		assertThatThrownBy(() -> slot(schoolA, classA, 1, periodA1))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID school(String name) {
		return jdbc.queryForObject("INSERT INTO school (name) VALUES (?) RETURNING id", UUID.class, name);
	}

	private UUID year(UUID school, String label) {
		return jdbc.queryForObject(
				"INSERT INTO academic_year (school_id, label, start_date, end_date) VALUES (?, ?, '2026-09-01', '2027-07-31') RETURNING id",
				UUID.class, school, label);
	}

	private UUID term(UUID school, UUID year, String label, String start, String end) {
		return jdbc.queryForObject(
				"INSERT INTO term (school_id, academic_year_id, label, start_date, end_date) VALUES (?, ?, ?, ?::date, ?::date) RETURNING id",
				UUID.class, school, year, label, start, end);
	}

	private UUID block(UUID school, String name) {
		return jdbc.queryForObject("INSERT INTO block (school_id, name) VALUES (?, ?) RETURNING id", UUID.class,
				school, name);
	}

	private UUID schoolClass(UUID school, UUID year, UUID block, String name) {
		return jdbc.queryForObject(
				"INSERT INTO school_class (school_id, academic_year_id, block_id, name, level) VALUES (?, ?, ?, ?, '4e') RETURNING id",
				UUID.class, school, year, block, name);
	}

	private UUID period(UUID school, int number, String start, String end) {
		return jdbc.queryForObject(
				"INSERT INTO period (school_id, number, start_time, end_time) VALUES (?, ?, ?::time, ?::time) RETURNING id",
				UUID.class, school, number, start, end);
	}

	private void slot(UUID school, UUID schoolClass, int weekday, UUID period) {
		jdbc.update(
				"INSERT INTO timetable_slot (school_id, class_id, weekday, period_id, teacher_id, subject) VALUES (?, ?, ?, ?, ?, 'Mathématiques')",
				school, schoolClass, weekday, period, UUID.randomUUID());
	}

}
