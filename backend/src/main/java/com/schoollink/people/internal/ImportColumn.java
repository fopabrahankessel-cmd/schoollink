package com.schoollink.people.internal;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The columns of the student import file (UC-A3, docs/IMPORT.md). Headers are matched after
 * normalization (case, accents, spaces and punctuation ignored), so "Année de naissance",
 * "annee_naissance" and "ANNEE NAISSANCE" are the same column. To follow a school's own file,
 * add its headers as aliases here.
 */
enum ImportColumn {

	MATRICULE(true, "matricule", "student_id", "id_eleve"),
	LAST_NAME(true, "nom", "last_name", "nom_eleve"),
	FIRST_NAME(true, "prenom", "prenoms", "first_name"),
	SEX(true, "sexe", "sex"),
	BIRTH(true, "annee_naissance", "annee_de_naissance", "date_naissance", "date_de_naissance", "birth_year",
			"birth_date"),
	CLASS(true, "classe", "class"),
	GUARDIAN1_NAME(true, "tuteur1_nom", "parent1_nom", "guardian1_name"),
	GUARDIAN1_PHONE(true, "tuteur1_telephone", "parent1_telephone", "guardian1_phone"),
	GUARDIAN1_RELATION(false, "tuteur1_lien", "parent1_lien", "guardian1_relation"),
	GUARDIAN1_LANGUAGE(false, "tuteur1_langue", "parent1_langue", "guardian1_language"),
	GUARDIAN2_NAME(false, "tuteur2_nom", "parent2_nom", "guardian2_name"),
	GUARDIAN2_PHONE(false, "tuteur2_telephone", "parent2_telephone", "guardian2_phone"),
	GUARDIAN2_RELATION(false, "tuteur2_lien", "parent2_lien", "guardian2_relation"),
	GUARDIAN2_LANGUAGE(false, "tuteur2_langue", "parent2_langue", "guardian2_language");

	/** The column must be present in the header row. */
	final boolean required;

	/** The first alias is the template header. */
	final List<String> aliases;

	ImportColumn(boolean required, String... aliases) {
		this.required = required;
		this.aliases = List.of(aliases);
	}

	String header() {
		return aliases.getFirst();
	}

	static Optional<ImportColumn> forHeader(String header) {
		String key = normalize(header);
		for (ImportColumn column : values()) {
			if (column.aliases.contains(key)) {
				return Optional.of(column);
			}
		}
		return Optional.empty();
	}

	/** "Année de naissance" -> "annee_de_naissance"; "N° Tél." -> "n_tel". */
	static String normalize(String text) {
		String plain = Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

}
