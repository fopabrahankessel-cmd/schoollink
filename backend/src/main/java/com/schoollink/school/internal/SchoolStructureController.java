package com.schoollink.school.internal;

import com.schoollink.school.internal.Structure.AcademicYearRequest;
import com.schoollink.school.internal.Structure.AcademicYearView;
import com.schoollink.school.internal.Structure.BlockRequest;
import com.schoollink.school.internal.Structure.BlockView;
import com.schoollink.school.internal.Structure.HolidayRequest;
import com.schoollink.school.internal.Structure.HolidayView;
import com.schoollink.school.internal.Structure.PeriodRequest;
import com.schoollink.school.internal.Structure.PeriodView;
import com.schoollink.school.internal.Structure.SchoolClassRequest;
import com.schoollink.school.internal.Structure.SchoolClassView;
import com.schoollink.school.internal.Structure.TermRequest;
import com.schoollink.school.internal.Structure.TermView;
import com.schoollink.school.internal.Structure.TimetableSlotRequest;
import com.schoollink.school.internal.Structure.TimetableSlotView;
import com.schoollink.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC-A1 school structure API: academic years, terms, holidays (D10), periods, blocks (D5), classes
 * and the timetable (D1). Access rules are in {@link StructureStore}.
 */
@RestController
@RequestMapping("/api/v1")
class SchoolStructureController {

	private final StructureStore store;

	private final StructureTables tables;

	SchoolStructureController(StructureStore store, StructureTables tables) {
		this.store = store;
		this.tables = tables;
	}

	// Academic years

	@GetMapping("/academic-years")
	List<AcademicYearView> years(@AuthenticationPrincipal CurrentUser user) {
		return store.list(user, tables.years, Map.of(), "start_date");
	}

	@PostMapping("/academic-years")
	@ResponseStatus(HttpStatus.CREATED)
	AcademicYearView createYear(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AcademicYearRequest body) {
		return store.create(user, tables.years, body);
	}

	@PutMapping("/academic-years/{id}")
	AcademicYearView updateYear(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody AcademicYearRequest body) {
		return store.update(user, tables.years, id, body);
	}

	@DeleteMapping("/academic-years/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteYear(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.years, id);
	}

	// Terms

	@GetMapping("/terms")
	List<TermView> terms(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) UUID academicYearId) {
		return store.list(user, tables.terms, filter("academic_year_id", academicYearId), "start_date");
	}

	@PostMapping("/terms")
	@ResponseStatus(HttpStatus.CREATED)
	TermView createTerm(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TermRequest body) {
		return store.create(user, tables.terms, body);
	}

	@PutMapping("/terms/{id}")
	TermView updateTerm(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody TermRequest body) {
		return store.update(user, tables.terms, id, body);
	}

	@DeleteMapping("/terms/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteTerm(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.terms, id);
	}

	// Holidays (D10)

	@GetMapping("/holidays")
	List<HolidayView> holidays(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) UUID termId) {
		return store.list(user, tables.holidays, filter("term_id", termId), "start_date");
	}

	@PostMapping("/holidays")
	@ResponseStatus(HttpStatus.CREATED)
	HolidayView createHoliday(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody HolidayRequest body) {
		return store.create(user, tables.holidays, body);
	}

	@PutMapping("/holidays/{id}")
	HolidayView updateHoliday(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody HolidayRequest body) {
		return store.update(user, tables.holidays, id, body);
	}

	@DeleteMapping("/holidays/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteHoliday(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.holidays, id);
	}

	// Periods

	@GetMapping("/periods")
	List<PeriodView> periods(@AuthenticationPrincipal CurrentUser user) {
		return store.list(user, tables.periods, Map.of(), "number");
	}

	@PostMapping("/periods")
	@ResponseStatus(HttpStatus.CREATED)
	PeriodView createPeriod(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PeriodRequest body) {
		return store.create(user, tables.periods, body);
	}

	@PutMapping("/periods/{id}")
	PeriodView updatePeriod(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody PeriodRequest body) {
		return store.update(user, tables.periods, id, body);
	}

	@DeleteMapping("/periods/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deletePeriod(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.periods, id);
	}

	// Blocks (D5)

	@GetMapping("/blocks")
	List<BlockView> blocks(@AuthenticationPrincipal CurrentUser user) {
		return store.list(user, tables.blocks, Map.of(), "name");
	}

	@GetMapping("/blocks/{id}")
	BlockView block(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return store.get(user, tables.blocks, id);
	}

	@PostMapping("/blocks")
	@ResponseStatus(HttpStatus.CREATED)
	BlockView createBlock(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody BlockRequest body) {
		return store.create(user, tables.blocks, body);
	}

	@PutMapping("/blocks/{id}")
	BlockView updateBlock(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody BlockRequest body) {
		return store.update(user, tables.blocks, id, body);
	}

	@DeleteMapping("/blocks/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteBlock(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.blocks, id);
	}

	// Classes

	@GetMapping("/classes")
	List<SchoolClassView> classes(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) UUID academicYearId, @RequestParam(required = false) UUID blockId) {
		Map<String, Object> filters = filter("academic_year_id", academicYearId);
		filters.put("block_id", blockId);
		return store.list(user, tables.classes, filters, "level, name");
	}

	@GetMapping("/classes/{id}")
	SchoolClassView schoolClass(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return store.get(user, tables.classes, id);
	}

	@PostMapping("/classes")
	@ResponseStatus(HttpStatus.CREATED)
	SchoolClassView createClass(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SchoolClassRequest body) {
		return store.create(user, tables.classes, body);
	}

	@PutMapping("/classes/{id}")
	SchoolClassView updateClass(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody SchoolClassRequest body) {
		return store.update(user, tables.classes, id, body);
	}

	@DeleteMapping("/classes/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteClass(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.classes, id);
	}

	// Timetable (D1)

	@GetMapping("/timetable-slots")
	List<TimetableSlotView> timetable(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) UUID classId) {
		return store.list(user, tables.timetable, filter("class_id", classId), "weekday, (SELECT number FROM period p WHERE p.id = period_id)");
	}

	@PostMapping("/timetable-slots")
	@ResponseStatus(HttpStatus.CREATED)
	TimetableSlotView createSlot(@AuthenticationPrincipal CurrentUser user,
			@Valid @RequestBody TimetableSlotRequest body) {
		return store.create(user, tables.timetable, body);
	}

	@PutMapping("/timetable-slots/{id}")
	TimetableSlotView updateSlot(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@Valid @RequestBody TimetableSlotRequest body) {
		return store.update(user, tables.timetable, id, body);
	}

	@DeleteMapping("/timetable-slots/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteSlot(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		store.delete(user, tables.timetable, id);
	}

	/** Filter columns are always fixed here, never taken from the request. */
	private static Map<String, Object> filter(String column, Object value) {
		Map<String, Object> filters = new HashMap<>();
		filters.put(column, value);
		return filters;
	}

}
