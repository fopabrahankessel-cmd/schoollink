# SchoolLink

School attendance and parent-notification platform for secondary schools. The source of truth is [docs/SPEC.md](docs/SPEC.md); the task breakdown is in [docs/PLAN.md](docs/PLAN.md). Developer commands and project rules are in [CLAUDE.md](CLAUDE.md).

| Folder | What |
| --- | --- |
| `backend/` | Java 21 + Spring Boot 4 modular monolith, REST API under `/api/v1` |
| `web/` | React dashboard for administrators, DMs and the researcher |
| `mobile/` | Flutter Android app for teachers, assistant DMs, DMs, counsellors and parents |
| `docs/` | Specification, plan, import format and samples |

## Local development

Requires Docker, JDK 21 and Node.js.

```bash
cp .env.example .env              # once
docker compose up -d db           # PostgreSQL 16 on localhost:5433
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd web && npm ci && npm run dev   # http://localhost:5173, proxies /api to :8080
```

The `dev` profile seeds two fictional schools (Lycée Démo A, Lycée Démo B) with fake staff, students and guardians; every seeded account uses the password `schoollink-dev` (for example `admin@lycee-demo-a.test`). In IntelliJ IDEA, use the shared run configuration **SchoolLink (dev)** (`backend/.run/`), which sets the profile. Without the `dev` profile the backend refuses to start unless `JWT_SECRET` is set.

## Phase 1 walkthrough

The Phase 1 exit check (PLAN task 1.20): from an empty database to a school with its structure, staff, imported students and invited guardians, without the dev seed. Everything is done through the web app, as the pilot schools will do it. Only fictional data is used.

### 1. Start from an empty database

```bash
docker compose down -v
docker compose up -d db
```

### 2. Start the backend without the dev profile

The school and its first administrator are created at startup from the `SCHOOLLINK_BOOTSTRAP_*` settings, once: when a school of that name exists, nothing happens. The password is temporary and must be changed at the first sign-in.

Bash:

```bash
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"
export SCHOOLLINK_BOOTSTRAP_SCHOOL_NAME="Lycée Pilote (fictif)"
export SCHOOLLINK_BOOTSTRAP_SCHOOL_LANGUAGE=FR      # FR or EN
export SCHOOLLINK_BOOTSTRAP_ADMIN_EMAIL=admin@pilote.test
export SCHOOLLINK_BOOTSTRAP_ADMIN_PASSWORD=Temporaire-2026
./mvnw spring-boot:run
```

PowerShell:

```powershell
cd backend
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:SCHOOLLINK_BOOTSTRAP_SCHOOL_NAME = 'Lycée Pilote (fictif)'
$env:SCHOOLLINK_BOOTSTRAP_SCHOOL_LANGUAGE = 'FR'
$env:SCHOOLLINK_BOOTSTRAP_ADMIN_EMAIL = 'admin@pilote.test'
$env:SCHOOLLINK_BOOTSTRAP_ADMIN_PASSWORD = 'Temporaire-2026'
.\mvnw.cmd spring-boot:run
```

Optional: `SCHOOLLINK_BOOTSTRAP_ADMIN_FIRST_NAME` and `..._ADMIN_LAST_NAME`. Incomplete or invalid settings (language other than FR/EN, no email, a password shorter than 8 characters) stop the startup without creating anything.

Then start the web app: `cd web && npm run dev`, and open http://localhost:5173.

### 3. Steps

Each step names the screen and the expected result. All steps were last checked on 2026-10-29.

- [x] **Startup.** The backend log shows `Bootstrap: created the school 'Lycée Pilote (fictif)' and its administrator admin@pilote.test`. A second start logs `exists already` and changes nothing.
- [x] **First sign-in.** Sign in as `admin@pilote.test` with the temporary password. The app asks for a new password before anything else; after it, the home page shows the setup checklist with every step to do.
- [x] **Calendar** (Établissement › Calendrier). Add the year `2026-2027`, then the term `Trimestre 1` from 2026-09-07 to 2026-12-18. Both appear in their lists.
- [x] **Periods** (Périodes). Add period 1 (07:30–08:25) and period 2 (08:25–09:20).
- [x] **Classes** (Classes). Add the block `Bloc A`, then the classes `6e A` and `5e A` in 2026-2027.
- [x] **Staff** (Personnes › Personnel). Create the teacher Paul Enseignant (`prof@pilote.test`). A temporary password is shown once. Give him the role Enseignant and assign Mathématiques in 6e A.
- [x] **Timetable** (Emploi du temps). Choose 6e A, click Monday period 1 and pick « Mathématiques · Paul Enseignant ». The cell shows the lesson.
- [x] **Import with errors** (Importer des élèves). Upload [docs/import-samples/pilote-with-errors.csv](docs/import-samples/pilote-with-errors.csv) for 2026-2027. Expected: 5 rows read, 2 valid, 3 with errors and 4 problems (row 4 unknown class « 6e B », row 5 sex X, row 6 invalid phone and a matricule already on row 5). Nothing is imported.
- [x] **Clean import.** « Envoyer un fichier corrigé », upload [docs/import-samples/pilote.csv](docs/import-samples/pilote.csv), then « Importer 5 élèves » and confirm. Expected: 5 students created, 5 guardians created (Awa and Junior share Mama Exemple), 0 already known.
- [x] **Guardians** (Tuteurs). Choose 6e A: 4 guardians « pas encore invités ». Mama Exemple lists Awa, and Junior « en 5e A ». « Inviter la classe », then « Envoyer les codes »: the 4 guardians become « invités ». The codes are never shown.
- [x] **Audit.** Every step above wrote to `audit_log`, for example:

  ```bash
  docker compose exec db psql -U schoollink -c "SELECT entity, action, count(*) FROM audit_log GROUP BY 1, 2 ORDER BY 1, 2;"
  ```

  Expected: one `school_bootstrap` entry, the password change (`app_user UPDATE`), the structure, staff and timetable entries, two `student_import CREATE` and one `IMPORT`, and four `invitation_code CREATE`.

Known limit: the invitation codes are created but no SMS leaves yet. The notification outbox and its SMS dispatcher come in Phase 2 (task 2.4).
