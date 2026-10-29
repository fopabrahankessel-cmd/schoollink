package com.schoollink.people.internal;

import com.schoollink.shared.phone.PhoneNumbers;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks one data row of an import file (UC-A3) and turns it into a {@link StagedStudent}, or into
 * the list of what is wrong with it. Messages are rendered later, in the reader's language
 * (NFR-12). Privacy: only the birth year is kept (NFR-10), and no value from the file is ever put
 * in an error, except the class name and a row number.
 */
final class ImportRows {

	static final int MAX_NAME = 60;

	static final int MAX_MATRICULE = 30;

	private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ISO_LOCAL_DATE,
			DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
			DateTimeFormatter.ofPattern("d.M.uuuu"));

	private ImportRows() {
	}

	record StagedGuardian(String name, String phone, String relation, String language, boolean primary) {
	}

	record StagedStudent(String matricule, String lastName, String firstName, String sex, int birthYear,
			UUID classId, String className, List<StagedGuardian> guardians) {
	}

	/** @param column the header as written in the file, {@code null} for the whole row */
	record RowError(String column, String code, List<Object> args) {

		static RowError of(String column, String code, Object... args) {
			return new RowError(column, code, List.of(args));
		}

	}

	record Result(StagedStudent student, List<RowError> errors) {
	}

	/**
	 * @param cells the row's values by column, already stripped
	 * @param headers each column's header as written in the file
	 * @param classes the academic year's classes, by {@link #classKey}
	 * @param currentYear birth years are plausible from 30 to 5 years before it
	 */
	static Result check(Map<ImportColumn, String> cells, Map<ImportColumn, String> headers, Map<String, UUID> classes,
			String schoolLanguage, int currentYear) {
		List<RowError> errors = new ArrayList<>();
		Row row = new Row(cells, headers, errors);

		String matricule = row.required(ImportColumn.MATRICULE, MAX_MATRICULE);
		String lastName = row.required(ImportColumn.LAST_NAME, MAX_NAME);
		String firstName = row.required(ImportColumn.FIRST_NAME, MAX_NAME);
		String sex = row.required(ImportColumn.SEX, MAX_NAME);
		if (sex != null) {
			sex = sex(sex).orElseGet(() -> row.error(ImportColumn.SEX, "import.sex_invalid"));
		}
		String birth = row.required(ImportColumn.BIRTH, MAX_NAME);
		Integer birthYear = null;
		if (birth != null) {
			birthYear = birthYear(birth).filter(y -> y >= currentYear - 30 && y <= currentYear - 5).orElse(null);
			if (birthYear == null) {
				row.error(ImportColumn.BIRTH, "import.birth_year_invalid");
			}
		}
		String className = row.required(ImportColumn.CLASS, MAX_NAME);
		UUID classId = null;
		if (className != null) {
			classId = classes.get(classKey(className));
			if (classId == null) {
				row.error(ImportColumn.CLASS, "import.class_unknown", className);
			}
		}

		List<StagedGuardian> guardians = new ArrayList<>();
		guardian(row, true, ImportColumn.GUARDIAN1_NAME, ImportColumn.GUARDIAN1_PHONE, ImportColumn.GUARDIAN1_RELATION,
				ImportColumn.GUARDIAN1_LANGUAGE, schoolLanguage).ifPresent(guardians::add);
		guardian(row, false, ImportColumn.GUARDIAN2_NAME, ImportColumn.GUARDIAN2_PHONE, ImportColumn.GUARDIAN2_RELATION,
				ImportColumn.GUARDIAN2_LANGUAGE, schoolLanguage).ifPresent(guardians::add);
		if (guardians.size() == 2 && guardians.get(0).phone().equals(guardians.get(1).phone())) {
			row.error(ImportColumn.GUARDIAN2_PHONE, "import.guardian_duplicate");
		}

		if (!errors.isEmpty()) {
			return new Result(null, List.copyOf(errors));
		}
		return new Result(new StagedStudent(matricule, lastName, firstName, sex, birthYear, classId, className.strip(),
				List.copyOf(guardians)), List.of());
	}

	/** Class names match ignoring case and repeated spaces: "6e  a" is "6e A". */
	static String classKey(String name) {
		return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/** The primary guardian is required (notifications need a phone, BR-20); the second is optional. */
	private static Optional<StagedGuardian> guardian(Row row, boolean primary, ImportColumn nameColumn,
			ImportColumn phoneColumn, ImportColumn relationColumn, ImportColumn languageColumn, String schoolLanguage) {
		boolean any = row.has(nameColumn) || row.has(phoneColumn) || row.has(relationColumn) || row.has(languageColumn);
		if (!primary && !any) {
			return Optional.empty();
		}
		String name = row.required(nameColumn, MAX_NAME);
		String rawPhone = row.required(phoneColumn, MAX_NAME);
		String phone = null;
		if (rawPhone != null) {
			// D15: Cameroon numbers only; one number per cell
			phone = PhoneNumbers.normalize(rawPhone).orElseGet(() -> row.error(phoneColumn, "validation.phone"));
		}
		String relation = row.has(relationColumn) ? relation(row.value(relationColumn))
			.orElseGet(() -> row.error(relationColumn, "import.relation_invalid")) : "GUARDIAN";
		String language = row.has(languageColumn) ? language(row.value(languageColumn))
			.orElseGet(() -> row.error(languageColumn, "import.language_invalid")) : schoolLanguage;
		if (name == null || phone == null || relation == null || language == null) {
			return Optional.empty();
		}
		return Optional.of(new StagedGuardian(name, phone, relation, language, primary));
	}

	private static Optional<String> sex(String value) {
		return switch (ImportColumn.normalize(value)) {
			case "m", "masculin", "male", "g", "garcon", "h", "homme" -> Optional.of("M");
			case "f", "feminin", "female", "fille", "femme" -> Optional.of("F");
			default -> Optional.empty();
		};
	}

	/** "2012", or a full date of which only the year is kept (NFR-10). */
	private static Optional<Integer> birthYear(String value) {
		if (value.matches("\\d{4}")) {
			return Optional.of(Integer.parseInt(value));
		}
		for (DateTimeFormatter format : DATES) {
			try {
				return Optional.of(LocalDate.parse(value, format).getYear());
			}
			catch (DateTimeParseException ex) {
				// try the next format
			}
		}
		return Optional.empty();
	}

	private static Optional<String> relation(String value) {
		return switch (ImportColumn.normalize(value)) {
			case "mere", "mother", "maman" -> Optional.of("MOTHER");
			case "pere", "father", "papa" -> Optional.of("FATHER");
			case "tuteur", "tutrice", "guardian" -> Optional.of("GUARDIAN");
			case "autre", "other" -> Optional.of("OTHER");
			default -> Optional.empty();
		};
	}

	private static Optional<String> language(String value) {
		return switch (ImportColumn.normalize(value)) {
			case "fr", "francais", "french" -> Optional.of("FR");
			case "en", "anglais", "english" -> Optional.of("EN");
			default -> Optional.empty();
		};
	}

	private record Row(Map<ImportColumn, String> cells, Map<ImportColumn, String> headers, List<RowError> errors) {

		boolean has(ImportColumn column) {
			return !value(column).isEmpty();
		}

		String value(ImportColumn column) {
			return cells.getOrDefault(column, "");
		}

		/** The value, or {@code null} after recording why it is missing or too long. */
		String required(ImportColumn column, int max) {
			String value = value(column);
			if (value.isEmpty()) {
				return error(column, "validation.not_blank");
			}
			if (value.length() > max) {
				return error(column, "import.too_long", max);
			}
			return value;
		}

		<T> T error(ImportColumn column, String code, Object... args) {
			errors.add(RowError.of(headers.getOrDefault(column, column.header()), code, args));
			return null;
		}

	}

}
