# Handoff — state of the project on 27 November 2026

Read this first if you are taking over SchoolLink. It says what is done, what is half done, what is blocked and exactly what to do next. [CLAUDE.md](../CLAUDE.md) has the project rules and commands; [SPEC.md](SPEC.md) and [PLAN.md](PLAN.md) (decisions D1–D51) are the source of truth. Delete or update this file when the work below is finished.

## 1. Where things stand

- Phases 1–3 are done and passed their exit checks; the features added to the pilot (D36–D46, D49) are done. Wave 2 (counsellor screens UC-C1–C3, research view and export UC-R1–R3) is **not built**.
- Tests on 27/11: mobile 246 pass and `flutter analyze` is clean; web 142 pass, lint and `tsc` clean; backend ~1,400 tests passed on 26/11 but **could not be run on 27/11** (Docker broken, see §3).
- Everything up to `339cfec` was **pushed to GitHub on 27/11**. That push started CI (`.github/workflows/ci.yml`) for the first time: check the run on the repository's Actions tab and fix whatever fails (it had never run before). Only the owner decides when to push.
- `backend/.run/SchoolLink (dev).run.xml` has a local change that must **never be committed**.
- Commit `3b0ebe0` cites NFR-12 by mistake (it is a test-timing fix). Harmless; leave it.

## 2. Work to do, in order

The owner chose these on 27/11 after a senior review of the project (summary in §5). Each item follows TDD and the CLAUDE.md rules (cite BR/UC/NFR/D numbers, FR+EN for any text, integration test per role for any endpoint, commit after each verified task).

### 2.1 Single-instance guard for scheduled jobs — needs Docker

Problem: nothing stops two backend instances running the jobs twice (double SMS digests, double detection). Jobs: `notification/internal/OutboxPoller` (every 5 s), `notification/internal/SmsDigestJob` (every minute), `detection/internal/NightlyDetection` (hourly at :15), `people/internal/StudentImportCommitter.expireOldPreviews` (02:30 Africa/Douala), plus the purge of §2.2. Scheduling is enabled in `shared/time/SchedulingConfig`.

Proposed: ShedLock with the JDBC provider (table `shedlock` in migration `V35__…`), `@SchedulerLock` on each job with `lockAtMostFor` above the job's worst duration. **Check that the ShedLock version supports Spring Framework 7 / Boot 4** before adding it; if none does, use a PostgreSQL advisory lock (`pg_try_advisory_xact_lock`) in a small `shared` helper instead. Test: two job invocations in parallel, the second does nothing. Log it as decision D52 in PLAN.md.

Note: the login rate limiter (`shared/ratelimit/RateLimiter`) is in memory and resets on restart. Known and documented in its Javadoc; acceptable for one instance. Mention it in D52, do not change it unless asked.

### 2.2 Data-retention purge (D51) — needs Docker

Read D51 in PLAN.md. Nightly job, per school: delete the rows of academic years that ended more than one year ago (attendance sessions and records, lateness, justifications, risk alerts, summons, disciplinary records, counselling cases, parent messages, notification outbox rows, SMS digest runs); keep `audit_log` rows but set their before/after values to null for the deleted entities; write one audit row per school and run.

The hard part: PostgreSQL triggers forbid these changes on purpose (D20). Guard functions: `attendance_reject_change`, `attendance_record_guard` (V11), `risk_alert_guard` (V22), `summons_guard` (V23), `disciplinary_record_guard` (V28), `parent_message_guard` (V29), `audit_log_reject_change` (V4). Proposed design: in a new migration, let each guard allow the change only when the transaction sets `SET LOCAL schoollink.purge = 'on'` (`current_setting('schoollink.purge', true) = 'on'`), and for `audit_log` only an update that nulls before/after and changes nothing else. Only the purge job sets it. Tests must prove: the purge deletes old years and nothing of the current or previous year; the guards still refuse everything without the setting; audit rows remain with who/what/when. Do it in batches to keep transactions short.

### 2.3 Crash reports with Sentry (D50) — mobile part can start now

Read D50. App: `sentry_flutter`, enabled only if `--dart-define=SENTRY_DSN=…` is given; a `beforeSend` that drops request bodies, query strings, user names, phones and e-mails; keep error, stack trace, route, app version and role. Check the release APK stays under 30 MB (NFR-4, checked in CI). Backend: `sentry-spring-boot` starter (check Boot 4 support), DSN from env var `SENTRY_DSN`, same scrubbing, no PII (`send-default-pii=false`). Add `SENTRY_DSN` to `deploy/.env.example` and DEPLOYMENT.md. The owner has **no Sentry account yet**; everything must work with no DSN. Add one line naming Sentry to the parent consent notice text when it is written.

### 2.4 Research export (UC-R1–R3) — design conversation first

Not started. Use the brainstorming process before any code. The open question put to the owner (unanswered): *what will they do with the exported data — which tables or figures their results chapter needs, and in which tool (Excel, SPSS, R, Python)?* The answer drives the datasets. Fixed rules already in SPEC §11 (random IDs, no names/phones/matricules, dates as week numbers, groups under 5 suppressed, every export audited). The evaluation measures are SPEC §11 / documentation chapter 14. This is the item the academic evaluation depends on; it matters more than new features.

### 2.5 Other findings from the review, not yet scheduled

No metrics on the server (no Micrometer/Prometheus) and only an uptime ping; no OpenAPI contract (API.md is hand-written); deployment is `git pull` + rebuild on the server, no versioned images; no end-to-end test of real app ↔ real backend; the load test does not model the 8 a.m. offline-sync burst on 3G; a few UI files over 550 lines (`mobile/lib/features/dm/day/day_screen.dart`, `teacher/attendance_screen.dart`, `web/src/pages/students/dialogs.tsx`, `web/src/pages/dm/dialogs.tsx`); Twilio is the costliest SMS route for Cameroon (a local gateway only needs a new `SmsProvider`); untracked clutter `my_flutter_app/` and `.playwright-mcp/` at the repo root (ask the owner before deleting).

## 3. Environment on the owner's PC (Windows 11)

- **Docker Desktop was broken on 27/11**: its internal disk turned read-only (`read-only file system`, `input/output error` on image pulls). Testcontainers cannot start, so `./mvnw verify` fails with `Can't get Docker image: testcontainers/ryuk`. Fix: restart Docker Desktop; if it persists, Troubleshoot → Clean/Purge data (the dev database volume would be lost; it only holds fake data). C: had 38 GB free, so it is not disk space.
- Dev database: `docker compose up -d db` (port 5433). Backend: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (port 8080, two fake schools, every demo account's password `schoollink-dev`, e.g. `sg1@lycee-demo-a.test` DM, `prof1@…` teacher, `sga1@…` assistant DM). Web: `cd web && npm run dev`.
- Android emulator `emulator-5554`; the dev APK calls 127.0.0.1, so run `adb -s emulator-5554 reverse tcp:8080 tcp:8080` after each emulator start.
- Demo parent "Adam Leroux" (fake) has password `Demo-parent-2026` and has used 1 of his 3 password resets this school year.
- Shells: Git Bash and PowerShell 5.1. `git rebase -i` is not available to agents.

## 4. The full documentation dossier

Two Word documents (FR 51 pages, EN 48 pages, with PDFs) describing the whole project, built on 27/11: `C:\Users\fopab\Documents\SchoolLink-Documentation\`. Not in the repository. To change and rebuild them, the sources are in its `sources\` folder: edit `fr.md` / `en.md`, then `node build.js fr.md SchoolLink-Documentation-FR.docx fr` (needs `npm install docx` in that folder), then `powershell -File finish.ps1 -Docx <full path>` to fill the table of contents and export the PDF with Word. `sources\README.md` explains the source syntax, the diagrams (`sources\diagrams\diagrams.html` draws them) and the screenshots (`sources\shots`).

## 5. Review summary (27/11)

Strengths to keep: modular monolith with module boundaries checked by Spring Modulith, transactional outbox, idempotent offline sync by device UUID, invariants enforced by PostgreSQL triggers, 403/404 scope design, secrets required outside dev/test. Weaknesses, most important first: no CI (now added, never run), no production observability, research export missing, spec drift (now fixed and a rule added), no single-instance guard, retention not implemented, scope grown before real users tried the core loop.
