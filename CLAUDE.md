# SchoolLink

SchoolLink is a school attendance and parent-notification platform for secondary schools. Teachers record attendance per period and assistant DMs record lateness at the gate. Each absence or lateness reaches the guardians the same day, by push or by one daily SMS digest. Repeated patterns raise risk alerts, and the Discipline Master (DM) decides what to do with each one (dismiss, summon, disciplinary issue, refer to the counsellor). The first release is a pilot in two schools, with an anonymised research export used to evaluate it.

**The source of truth is [docs/SPEC.md](docs/SPEC.md).** Business rules are numbered BR-1…BR-25, use cases UC-xx and non-functional requirements NFR-x. Read the relevant sections before changing behaviour. The task breakdown and the open questions are in [docs/PLAN.md](docs/PLAN.md).

## Repository layout

| Folder | What | Stack and key libraries |
| --- | --- | --- |
| `mobile/` | Android app (Flutter, one app with role-based screens) for teachers, assistant DMs, DMs, counsellors and parents | Flutter, Riverpod (state), drift (SQLite, offline storage and sync queue), dio (HTTP with JWT and refresh interceptors), firebase_messaging + flutter_local_notifications, flutter_secure_storage (tokens), flutter_localizations + intl (ARB files, FR/EN) |
| `backend/` | Java 21 + Spring Boot 4 **modular monolith**, Maven, one REST API under `/api/v1` | Spring Boot 4 (Spring Framework 7, Jakarta EE 11, Jackson 3), Spring Data JPA, Spring Security + JWT (BCrypt), PostgreSQL 16, Flyway, Firebase Admin SDK (FCM), Apache POI + OpenCSV (import), Lombok, JUnit 5 + Testcontainers |
| `web/` | Dashboard for administrators, DMs and the researcher | React, Vite, TypeScript, MUI, Recharts, i18next (FR/EN) |
| `docs/` | SPEC.md, PLAN.md and later docs | Markdown |
| `loadtest/` | Load test of SPEC §11 (2 schools × 40 classes × 8 periods): fake seed, script, outbox checks; see its README | k6 (Docker image), SQL |
| `docker-compose.yml` | Local PostgreSQL 16 for development | Docker Compose |

Backend base package is `com.schoollink` (`SchoolLinkApplication`). Spring Boot 4 removed or renamed some Boot 3 APIs, so check examples against the Boot 4 docs before copying them. Backend modules (sub-packages of `com.schoollink`, in `backend/src/main/java/com/schoollink/`): `auth`, `school`, `people`, `attendance`, `lateness`, `justification`, `detection`, `discipline`, `notification`, `reporting`, `audit`, `shared`. Modules call each other through public Java interfaces and Spring application events, never over HTTP. A module's `internal` package is private to that module.

## Non-negotiable rules

- **Modular monolith, no microservices.** There is one deployable backend. Do not add message brokers or separate services.
- **Every query is filtered by `school_id` and the user's scope, on the server** (NFR-8). School and scope come from the JWT principal, never from the request body or query string. Every endpoint gets an integration test per role, covering both the allowed and the forbidden case.
- **Rules only raise alerts; never automate a DM decision** (BR-16–BR-19). Detection code may create a `RiskAlert` and nothing else: no automatic summons, discipline or referral.
- **Attendance and lateness are separate records and never override each other** (BR-8, BR-9). There is no reconciliation code between them.
- **Locked attendance sessions are never edited** (BR-4). Only a DM can cancel a record, with a mandatory reason (BR-13). The cancellation is stored and audited, and a correction is sent if the parent was already notified (BR-14). Cancelled rows stay in the database and are excluded from counts (BR-15).
- **Notifications go through the `notification_outbox` table, never inline** (BR-20–BR-24, NFR-6). Outbox rows are written in the same transaction as the event that causes them. A separate dispatcher sends them, with retries.
- **Teacher and assistant DM screens are offline-first** (BR-6, NFR-3). The device generates a UUID for each session or lateness record, and the server endpoints are idempotent on that UUID (`PUT /sessions/{uuid}`, `PUT /lateness/{uuid}`).
- **All user-facing text exists in French and English**: app, web, API error messages, push and SMS templates (NFR-12). No hard-coded UI strings. Add the key to both languages in the same change.
- **No real student data in seeds, fixtures or tests.** Use generated fake data only. Never commit an import file from a real school.
- **Privacy** (NFR-9, NFR-10): phone numbers never appear in URLs or logs. Store the birth year only, no photos, and no health detail beyond "illness".
- **Audit** (NFR-11): every create, edit, cancel, justification, decision and export writes to the append-only `audit_log`.
- **Cite the BR/UC/NFR number** in commit messages (e.g. `BR-4: lock session after second confirmation`) and in code comments where a rule is implemented or tested.

## Commands

Start local infrastructure from the repo root:

```bash
cp .env.example .env           # once; local dev credentials
docker compose up -d db        # PostgreSQL 16 on localhost:5433 (5432 is often a local PostgreSQL install)
docker compose down            # stop (add -v to wipe the data volume)
```

### mobile/

```bash
cd mobile
flutter pub get
dart run build_runner build --delete-conflicting-outputs   # drift / riverpod codegen
flutter gen-l10n                                           # ARB -> localizations
flutter analyze
flutter test
flutter run                                                # emulator or device
flutter build apk --release --split-per-abi                # NFR-4: each APK < 30 MB
```

### backend/

Maven is not installed globally, so use the wrapper (`mvnw.cmd` on Windows cmd/PowerShell). Testcontainers needs Docker running.

```bash
cd backend
./mvnw verify                                              # unit + integration tests (Testcontainers)
./mvnw test -Dtest=SomeTest                                # single test class
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev      # API on :8080, fake seed data
```

The backend fails closed without a JWT signing secret: outside the `dev` and `test` profiles it refuses to start unless `JWT_SECRET` (32+ bytes) is set. Tests get the `test` profile from `src/test/resources/config/application.properties`.

### web/

```bash
cd web
npm ci
npm run dev        # Vite dev server, proxies /api to :8080
npm run lint
npm test           # Vitest
npm run build
```
