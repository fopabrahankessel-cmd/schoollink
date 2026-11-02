# Full-Day Absence Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A DM generates, previews and shares from the Android app a PDF of the students of his blocks absent the whole day, under the school letterhead that the admin sets on the web dashboard.

**Architecture:** The backend `reporting` module serves the day's listing as JSON (scope from the JWT, audited as an export); the `school` module stores the letterhead and logo. The Flutter app draws the PDF from the JSON with the `pdf` package and previews and shares it with `printing`. The web admin edits the letterhead with a live preview.

**Tech Stack:** Spring Boot 4 / JDBC / Flyway / Testcontainers; Flutter, Riverpod, dio, `pdf` + `printing`; React + MUI + i18next + Vitest.

**Spec:** `docs/superpowers/specs/2026-11-02-full-day-absence-report-design.md` (read it with this plan).

## Global Constraints

- Every query filtered by `school_id` and scope from the JWT principal (`CurrentUser`), never from the request (NFR-8). Each endpoint: an integration test per role, allowed and forbidden.
- Report: DM only, classes = `user.classesAs(Role.DM)` (all his blocks). Letterhead read: ADMIN and DM; write: ADMIN only.
- Listed rule: for every period in `SchoolCalendar.periodsOf(school, class, date)` (non-empty), a session exists and the student's record is ABSENT, not cancelled, not justified.
- A class with any timetabled period not recorded lists nobody and goes in `unrecorded`.
- Dates: school days only (`SchoolCalendar.isSchoolDay`), never in the future; today only from 17:00 school time (`SchoolCalendar.zone`). Default date: today from 17:00, else the previous school day.
- No PDF when nobody is listed: the app shows the message instead.
- Report generation writes `audit_log` EXPORT, entity `full_day_report`, entityId the ISO date, `after` = `{"date", "students", "classes"}`, no names (NFR-11). Letterhead and logo changes write UPDATE, entity `school_letterhead`.
- Letterhead limits: at most 12 lines per language, each line 1–100 characters; sizes `SMALL | NORMAL | LARGE`; layout `BILINGUAL | FRENCH | ENGLISH`; logo PNG or JPEG checked by magic bytes, at most 1 048 576 bytes.
- Not configured: the PDF header is the school name, centred, no logo.
- Every user-facing string in FR and EN, in the same change (mobile ARB, web i18n JSON, backend `messages_*.properties`) (NFR-12).
- No phone numbers in the report, URLs or logs (NFR-9). Generated data only in tests.
- Commit after each task whose checks pass; messages cite UC-D9 / UC-A7 / D2 / D36 / NFR numbers and end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. A student absent in every period but one of those absences cancelled (BR-15) or justified must not be listed — Task 2 test `cancelledOrJustifiedAbsenceKeepsAStudentOff`.
2. Asking for today before 17:00, a weekend, a holiday or a future date must give a clear localized 400, not an empty report — Task 2 tests `refusesDaysThatAreNotFinishedOrNotSchoolDays`.
3. A long report (60+ students, accented names like "Ébodé Chloé") must paginate with repeated table headings and render accents — Task 6 test `longReportWithAccentsPaginates`.
4. A file named `.png` that is not a PNG, or a 1.5 MB logo, must be refused with a localized message — Task 3 test `refusesLogosThatAreNotPngOrJpegOrTooLarge`.
5. A DM with two blocks gets both, grouped by block; a DM never sees another block's classes — Task 2 test `aDmWithTwoBlocksGetsBothAndNothingElse`.

---

### Task 1: Spec, plan and the module read APIs the report needs

**Files:**
- Modify: `docs/SPEC.md` (UC table: add UC-D9 and UC-A7 as MVP "Yes"; endpoint table: the five endpoints of Tasks 2–3), `docs/PLAN.md` (D2: "every **timetabled** period; a lesson never recorded means the day is not full" — drop "counts recorded sessions only"; add decision D36 = design summary; add row 3.15 pointing to this plan)
- Modify: `backend/src/main/java/com/schoollink/attendance/AttendanceHistory.java`, `.../attendance/internal/JdbcAttendanceHistory.java`
- Modify: `backend/src/main/java/com/schoollink/school/SchoolClasses.java`, `.../school/internal/JdbcSchoolClasses.java`
- Modify: `backend/src/main/java/com/schoollink/people/UserAccounts.java` and its JDBC implementation
- Test: the existing test classes of these implementations (find with `grep -rl "JdbcAttendanceHistory\|AttendanceHistory" backend/src/test`), or new `AttendanceDayTests`, `SchoolClassesTests` in the same packages

**Interfaces:**
- Produces:
  - `AttendanceHistory.Day day(UUID schoolId, Set<UUID> classIds, LocalDate date)` with
    `record Day(Map<UUID, Set<UUID>> recordedPeriods /* classId -> periodIds with a session */, List<DayAbsence> absences)` and
    `record DayAbsence(UUID studentId, UUID classId /* the session's class */, UUID periodId, boolean justified)`; cancelled records are left out (BR-15).
  - `SchoolClasses.Map<UUID, UUID> blockOf(UUID schoolId, Set<UUID> classIds)` (classId -> blockId) and `Map<UUID, String> blockNames(UUID schoolId, Set<UUID> blockIds)`.
  - `UserAccounts.Optional<String> fullName(UUID schoolId, UUID userId)` → "First Last".

- [ ] **Step 1: Update SPEC.md and PLAN.md as listed above.**
- [ ] **Step 2: Write failing tests**
  - `dayListsRecordedPeriodsAndLiveAbsencesOfTheClasses`: two sessions of classA on Monday (period1 with studentA ABSENT justified, a second period with studentA ABSENT), one cancelled ABSENT record of another student, a session of classC (not asked) → `recordedPeriods == {classA: {p1, p2}}`, absences = the two of studentA with `justified` true/false, cancelled one absent, classC absent.
  - `blockOfAndBlockNamesStayInTheSchool`: classA→block1; names of {block1, a block of schoolY} → only block1's name.
  - `fullNameOfAUserOfAnotherSchoolIsEmpty`.
- [ ] **Step 3: Run** `cd backend; ./mvnw test -Dtest=<those classes>` — expect FAIL (methods missing).
- [ ] **Step 4: Implement** the three methods with `NamedParameterJdbcTemplate`, each query `WHERE school_id = :school`; return empty collections for empty `classIds`.
- [ ] **Step 5: Run** the same tests and `ModularityTests` — expect PASS.
- [ ] **Step 6: Commit** `UC-D9, D2, D36: spec and the read APIs of the full-day absence report`.

---

### Task 2: Report endpoint (reporting module)

**Files:**
- Create: `backend/src/main/java/com/schoollink/reporting/internal/FullDayReports.java` (service), `.../reporting/internal/FullDayReportController.java`
- Modify: `backend/src/main/resources/messages.properties`, `messages_fr.properties`, `messages_en.properties`
- Test: `backend/src/test/java/com/schoollink/reporting/internal/FullDayReportTests.java`

**Interfaces:**
- Consumes: Task 1 APIs; `SchoolCalendar.periodsOf/isSchoolDay/zone/period`; `Students.cards`; `SchoolClasses.names`; `AuditLog.record`; `Clock`.
- Produces (JSON field names are the contract for Task 5):
  - `GET /api/v1/reports/full-day-absences/default-date` → `{"date": "2026-11-03"}`
  - `GET /api/v1/reports/full-day-absences?date=YYYY-MM-DD` →
    `FullDayReport(LocalDate date, String generatedBy, int studentCount, int classCount, List<BlockSection> blocks, List<Unrecorded> unrecorded)`;
    `BlockSection(UUID blockId, String blockName, List<ClassSection> classes)`;
    `ClassSection(UUID classId, String className, List<ReportStudent> students)`;
    `ReportStudent(UUID id, String lastName, String firstName, String matricule)`;
    `Unrecorded(UUID classId, String className, List<Integer> periods)`.
    Only blocks and classes with listed students appear; blocks by name, classes by name, students by last then first name (case-insensitive), `unrecorded` by class name with period numbers ascending.
  - Error codes (400): `report.day_not_finished`, `report.not_a_school_day`, `report.date_in_future`; non-DM: 403 via `ApiException.forbidden()`.

- [ ] **Step 1: Write the failing tests** (TestWorld, `@Transactional` test transaction; clock `TestClock` at Tue 2026-11-03 18:00 Douala unless stated). Build a two-period Monday for classA (period1 and a second period you insert):
  - `listsStudentsAbsentInEveryTimetabledPeriod`: studentA absent in both → listed under block1 / classA; `studentCount == 1`, `classCount == 1`; another student absent in one period only → not listed.
  - `cancelledOrJustifiedAbsenceKeepsAStudentOff`: one of studentA's two absences cancelled → not listed; same with one justified → not listed.
  - `aClassWithALessonNotRecordedListsNobodyAndIsNamed`: only period1 recorded → `blocks` empty, `unrecorded == [{classA, "…", [2]}]`.
  - `refusesDaysThatAreNotFinishedOrNotSchoolDays`: clock Tue 16:59, date Tue → 400 `report.day_not_finished` with the FR message; Saturday → `report.not_a_school_day`; Wednesday → `report.date_in_future`.
  - `defaultDateIsTodayFrom17hElseThePreviousSchoolDay`: Tue 16:59 → Mon; Tue 17:00 → Tue; Mon 08:00 → previous Friday.
  - `aDmWithTwoBlocksGetsBothAndNothingElse`: give DM_1 block2 as well; students absent all day in classA and classC → two block sections; DM_2's view of the same day has only its block.
  - `generatingIsAuditedWithoutNames`: one `audit_log` row EXPORT `full_day_report`, entityId `2026-11-02`, after `{"date":"2026-11-02","students":1,"classes":1}`, no student name in it.
  - `onlyTheDmMayAsk`: ADMIN_X, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A → 403; ANONYMOUS → 401; DM_1 → 200 (both endpoints).
  - `theReportAgreesWithTheFullDayAlert` (non-transactional, `world.remove(transactions)` in `@AfterEach`, imports `DetectionDriver.Config`): same Monday data, unjustified → the listed students equal the students with a FULL_DAY_ABSENCE alert after `detection.nightly(schoolX, monday)`.
- [ ] **Step 2: Run** `./mvnw test -Dtest=FullDayReportTests` — expect FAIL (404 on the endpoints).
- [ ] **Step 3: Implement** `FullDayReports.report(CurrentUser user, LocalDate date)` (`@Transactional`, audits) and `FullDayReports.defaultDate(CurrentUser user)` (read-only); controller maps both endpoints. Date checks in this order: future → `date_in_future`; not a school day → `not_a_school_day`; today before 17:00 → `day_not_finished`. Default date: walk back from yesterday at most 60 days to the first school day; if none, 404 `not_found`. Add the three message keys in FR and EN (e.g. FR "La journée n'est pas terminée : le rapport du jour est disponible à partir de 17 h.", EN "The day is not over: today's report is available from 5 PM.").
- [ ] **Step 4: Run** `./mvnw test -Dtest=FullDayReportTests,ModularityTests,MessageBundleTests` — expect PASS.
- [ ] **Step 5: Commit** `UC-D9, D2, NFR-8, NFR-11: full-day absence report endpoint`.

---

### Task 3: Letterhead storage and endpoints (school module)

**Files:**
- Create: `backend/src/main/resources/db/migration/V26__school_letterhead.sql`
- Create: `backend/src/main/java/com/schoollink/school/internal/Letterheads.java` (service + JDBC), `.../school/internal/LetterheadController.java`
- Modify: the three `messages*.properties`
- Test: `backend/src/test/java/com/schoollink/school/internal/LetterheadTests.java`

**Interfaces:**
- Table `school_letterhead(school_id uuid PK REFERENCES school, layout varchar(9) NOT NULL CHECK (layout IN ('BILINGUAL','FRENCH','ENGLISH')), french jsonb NOT NULL, english jsonb NOT NULL, logo bytea, logo_type varchar(10) CHECK (logo_type IN ('image/png','image/jpeg')), CHECK ((logo IS NULL) = (logo_type IS NULL)), updated_at timestamptz NOT NULL, updated_by uuid NOT NULL, FK (school_id, updated_by) → app_user)`.
- Produces (contract for Tasks 4 and 5):
  - `GET /api/v1/school/letterhead` (ADMIN, DM) → `Letterhead(String schoolName, boolean configured, String layout, List<Line> french, List<Line> english, boolean hasLogo, String logoVersion)`, `Line(String text, boolean bold, String size)`. Not configured: `configured=false`, `layout="BILINGUAL"`, empty lists, `hasLogo=false`, `logoVersion=null`. `logoVersion` = `updated_at` epoch millis of the last logo change, as a string (cache key).
  - `PUT /api/v1/school/letterhead` (ADMIN) body `{layout, french, english}` → `Letterhead`.
  - `GET /api/v1/school/letterhead/logo` (ADMIN, DM) → bytes with `Content-Type` of the stored type; 404 `not_found` when none.
  - `PUT /api/v1/school/letterhead/logo` (ADMIN) multipart field `file` → `Letterhead`; `DELETE /api/v1/school/letterhead/logo` (ADMIN) → `Letterhead`.
  - Error codes (400): `letterhead.too_many_lines` (>12), `validation.size` for line text (1–100), `letterhead.logo_type`, `letterhead.logo_too_large`. Setting the logo before any text creates the row with `BILINGUAL` and empty lists. `configured` = at least one line or a logo; when it is false the PDF header is the school name only, and when there is a logo but no lines the PDF shows the logo with the school name under it.

- [ ] **Step 1: Write the failing tests**: `anUnsetLetterheadIsTheSchoolNameOnly`; `theAdminSavesLinesAndLayoutAndTheDmReadsThem` (order, bold, size kept); `refusesTooManyOrTooLongLines`; `refusesLogosThatAreNotPngOrJpegOrTooLarge` (bytes `"not a png"` named `x.png` → `letterhead.logo_type`; 1 048 577 bytes of a PNG header → `letterhead.logo_too_large`); `aLogoIsServedWithItsTypeAndCanBeRemoved`; `changesAreAudited` (UPDATE rows, logo `after` holds `{type, bytes}` not the image); `rolesAndSchools` (per role allowed/forbidden for each endpoint; ADMIN_Y never sees schoolX's letterhead: reads its own).
- [ ] **Step 2: Run** `./mvnw test -Dtest=LetterheadTests` — expect FAIL.
- [ ] **Step 3: Implement** migration, store, controller; PNG magic `89 50 4E 47 0D 0A 1A 0A`, JPEG `FF D8 FF`. Messages in FR and EN.
- [ ] **Step 4: Run** `./mvnw test -Dtest=LetterheadTests,ModularityTests,MessageBundleTests` — expect PASS.
- [ ] **Step 5: Commit** `UC-A7, NFR-8, NFR-11: school letterhead and logo`.

---

### Task 4: Web — admin letterhead page

**Files:**
- Create: `web/src/api/letterhead.ts`, `web/src/pages/structure/LetterheadPage.tsx`, `web/src/pages/structure/LetterheadPreview.tsx`, `web/src/pages/structure/letterhead.test.tsx`
- Modify: `web/src/routes.tsx` (`/structure/letterhead`, `admin(...)`), `web/src/components/AppLayout.tsx` (ADMIN_NAV group `nav.school`: item `nav.letterhead`), `web/src/icons.ts` (one phosphor icon, e.g. `IdentificationCardIcon`), `web/src/i18n/en.json`, `fr.json` (`nav.letterhead`, `letterhead.*`)

**Interfaces:**
- Consumes: Task 3 endpoints.
- Produces: `LETTERHEAD_PATH = '/school/letterhead'`; types `Letterhead`, `LetterheadLine`, `LetterheadLayout`; `saveLetterhead(client, body)`, `uploadLogo(client, file: File)`, `removeLogo(client)`, `fetchLogo(client): Promise<Blob | null>` (authenticated fetch, shown via `URL.createObjectURL`).

- [ ] **Step 1: Write the failing tests** (`fakeFetch`/`renderApp`/`tokens`):
  - `the admin edits lines, moves one up, makes one bold and saves`: PUT body equals the expected `{layout, french, english}`.
  - `the preview shows French left, logo centre, English right, and one centred column for a one-language layout`.
  - `uploading a logo sends multipart "file" and shows the server refusal for a wrong type` (message from the 400 body).
  - `the DM has no letterhead page` (redirected; no nav link).
  - `every letterhead key exists in both languages` is covered by the existing i18n parity test — run it.
- [ ] **Step 2: Run** `cd web; npm test -- letterhead` — expect FAIL.
- [ ] **Step 3: Implement** the page: layout radio (both / French only / English only); per language a list of rows (text field, bold toggle, size select small/normal/large, move up/down, delete), "add a line" disabled at 12; logo upload (accept `image/png,image/jpeg`, client check ≤ 1 MB with the same message as the server) and remove; preview component using the same proportions as the PDF (three columns, logo ~3 cm equivalent); Save button with success snackbar.
- [ ] **Step 4: Run** `npm run lint; npm test; npm run build` — expect all green.
- [ ] **Step 5: Commit** `UC-A7, NFR-12: web letterhead editor with preview`.

---

### Task 5: Mobile — report and letterhead data

**Files:**
- Create: `mobile/lib/features/dm/full_day/full_day_models.dart`
- Modify: `mobile/lib/features/dm/dm.dart` (`DmRepository`)
- Test: `mobile/test/features/full_day_repository_test.dart` (uses `test/support/fake_server.dart`)

**Interfaces:**
- Consumes: Task 2 and 3 JSON (field names exactly as listed there).
- Produces: `FullDayReport` (`date: DateTime`, `generatedBy`, `studentCount`, `classCount`, `blocks: List<ReportBlock>`, `unrecorded: List<UnrecordedClass>`, `bool get isEmpty => blocks.isEmpty`), `ReportBlock(name, classes)`, `ReportClass(name, students)`, `ReportStudent(lastName, firstName, matricule)`, `UnrecordedClass(className, periods: List<int>)`, `Letterhead` (`schoolName`, `configured`, `layout` enum `bilingual|french|english`, `french`, `english: List<LetterheadLine>`, `hasLogo`), `LetterheadLine(text, bold, size` enum `small|normal|large)`; `DmRepository.defaultReportDate(): Future<DateTime>`, `fullDayReport(DateTime date): Future<FullDayReport>`, `letterhead(): Future<Letterhead>`, `letterheadLogo(): Future<Uint8List?>` (null on 404).

- [ ] **Step 1: Write the failing tests**: parsing a report with two blocks; an empty report (`isEmpty`); `fullDayReport` sends `date=2026-11-03`; a 400 `report.day_not_finished` surfaces as the existing `ApiFailure` with the server message; logo 404 → null.
- [ ] **Step 2: Run** `cd mobile; flutter test test/features/full_day_repository_test.dart` — expect FAIL.
- [ ] **Step 3: Implement** models (`fromJson`) and the four repository methods through the existing `_call`.
- [ ] **Step 4: Run** the test and `flutter analyze` — expect PASS / no issues.
- [ ] **Step 5: Commit** `UC-D9, UC-A7: mobile report and letterhead data`.

---

### Task 6: Mobile — the PDF

**Files:**
- Modify: `mobile/pubspec.yaml` (`flutter pub add pdf printing`; assets `assets/fonts/NotoSans-Regular.ttf`, `assets/fonts/NotoSans-Bold.ttf` downloaded from Google Fonts, OFL licence file `assets/fonts/OFL.txt`)
- Create: `mobile/lib/features/dm/full_day/report_document.dart`
- Test: `mobile/test/features/report_document_test.dart`

**Interfaces:**
- Consumes: Task 5 models; `AppLocalizations` (the document keys are added in Step 1 below; the screen keys come in Task 7).
- Produces: `ReportTexts` (all strings of the document, built from `AppLocalizations` and the locale: `title`, `date` like "mardi 3 novembre 2026" / "Tuesday 3 November 2026", `summary`, column headings, `unrecorded`, `footer`); `Future<Uint8List> buildReportPdf({required FullDayReport report, required Letterhead letterhead, Uint8List? logo, required ReportTexts texts, required DateTime generatedAt})`; `String reportFileName(DateTime date)` → `absences-2026-11-03.pdf`.

- [ ] **Step 1: Add the ARB keys** used by the document, FR and EN (FR: `reportTitle` "Élèves absents toute la journée"; `reportSummary` "{students, plural, =1{1 élève} other{{students} élèves}} dans {classes, plural, =1{1 classe} other{{classes} classes}}"; `reportColumnNumber` "N°", `reportColumnName` "Nom et prénom", `reportColumnMatricule` "Matricule"; `reportUnrecorded` "Heures non saisies, les absences de toute la journée y sont donc inconnues : {list}"; `reportFooter` "Généré par SchoolLink le {date} à {time}"; `reportPage` "Page {page}/{pages}"; EN equivalents: "Students absent the whole day", "{students} students in {classes} classes" (plural forms), "N°", "Name", "Matricule", "Lessons not recorded, so full-day absences there cannot be known: {list}", "Generated by SchoolLink on {date} at {time}", "Page {page}/{pages}"). Run `flutter gen-l10n`.
- [ ] **Step 2: Write the failing tests**:
  - `buildsAPdf`: output starts with `%PDF`.
  - `longReportWithAccentsPaginates`: 70 students, names with "É", "ç", "ï" → builds without error and `pages >= 2` (count with `pdf` `Document` page list exposed by an `@visibleForTesting` helper `buildReportDocument(...)` returning the `pw.Document` before saving).
  - `unconfiguredLetterheadIsTheSchoolNameOnly` and `oneLanguageLayoutHasOneColumn` (inspect the header widget model via `@visibleForTesting headerColumns(letterhead) → List<List<LetterheadLine>>`).
  - `fileNameCarriesTheDate`.
- [ ] **Step 3: Run** `flutter test test/features/report_document_test.dart` — expect FAIL.
- [ ] **Step 4: Implement** with `pw.MultiPage` (A4 portrait, 15 mm margins), header (French column, logo `pw.MemoryImage` max 30 mm high, English column; text sizes small 7 pt / normal 8.5 pt / large 10 pt), rule, title, date 20 pt bold, summary (block names joined by ", " — dm name), per block a sub-heading only when there are 2+ blocks, per class a heading "{class} — n" and a `pw.TableHelper`-style table with repeated header rows, the unrecorded line after the tables, footer with generated time and page x/y. Fonts from the bundled Noto Sans via `pw.ThemeData.withFont`.
- [ ] **Step 5: Run** the test, `flutter analyze` — expect PASS. Check `flutter build apk --release --split-per-abi` stays under 30 MB per APK (NFR-4).
- [ ] **Step 6: Commit** `UC-D9, NFR-4, NFR-12: full-day absence PDF`.

---

### Task 7: Mobile — the screen, preview and share

**Files:**
- Create: `mobile/lib/features/dm/full_day/full_day_screen.dart`
- Modify: `mobile/lib/router.dart` (route `full-day` under `/home/:tab`), `mobile/lib/features/dm/dm_home_screen.dart` (an entry "Full-day absences" next to the student search entry, `context.push('/home/alerts/full-day')`), `mobile/lib/l10n/app_fr.arb`, `app_en.arb`
- Test: `mobile/test/features/full_day_screen_test.dart` (harness + fake server; `printing` platform calls replaced by an injectable `ReportSharer` provider)

**Interfaces:**
- Consumes: Task 5 repository, Task 6 `buildReportPdf`, `reportFileName`, `ReportTexts`.
- Produces: `fullDayReportProvider`, `abstract class ReportSharer { Future<void> share(Uint8List bytes, String fileName); }` with the default implementation calling `Printing.sharePdf(bytes: bytes, filename: fileName)`.

- [ ] **Step 1: Add ARB keys** FR/EN: `fullDayEntry` "Absents toute la journée" / "Full-day absences"; `fullDayGenerate` "Générer le document" / "Generate the document"; `fullDayShare` "Partager" / "Share"; `fullDayNobody` "Aucun élève n'a été absent à toutes les heures de cours le {date}." / "No student was absent from all the school periods on {date}."; `fullDayPickDay` "Jour" / "Day".
- [ ] **Step 2: Write the failing widget tests**:
  - `opensOnTheDefaultDay`: fake default-date `2026-11-02` → the date field shows "lundi 2 novembre 2026" (FR).
  - `nobodyAbsentShowsTheMessageAndNoPdf`: empty report with `unrecorded` [4e B, [5]] → message `fullDayNobody` and the unrecorded line; no Share button; `ReportSharer` never called.
  - `generatesPreviewsAndShares`: a report with students → preview shown (`PdfPreview` present) and Share calls the sharer once with `absences-2026-11-02.pdf` and bytes starting `%PDF`.
  - `theDatePickerRefusesFutureDays` (`lastDate` = today) and a server 400 shows its message with a retry.
  - `theEntryIsOnlyOnTheDmHome` (teacher home has no entry).
- [ ] **Step 3: Run** `flutter test test/features/full_day_screen_test.dart` — expect FAIL.
- [ ] **Step 4: Implement** the screen: date field (Material date picker, `lastDate` today, `firstDate` 1 Sep of the previous year), Generate → fetch report; if empty show the message (+ unrecorded line); else fetch letterhead and logo (logo cached per `logoVersion` in memory), build the PDF, show `PdfPreview(build: (_) => bytes, allowPrinting: false, allowSharing: false, canChangePageFormat: false, canChangeOrientation: false)` and a Share button calling `ReportSharer`.
- [ ] **Step 5: Run** `flutter gen-l10n; flutter analyze; flutter test` — expect all green.
- [ ] **Step 6: Manual walkthrough** on the emulator against a dev backend on 8081 (`DB_NAME` of a throwaway database, dev seed + a full-day absence recorded through `PUT /sessions/{uuid}`): set the letterhead on the web (port 5175 walkthrough config), generate as the DM, preview, share to the emulator's share sheet; also a day with nobody absent. Screenshot both.
- [ ] **Step 7: Commit** `UC-D9, NFR-12: DM screen to generate, preview and share the full-day absence report`.

---

## Self-review notes

- Spec coverage: who is listed (T2), day rules and default (T2, T7), PDF content incl. date, summary, tables, unrecorded line, footer (T6), empty case without PDF (T7), multiple blocks (T2, T6), letterhead fields/limits/layout/logo/preview/unset fallback (T3, T4, T6), audit (T2, T3), scope tests per role (T2, T3, T4), FR/EN (all), D2 wording (T1).
- Types: JSON names in T2/T3 are the ones parsed in T5; `buildReportPdf`/`reportFileName`/`ReportTexts` defined in T6 and used in T7.
