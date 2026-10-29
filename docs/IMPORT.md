# Student and guardian import (UC-A3)

The admin imports the students of one academic year from a CSV or Excel file, in two steps:

1. **Preview**: `POST /api/v1/students/import` (multipart: `file`, `academicYearId`). Every row is checked, and the import is stored with its list of errors. Nothing reaches the student list yet.
2. **Commit** (task 1.12): `POST /api/v1/students/import/{id}/commit` creates the students, guardians and links.

`GET /api/v1/students/import/{id}` shows an import again with its errors, in the reader's language.

## File

- **Format**: CSV (`.csv`) or Excel (`.xlsx`, first sheet only). 5 MB and 5,000 students at most.
- **CSV**: separator `;` (Excel in French) or `,`, UTF-8 or Windows-1252 (Excel's default "CSV" on Windows).
- **Row 1** holds the headers. Rows that are completely empty are skipped. Columns the template does not know are ignored.
- **Headers** are matched without case, accents, spaces or punctuation: `Année naissance`, `annee_naissance` and `ANNEE NAISSANCE` are the same column.

Template: [import-template.csv](import-template.csv). It contains made-up students only.

## Columns

| Header | Also accepted | Required | Values |
| --- | --- | --- | --- |
| `matricule` | `student_id`, `id_eleve` | yes | Unique in the school, at most 30 characters. |
| `nom` | `last_name`, `nom_eleve` | yes | At most 60 characters. |
| `prenom` | `prenoms`, `first_name` | yes | At most 60 characters. |
| `sexe` | `sex` | yes | `M` or `F`. Also `Masculin`/`Féminin`, `G`/`F`, `Male`/`Female`. |
| `annee_naissance` | `date_naissance`, `birth_year`, `birth_date` | yes | A year (`2012`) or a date (`15/03/2012`, `2012-03-15`). **Only the year is kept** (NFR-10). It must be 5 to 30 years before the current year. |
| `classe` | `class` | yes | The exact name of a class of the chosen academic year, ignoring case and extra spaces (`6e  a` = `6e A`). |
| `tuteur1_nom` | `parent1_nom`, `guardian1_name` | yes | The primary guardian. |
| `tuteur1_telephone` | `parent1_telephone`, `guardian1_phone` | yes | One Cameroon number: `699 00 00 01`, `+237699000001`, `00237699000001`. |
| `tuteur1_lien` | `parent1_lien`, `guardian1_relation` | no | `MERE`, `PERE`, `TUTEUR` (default) or `AUTRE`. |
| `tuteur1_langue` | `parent1_langue`, `guardian1_language` | no | `FR` or `EN`. Default: the school's language. Used for SMS and push messages. |
| `tuteur2_nom`, `tuteur2_telephone`, `tuteur2_lien`, `tuteur2_langue` | `parent2_…`, `guardian2_…` | no | A second guardian. If any of these is filled, the name and phone are required, and the phone must differ from the first guardian's. |

A guardian is identified by their phone number within the school (D15): brothers and sisters with the same guardian phone share one guardian.

## Errors

Each error gives the row number as the spreadsheet shows it (the header is row 1), the column header as written in the file, a code and a message. File-level problems (unsupported format, unreadable file, missing columns, too many rows) reject the upload with a 400 instead.

| Code | Meaning |
| --- | --- |
| `validation.not_blank` | A required value is empty. |
| `import.too_long` | The value is longer than allowed. |
| `import.sex_invalid` | Not a recognized sex. |
| `import.birth_year_invalid` | Not a year or date, or not plausible. |
| `import.class_unknown` | No class with this name in the academic year. |
| `validation.phone` | Not a Cameroon phone number. |
| `import.relation_invalid`, `import.language_invalid` | Unrecognized relation or language. |
| `import.guardian_duplicate` | Both guardians have the same phone. |
| `import.matricule_duplicate_in_file` | The matricule already appears on an earlier row. |
| `import.matricule_exists` | A student with this matricule is already in the school. |

## Adapting to a school's own file

Add the school's headers as aliases in `ImportColumn` (backend `people` module) and the values it uses (e.g. other words for sex or relation) in `ImportRows`. Never commit a real school's file: test fixtures use generated data only.
