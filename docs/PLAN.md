# SchoolLink — MVP implementation plan (phases 1–3)

This plan breaks SPEC.md §12 phases 1–3 into ordered tasks. Each task fits in one session and ends with a check that must pass before the next task starts. References point to SPEC.md (BR/UC/NFR, §sections).

Conventions for every task:
- Backend tasks finish with `./mvnw verify` green. Any task that adds an endpoint also adds role/scope integration tests, covering both the allowed and the forbidden case (NFR-8).
- Mobile and web tasks finish with lint and tests green, and with FR and EN strings for everything new (NFR-12).
- Decisions D1–D14 at the end of this file resolve the ambiguities in the spec.

## Phase 0 — Repository setup

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 0.1 | Move the Flutter app to `mobile/` with `git mv`, rename the package to `schoollink` and the applicationId to `cm.schoollink.app`, remove `ios/ linux/ macos/ web/ windows/`, keep the animated splash (D8), and replace the broken counter widget test with a smoke test | §1 scope | `flutter analyze`, `flutter test` and `flutter build apk --debug` pass from `mobile/` |
| 0.2 | Root `.gitignore`, `docker-compose.yml` (PostgreSQL 16, named volume, healthcheck), `.env.example` | §8 | `docker compose up -d db` then `pg_isready` reports ready |

## Phase 1 — Foundations

Exit criterion: an admin can set up a school and import students.

### Backend

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 1.1 | Spring Boot 4 / Java 21 skeleton with the Maven wrapper and the module packages from CLAUDE.md. Spring Modulith enforces module boundaries. Actuator health | §8 | `ApplicationModules.verify()` test passes; `/actuator/health` returns UP |
| 1.2 | Flyway V1 (Testcontainers PG16 config already added in 1.1): `school`, `academic_year`, `term`, `period`, `school_class`, `block` (D5), `timetable_slot` (D1), `holiday` (F3) | §7 | Migration test applies cleanly on an empty container |
| 1.3 | Shared error model `{code, message, field}`, localized through `messages_fr/en.properties` and `Accept-Language` / the user's language | §9, NFR-12 | Test: one validation error returns the French text for `fr` and the English text for `en` |
| 1.4 | Flyway V2 people tables: `app_user`, `role_assignment`, `teaching_assignment`, `student`, `guardian`, `student_guardian`, `invitation_code`. Phone numbers normalized to E.164, birth year only | §7, NFR-10 | Migration and constraint tests (unique matricule per school, many-to-many guardians) |
| 1.5 | `audit` module: append-only `audit_log`, an `AuditService` other modules call; the DB rejects UPDATE and DELETE | NFR-11 | Test: insert works, update and delete fail |
| 1.6 | Staff auth: `POST /auth/login` (school + phone or email + password, D15, D16), BCrypt, JWT access token 15 min, refresh token 30 days with rotation, `POST /auth/refresh` | NFR-7 | Tests: valid login, wrong password, same phone in two schools resolves to the right account, expired access token, refresh rotates and the old refresh token is rejected |
| 1.7 | Rate limiting on login (and on activation later) | NFR-7 | Test: the request past the limit returns 429 |
| 1.8 | Scope resolution: a `CurrentUser` principal (user, school_id, roles, class ids in scope) built from the role and teaching assignments; a reusable permission-matrix test harness | §3, NFR-8 | Tests: a teacher of class A cannot read class B; school X cannot read school Y; one test per row of the §3 matrix that exists so far |
| 1.9 | School structure API (UC-A1): years, terms, periods, holidays, blocks, classes and the timetable (D1, D5) | UC-A1 | Admin can write; other roles can read or get 403, as in the matrix; changes are audited |
| 1.10 | Staff accounts and assignments API (UC-A2): create teacher, assistant DM, DM and counsellor accounts; assign classes and blocks | UC-A2 | Integration tests; each assignment is audited |
| 1.11 | Student/guardian import, preview step: CSV (OpenCSV) and XLSX (POI) parsing, row-level validation, staged import stored with an error list | UC-A3 | A fake fixture file with known errors returns exactly those errors; no rows appear in `student` |
| 1.12 | Import commit step: applies a valid staged import in one transaction | UC-A3 | Commit creates students, guardians and links; a second commit of the same import is rejected; the import is audited |
| 1.13 | Invitation codes (UC-A4): generate for guardians without an account (D7); hashed, single-use, 30-day expiry, issuing a new code revokes the previous one; sent by SMS through the outbox in 2.4 (D13) | UC-A4 | Tests: expiry, single use, only the hash is stored |
| 1.14 | `dev` profile seed: two fake schools generated with a faker (FR/EN names), with classes, staff, students and guardians | rules | `spring-boot:run` with `dev` gives the expected counts; no real data |

### Web

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 1.15 | Vite + React + TS skeleton: MUI theme, i18next FR/EN with a language switch, router, API client with a token refresh interceptor, login page | NFR-12 | Vitest: login form; manual login against the dev backend in both languages |
| 1.16 | Admin: school structure screens (years, terms, periods, classes, timetable) | UC-A1 | Component tests; manual: create a full term structure |
| 1.17 | Admin: staff accounts and assignments screens | UC-A2 | Component tests; manual: create a teacher and assign 2 classes |
| 1.18 | Admin: import wizard (upload → preview with an error table → commit) | UC-A3 | Manual with the fake fixture file: errors shown, then a clean file commits |
| 1.19 | Admin: guardians and invitation codes (generate and send by SMS, per guardian or per class; guardians who already have an account are skipped) | UC-A4, D7, D13 | Component tests; manual: codes queued in the outbox for a class |
| 1.20 | **Phase 1 exit check**: a scripted walkthrough (README section) from an empty DB to an imported school | §12 | Done by hand and checked off |

## Phase 2 — Attendance loop

Exit criterion: a teacher's confirmation reaches a parent's phone.

### Backend

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 2.1 | Flyway V11: `attendance_session` (UUID PK from the device, unique class + date + period), `attendance_record` (one row per student), `lateness_record` (unique student + date), `justification` | §7 | Migration and constraint tests |
| 2.2 | `GET /me/sessions?date=`: the teacher's expected sessions for the day, with recorded/not-recorded status (D1) | UC-T3 | Test with a fake timetable: the right sessions, with the current period first |
| 2.3 | `PUT /sessions/{uuid}`: create a confirmed, locked session; idempotent; `confirmed_at` comes from the device; publishes `AttendanceConfirmed` | BR-1–BR-6 | Tests: replaying the same UUID gives no duplicate; a different UUID for the same class/date/period gets 409; a non-assigned teacher gets 403; a zero-absentee session is saved (BR-5); any change to a locked session is rejected (BR-4) |
| 2.4 | `notification` module: `notification_outbox` table; an event listener writes one row per guardian per channel, in the guardian's language, in the same transaction | BR-20, NFR-6 | Tests: 2 absentees × 2 guardians gives 4 PUSH rows; when the transaction rolls back there are no rows |
| 2.5 | FR/EN templates for absence, lateness, correction, summons and invitation (push and SMS) | NFR-12 | Snapshot tests for each template in both languages |
| 2.6 | Outbox dispatcher: scheduled poller (`FOR UPDATE SKIP LOCKED`), `PushProvider` interface with a fake, statuses queued/sent/failed, backoff, max 3 retries | BR-21, BR-24, NFR-5/6 | Tests: a provider that fails twice then succeeds ends `sent` with attempts = 3; a row picked mid-send is recovered after a simulated restart |
| 2.7 | Firebase Admin `PushProvider` + `PUT /me/device-token` | BR-21 | Integration test with a mocked `FirebaseMessaging`; a real send to a test device |
| 2.8 | Lateness: student search for the gate (scope-limited) + `PUT /lateness/{uuid}` (idempotent, one per student per day, arrival time after the school start time); publishes `LatenessRecorded` | BR-7–BR-9, UC-AD1/AD2 | Tests: duplicate same day; outside scope gets 404 (D17); attendance rows untouched (BR-9); outbox rows created |
| 2.9 | Parent activation: `POST /auth/parent/activate` (code + phone → OTP) and `/verify` (→ tokens); OTP sent directly via `SmsProvider` (D7); the account is created in the guardian's school (D15); rate-limited | UC-P1, NFR-7 | Tests: wrong phone, used code, expired code, OTP brute force gets 429 |
| 2.10 | Parent API: `/me/children`, `/children/{id}/history`, `/me/notifications`, `POST /notifications/{id}/opened` | UC-P2/P3 | Tests: a parent cannot read another family's child; the opened receipt sets `opened_at` |

### Mobile

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 2.11 | App foundation: Riverpod, dio + JWT and refresh interceptors, secure token storage, ARB FR/EN, theme, staff login, role-based navigation shell | §8 | Widget tests: login, routing per role, both languages render |
| 2.12 | drift database + generic sync queue (device UUIDs, retry with backoff, connectivity-triggered, persisted across restarts, handles 409 and 403 responses) | BR-6, NFR-3 | Unit tests on in-memory drift: an item syncs exactly once; the queue survives an app restart; a 409 marks a conflict |
| 2.13 | Teacher: today's sessions list, cached for offline use | UC-T3 | Widget test: unrecorded sessions highlighted, current period first |
| 2.14 | Teacher: attendance screen (all present by default, tap absentees) with the two-step confirmation and a locked view after it | BR-2–BR-4, UC-T1 | Widget tests for both "Back" paths and for the lock; manual: 60 students, 5 absent, under 60 s on a low-end phone (NFR-1) |
| 2.15 | Teacher: offline recording with a "Waiting to sync" banner | BR-6, UC-T2 | Test with a fake API, offline then online: synced once; manual airplane-mode test |
| 2.16 | Assistant DM: gate search, lateness entry and today's list, offline-first | UC-AD1/AD2 | Widget and sync tests |
| 2.17 | FCM receive + local notifications + device token registration | BR-21 | Manual on a device; unit test for token registration |
| 2.18 | Parent: activation flow (code, phone, OTP) | UC-P1 | Widget tests |
| 2.19 | Parent: notification inbox (sends the opened receipt) and child attendance history | UC-P2/P3, NFR-2 | Widget tests |
| 2.20 | **Phase 2 exit check**: teacher confirms on a device and the parent device receives the push; airplane-mode variant | §12, NFR-5 | Done by hand; outbox timestamps show a send within 1 min of sync |

## Phase 3 — DM and detection

Exit criterion: a full test day produces the correct alerts and one digest per student.

### Backend

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 3.1 | `POST /justifications`: justify one or many absence/lateness records (reason enum + note); only by the DM in charge | BR-10–BR-12, UC-D1 | Tests: a 3-day bulk justification; DM outside scope gets 403; each record is audited |
| 3.2 | `POST /records/{id}/cancel` with a mandatory reason; correction notification if the parent was already notified; cancelled rows excluded from counts | BR-13–BR-15, UC-D6 | Tests: cancel before and after notification; counts exclude cancelled rows; audit holds before/after |
| 3.3 | `threshold_rule` table with pilot defaults seeded per school; `GET/PUT /settings/thresholds` together with school start time and SMS digest times | BR-16, UC-D7 | Tests: only a DM can write; changes are audited |
| 3.4 | Detection engine: one evaluator per rule type (full-day absence D2, consecutive lateness Mon–Fri D3, unjustified periods in the current term; thresholds from the DM settings D9), run on events and in a nightly job; a partial unique index enforces one open alert per student per rule | BR-16, BR-17, BR-19 | Unit tests per rule on a fixed calendar; a concurrency test shows no duplicate open alerts; justifying later does not close an alert |
| 3.5 | Alerts API: `GET /alerts?status=open`, `POST /alerts/{id}/decision` (dismiss with reason, summon, disciplinary, refer) | BR-18, UC-D2 | Tests: only the DM in charge sees and decides; the decision stores who, when and why; detection never writes a decision |
| 3.6 | Summons: `POST /summons` (standalone or from an alert; one tap after a full-day absence), push to app users + SMS to guardians without the app, immediately through the outbox (D12) | BR-23, UC-D3 | Tests: SMS row created immediately, not held for the digest; still sent when the SMS cap is reached |
| 3.7 | `SmsProvider` interface + fake + one real gateway adapter; `POST /webhooks/sms/delivery` (signed) sets `delivered` | BR-24 | Contract test with a recorded gateway response; a webhook with a bad signature is rejected |
| 3.8 | SMS digest job at the configured times: one SMS per student, only to guardians who have no app or did not open the push; only students with new absence/lateness since the previous run (D6); per-school monthly counter and cap | BR-22, BR-25, NFR-5 | Clock-controlled tests: one digest per student; an opened push excludes that guardian; at the cap only summons go out |
| 3.9 | Reporting: `GET /dashboard/school` (attendance trend, alerts, delivery rate, SMS usage) and `/dashboard/scope`; student history for the DM | UC-A6, UC-D8 | Tests: numbers match a fixture; scope is respected |

### Mobile and web

| # | Task | Refs | Acceptance check |
| --- | --- | --- | --- |
| 3.10 | Mobile DM: alert list and decision, justify (multi-select), cancel with reason, summon, student history | UC-D1–D3, D6, D8 | Widget tests for each flow |
| 3.11 | Web DM: alerts, bulk justification, threshold/settings page, scope dashboard | UC-D1, D2, D7, D8 | Component tests; manual walkthrough |
| 3.12 | Web admin: school dashboard with Recharts (trends, delivery rate, SMS counter vs cap) | UC-A6, BR-25 | Component test with fixture data |
| 3.13 | **Phase 3 exit check**: a "full test day" simulation test (fake school, scripted sessions and lateness across a day and a week) asserting the exact alerts raised and exactly one digest per student | §12 | Automated test green |
| 3.14 | Load test: 2 schools × 40 classes × 8 periods synced within 10 minutes | §11 | The k6/Gatling script finishes with no errors or duplicates and the outbox drains within NFR-5 |

## Decisions (answered 2026-10-28)

| # | Topic | Decision |
| --- | --- | --- |
| D1 | Timetable | Add a `timetable_slot` table (class, weekday, period, teacher, subject), entered by the admin. It drives `GET /me/sessions` and UC-T3. |
| D2 | Parent notification vs DM alert | **Parents** are notified for **each absent period** as soon as the session is locked (P1 absent → notification, P2 absent → another one; no absence → nothing sent) (BR-20, BR-21). **DM alerts** are separate and come from the threshold rules (D9). The full-day absence alert is raised when the student is absent in **every timetabled period of the day**: checked after each locked session (so it fires when the last period is locked) and again in the nightly job, which counts recorded sessions only. The DM can then summon the parents in one tap. |
| D3 | School days | **Monday to Friday**, minus the holidays the admin enters per term (D10). "Consecutive lateness" counts school days only; a school day with no lateness resets the streak. |
| D4 | Disciplinary and refer decisions in the MVP | Store the decision and create a minimal `disciplinary_record` / `counselling_case` row (status `open`); their screens come in wave 2. |
| D5 | DM scope | A **block** is a named group of classes, with **one DM per block** (assistant DMs also assigned per block). Add a `block` table; `role_assignment.scope_type = BLOCK`. The DM "in charge" of a student is the DM of the block holding the student's class. |
| D6 | SMS content | A digest SMS goes to a guardian **only when the student was marked absent or late**, never an empty digest. The 13:00 run covers the morning; the 17:00 run covers only new absence/lateness since 13:00. |
| D7 | Parent accounts and channels | **Every guardian** can receive an invitation code **by SMS** (D13). A guardian who activates it in the app (UC-P1) gets an app account and receives **push** notifications; a guardian without an account receives **SMS**. The channel follows from whether the guardian has an account, so no `sms_only` flag is stored (V3). |
| D8 | Mobile identity | Package `schoollink`, applicationId `cm.schoollink.app`; remove `ios/ linux/ macos/ web/ windows/`. **Keep the animated Lottie splash.** |
| D9 | Alert rules | Three rule types, **each configured by the DM** (count, unit, window, on/off) in UC-D7; the values below are only the seeded starting values: 1 day of absence, 5 lateness, 15 unjustified absences. Rules only raise alerts for the DM; the DM decides (summon, dismiss, discipline, refer). |
| D10 | Holidays | The admin configures holidays per term (UC-A1). |
| D11 | Corrections | **Only the DM** cancels a record and triggers a correction (BR-13, BR-14). The correction goes out on the same channel as the original notification. |
| D12 | Summons channels | Summons go out immediately (BR-23): push to app users, **SMS to guardians without the app**. Summons SMS is never held for the digest and is still sent when the monthly cap is reached (BR-25). |
| D13 | Activation code delivery | Invitation codes are sent **by SMS** through the outbox (event type `INVITATION`) to guardians who have no account yet. No printed sheet in the MVP. |
| D14 | Backend stack | **Spring Boot 4** (as generated, 4.1.2) with Java 21; folder `backend/`, base package `com.schoollink`. |
| D15 | Identity per school | A phone number or an email identifies **one user within one school**; the same parent can have an account in each pilot school (V3). Phone numbers: Cameroon E.164 only (+237 + 9 digits), foreign numbers rejected. |
| D16 | School at login | Login requests include the school. The app and web show a **school picker**, remembered on the device; it is skipped when the phone or email exists in only one school. |
| D17 | 403 or 404 (NFR-8) | A role with no access to a kind of data gets **403**. A record in another school or outside the user's scope gets **404**, so its existence does not leak. Roles, language and scope are read from the database on every request; the token only identifies the user and school. |
| D18 | First staff password | The admin never chooses a staff password. Creating an account (or resetting its password) generates a 12-character **temporary password**, shown once to the admin and never stored in clear or audited. Until the user replaces it, only `GET /me` and `POST /me/password` work (403 `auth.password_change_required`). Changing the password, a reset, or disabling the account ends every other session at once: refresh tokens are revoked, and access tokens carry a credentials version (`cv`) that no longer matches. |
| D19 | Import commit | A previewed import is committed **all or nothing**: only when it has no errors, and only once. If the school changed since the preview (a matricule now exists, a class was removed), the commit is refused and the admin uploads the file again. Guardians are matched by phone within the school (D15): siblings share one guardian, and an existing guardian is linked without being overwritten. After the commit the staged rows are deleted; a preview never committed **expires after 7 days** (nightly job) and its rows are deleted (NFR-9). |
| D20 | Locked records in the database | The lock (BR-4) is enforced by PostgreSQL triggers, like the append-only audit log: a session never changes and is never deleted; on an attendance or lateness record only the justification may change and the DM's cancellation is set once and never undone (BR-13, BR-15). Lateness is unique per student and day among **live** records: a cancelled entry no longer counts, so the student can be recorded again that day (V11). |

Questions not answered keep their recommended defaults: DM can cancel a whole session; lateness time defaults to the device time, today only; a cancellation before the digest removes the item and sends no correction SMS; fake SMS provider until one is chosen; guardian language from import, falling back to the school's language; a CSV template from us with upsert by matricule; E.164 (+237); DM features split mobile/web as proposed; store both device `confirmed_at` and server `received_at`; the activation OTP SMS is sent directly via `SmsProvider`, counted but never blocked by the cap.
