package com.schoollink.school.internal;

import com.schoollink.school.internal.Structure.AcademicYearView;
import com.schoollink.school.internal.Structure.BlockView;
import com.schoollink.school.internal.Structure.HolidayView;
import com.schoollink.school.internal.Structure.PeriodView;
import com.schoollink.school.internal.Structure.SchoolClassView;
import com.schoollink.school.internal.Structure.TermView;
import com.schoollink.school.internal.Structure.TimetableSlotView;
import com.schoollink.shared.error.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The school structure tables and the rules checked before each write (UC-A1). Database
 * constraints (V1) back every rule up; the checks here give the admin a precise error.
 */
@Component
class StructureTables {

	/** Stands for "no row" when a create has no id to exclude yet. */
	private static final UUID NONE = new UUID(0, 0);

	final StructureTable<AcademicYearView> years;

	final StructureTable<TermView> terms;

	final StructureTable<HolidayView> holidays;

	final StructureTable<PeriodView> periods;

	final StructureTable<BlockView> blocks;

	final StructureTable<SchoolClassView> classes;

	final StructureTable<TimetableSlotView> timetable;

	private final NamedParameterJdbcTemplate jdbc;

	StructureTables(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
		years = new StructureTable<>("academic_year", "id, label, start_date, end_date", AcademicYearView.class, null,
				"academic_year.duplicate", this::checkYear);
		terms = new StructureTable<>("term", "id, academic_year_id, label, start_date, end_date", TermView.class, null,
				"term.duplicate", this::checkTerm);
		holidays = new StructureTable<>("holiday", "id, term_id, label, start_date, end_date", HolidayView.class, null,
				"holiday.duplicate", this::checkHoliday);
		periods = new StructureTable<>("period", "id, number, start_time, end_time", PeriodView.class, null,
				"period.duplicate", this::checkPeriod);
		// D5: a DM sees the block they are in charge of; a teacher the blocks of the classes they teach
		blocks = new StructureTable<>("block", "id, name", BlockView.class,
				"id IN (SELECT block_id FROM school_class WHERE id IN (:ids))", "block.duplicate", (school, id, v) -> {
				});
		classes = new StructureTable<>("school_class", "id, academic_year_id, block_id, name, level",
				SchoolClassView.class, "id IN (:ids)", "class.duplicate", this::checkClass);
		timetable = new StructureTable<>("timetable_slot", "id, class_id, weekday, period_id, teacher_id, subject",
				TimetableSlotView.class, "class_id IN (:ids)", "timetable.slot_taken", this::checkSlot);
	}

	private void checkYear(UUID school, UUID id, Map<String, Object> v) {
		LocalDate start = (LocalDate) v.get("start_date");
		LocalDate end = (LocalDate) v.get("end_date");
		requireOrder(start.isBefore(end), "endDate");
		if (id != null && exists("""
				SELECT 1 FROM term WHERE academic_year_id = :id AND (start_date < :start OR end_date > :end)""",
				params(school).addValue("id", id).addValue("start", start).addValue("end", end))) {
			throw ApiException.conflict("academic_year.terms_outside");
		}
	}

	/** BR-16 counts over the current term, so terms lie within their year and never overlap (V1). */
	private void checkTerm(UUID school, UUID id, Map<String, Object> v) {
		LocalDate start = (LocalDate) v.get("start_date");
		LocalDate end = (LocalDate) v.get("end_date");
		requireOrder(start.isBefore(end), "endDate");
		UUID year = (UUID) v.get("academic_year_id");
		requireReference("academic_year", year, school, "academicYearId");
		if (exists("SELECT 1 FROM academic_year WHERE id = :year AND (:start < start_date OR :end > end_date)",
				params(school).addValue("year", year).addValue("start", start).addValue("end", end))) {
			throw ApiException.invalid("term.outside_year", "startDate");
		}
		if (id != null && exists("""
				SELECT 1 FROM holiday WHERE term_id = :id AND (start_date < :start OR end_date > :end)""",
				params(school).addValue("id", id).addValue("start", start).addValue("end", end))) {
			throw ApiException.conflict("term.holidays_outside");
		}
	}

	/** D10: holidays are set per term and lie within it. */
	private void checkHoliday(UUID school, UUID id, Map<String, Object> v) {
		LocalDate start = (LocalDate) v.get("start_date");
		LocalDate end = (LocalDate) v.get("end_date");
		requireOrder(!end.isBefore(start), "endDate");
		UUID term = (UUID) v.get("term_id");
		requireReference("term", term, school, "termId");
		if (exists("SELECT 1 FROM term WHERE id = :term AND (:start < start_date OR :end > end_date)",
				params(school).addValue("term", term).addValue("start", start).addValue("end", end))) {
			throw ApiException.invalid("holiday.outside_term", "startDate");
		}
	}

	private void checkPeriod(UUID school, UUID id, Map<String, Object> v) {
		LocalTime start = (LocalTime) v.get("start_time");
		LocalTime end = (LocalTime) v.get("end_time");
		requireOrder(start.isBefore(end), "endTime");
		if (exists("""
				SELECT 1 FROM period
				WHERE school_id = :school AND id <> :id AND start_time < :end AND end_time > :start""",
				params(school).addValue("id", id == null ? NONE : id).addValue("start", start).addValue("end", end))) {
			throw ApiException.conflict("period.overlap");
		}
	}

	private void checkClass(UUID school, UUID id, Map<String, Object> v) {
		requireReference("academic_year", (UUID) v.get("academic_year_id"), school, "academicYearId");
		requireReference("block", (UUID) v.get("block_id"), school, "blockId");
	}

	/** D1 + BR-1: only a teacher assigned to the class and subject, and not busy elsewhere in that period. */
	private void checkSlot(UUID school, UUID id, Map<String, Object> v) {
		requireReference("school_class", (UUID) v.get("class_id"), school, "classId");
		requireReference("period", (UUID) v.get("period_id"), school, "periodId");
		MapSqlParameterSource p = params(school).addValue("id", id == null ? NONE : id)
			.addValue("teacher", v.get("teacher_id"))
			.addValue("class", v.get("class_id"))
			.addValue("subject", v.get("subject"))
			.addValue("weekday", v.get("weekday"))
			.addValue("period", v.get("period_id"));
		if (!exists("""
				SELECT 1 FROM teaching_assignment
				WHERE school_id = :school AND teacher_id = :teacher AND class_id = :class AND subject = :subject""", p)) {
			throw ApiException.invalid("timetable.teacher_not_assigned", "teacherId");
		}
		if (exists("""
				SELECT 1 FROM timetable_slot
				WHERE school_id = :school AND teacher_id = :teacher AND weekday = :weekday AND period_id = :period
				  AND id <> :id""", p)) {
			throw ApiException.conflict("timetable.teacher_busy");
		}
	}

	private static void requireOrder(boolean ordered, String field) {
		if (!ordered) {
			throw ApiException.invalid("validation.date_order", field);
		}
	}

	/** A reference to another school's row is reported like a missing one (NFR-8). */
	private void requireReference(String table, UUID id, UUID school, String field) {
		if (!exists("SELECT 1 FROM " + table + " WHERE id = :ref AND school_id = :school",
				params(school).addValue("ref", id))) {
			throw ApiException.invalid("reference.unknown", field);
		}
	}

	private boolean exists(String sql, MapSqlParameterSource params) {
		return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + sql + ")", params, Boolean.class));
	}

	private static MapSqlParameterSource params(UUID school) {
		return new MapSqlParameterSource("school", school);
	}

}
