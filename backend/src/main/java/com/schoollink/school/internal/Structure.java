package com.schoollink.school.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Request and response shapes of the school structure API (UC-A1, D1, D3, D5, D10). Responses never
 * carry the school id: it always comes from the signed-in user (NFR-8).
 */
final class Structure {

	private Structure() {
	}

	/** A write request: the column values it sets. */
	interface Columns {

		Map<String, Object> columns();

	}

	private static Map<String, Object> columns(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	record AcademicYearView(UUID id, String label, LocalDate startDate, LocalDate endDate) {
	}

	record AcademicYearRequest(@NotBlank @Size(max = 40) String label, @NotNull LocalDate startDate,
			@NotNull LocalDate endDate) implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("label", label.strip(), "start_date", startDate, "end_date", endDate);
		}

	}

	record TermView(UUID id, UUID academicYearId, String label, LocalDate startDate, LocalDate endDate) {
	}

	record TermRequest(@NotNull UUID academicYearId, @NotBlank @Size(max = 40) String label,
			@NotNull LocalDate startDate, @NotNull LocalDate endDate) implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("academic_year_id", academicYearId, "label", label.strip(), "start_date", startDate,
					"end_date", endDate);
		}

	}

	record HolidayView(UUID id, UUID termId, String label, LocalDate startDate, LocalDate endDate) {
	}

	record HolidayRequest(@NotNull UUID termId, @NotBlank @Size(max = 80) String label, @NotNull LocalDate startDate,
			@NotNull LocalDate endDate) implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("term_id", termId, "label", label.strip(), "start_date", startDate, "end_date",
					endDate);
		}

	}

	record PeriodView(UUID id, int number, LocalTime startTime, LocalTime endTime) {
	}

	record PeriodRequest(@NotNull @Min(1) @Max(12) Integer number, @NotNull LocalTime startTime,
			@NotNull LocalTime endTime) implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("number", number, "start_time", startTime, "end_time", endTime);
		}

	}

	record BlockView(UUID id, String name) {
	}

	record BlockRequest(@NotBlank @Size(max = 60) String name) implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("name", name.strip());
		}

	}

	record SchoolClassView(UUID id, UUID academicYearId, UUID blockId, String name, String level) {
	}

	record SchoolClassRequest(@NotNull UUID academicYearId, @NotNull UUID blockId, @NotBlank @Size(max = 40) String name,
			@NotBlank @Size(max = 40) String level) implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("academic_year_id", academicYearId, "block_id", blockId, "name", name.strip(),
					"level", level.strip());
		}

	}

	/** D1: weekday is ISO, 1 = Monday; school days are Monday to Friday (D3). */
	record TimetableSlotView(UUID id, UUID classId, int weekday, UUID periodId, UUID teacherId, String subject) {
	}

	record TimetableSlotRequest(@NotNull UUID classId, @NotNull @Min(1) @Max(5) Integer weekday,
			@NotNull UUID periodId, @NotNull UUID teacherId, @NotBlank @Size(max = 60) String subject)
			implements Columns {

		@Override
		public Map<String, Object> columns() {
			return Structure.columns("class_id", classId, "weekday", weekday, "period_id", periodId, "teacher_id",
					teacherId, "subject", subject.strip());
		}

	}

}
