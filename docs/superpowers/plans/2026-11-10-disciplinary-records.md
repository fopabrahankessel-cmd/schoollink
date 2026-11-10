# Disciplinary records (UC-D5, D37) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A DM records a disciplinary incident for a student of their blocks at any time, sees the student's incidents, closes them or cancels one made by mistake, on Android and on the web; the alert decision "disciplinary issue" records the same kind of incident.

**Architecture:** The `disciplinary_record` table (V22) gains category, date of the incident, measure, closing and cancelling columns, guarded by a trigger like `risk_alert` (no delete, no edit, closed once, cancelled once). The `discipline` module owns one insert path (`DisciplineOutcomes.recordDisciplinary`, used by the alert decision and the new endpoint), a service for list, close and cancel, and a controller. On both clients the DM's student page gets two tabs, History and Discipline.

**Tech Stack:** Spring Boot 4, Flyway, JDBC, JUnit 5 + Testcontainers + `PermissionMatrix` + `TestClock`; Flutter, Riverpod, dio, ARB (FR/EN); React, MUI, i18next, Vitest + `fakeApi`.

**Spec:** `docs/superpowers/specs/2026-11-09-dm-workspace-messages-design.md`, §4.1, §5.1 (student page), §5.2, §8 (parity row "Disciplinary records"), §11 call 5. This is plan 2 of 5.

## Global Constraints

- Values fixed by the design §4.1: categories `BEHAVIOUR`, `INSOLENCE`, `FIGHT`, `ABSENTEEISM`, `CHEATING`, `DRESS`, `PHONE`, `DAMAGE`, `OTHER`; measures `NONE`, `WARNING`, `DETENTION`, `SUSPENSION`, `OTHER`; `occurred_on` not in the future and at most 30 days back (school time zone); description 1 to 500 characters; status `OPEN` or `CLOSED`.
- No edit after saving: corrections are a cancel (with a reason) plus a new record. Cancelled records stay in the table and are hidden from lists (design §4.1, spirit of BR-13 to BR-15).
- Rules only raise alerts (BR-16 to BR-19): no code outside a DM's request creates a record. No notification to parents for a record (design §3 non-goal, §11 call 5).
- NFR-8: write = a DM whose blocks contain the student's **current** class; read = that DM or an ADMIN of the school. Everyone else 403; another school's or out-of-scope student or record is 404. School and scope from `CurrentUser`, never the request. A permission-matrix test per endpoint.
- NFR-11: create (`CREATE`), close (`UPDATE`), cancel (`CANCEL`) are written to `audit_log` in the same transaction.
- NFR-12: every new string in FR and EN: `messages_fr/en.properties` (API errors), `app_fr/en.arb` (+ `flutter gen-l10n`), `web/src/i18n/fr.json` and `en.json`.
- Fake data only in tests. Cite UC-D5, D37 and the BR/NFR numbers in commits and in comments where a rule is implemented.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never commit `backend/.run/SchoolLink (dev).run.xml`.

## Decisions this plan makes (the design leaves them to the builder)

- The counsellor's read access ("R (referred)" in SPEC §3) waits for the counsellor screens (UC-C1, wave 2): the counsellor gets 403 here. The assistant DM gets 403.
- A cancelled record is gone for every endpoint: listing skips it, close and cancel answer 404.
- Closing a closed record is 409 `discipline.already_closed`; a closed record can still be cancelled.
- An alert decision `DISCIPLINARY` sent without a category or measure (older app versions) records `OTHER` / `NONE`; its `occurred_on` is the school's today.
- The DM's student page on mobile: a `TabBar` History | Discipline under the app bar, a "Record an incident" extended FAB on the Discipline tab; the summon button stays in the app bar (the full action menu comes with messages, plan 3). On the web: MUI `Tabs` with one URL per tab, `/students/:id` and `/students/:id/discipline`.

## Review Focus

1. The date of the incident near midnight: the school's today (Africa/Douala) is accepted even when UTC is already tomorrow or still yesterday; tomorrow and 31 days back are refused with a clear message on the `occurredOn` field.
2. A student who moved to a class of another block: the old DM can no longer read, close or cancel the student's records (404); the new block's DM can.
3. Two closes of the same record (double tap, or two DMs): the second gets 409 and only one audit row exists.
4. An older app sending `DISCIPLINARY` without category or measure still records the incident, as `OTHER` / `NONE`.
5. A description of spaces only: the mobile and web forms keep the save button disabled, and the server refuses it (400 on `description`) if it comes anyway.

Each line has its test in the task that owns the code: 1 to 3 and 5 (server) in Tasks 1 and 2, 4 in Task 3, 5 (forms) in Tasks 4 and 5.

---

### Task 1: Migration, create and list

**Files:**
- Create: `backend/src/main/resources/db/migration/V28__disciplinary_record_details.sql`
- Create: `backend/src/main/java/com/schoollink/discipline/DisciplinaryCategory.java`, `DisciplinaryMeasure.java` (public enums, values above)
- Modify: `backend/src/main/java/com/schoollink/discipline/DisciplineOutcomes.java`, `discipline/internal/JdbcDisciplineOutcomes.java`
- Modify: `backend/src/main/java/com/schoollink/detection/internal/Alerts.java:153` (new signature, passes `OTHER`, `NONE`, `null`)
- Create: `backend/src/main/java/com/schoollink/discipline/internal/DisciplinaryRecords.java` (service), `DisciplinaryRecordController.java`
- Modify: `backend/src/main/resources/messages_fr.properties`, `messages_en.properties`
- Modify: `docs/SPEC.md` (UC-D5 moves to the pilot, `DisciplinaryRecord` row, API rows), `docs/PLAN.md` (D37 row after D36)
- Test: `backend/src/test/java/com/schoollink/discipline/internal/DisciplinaryRecordTests.java`

**Interfaces:**
- Produces:
  - `DisciplineOutcomes.recordDisciplinary(UUID schoolId, UUID studentId, UUID alertId, UUID actorId, DisciplinaryCategory category, DisciplinaryMeasure measure, LocalDate occurredOn, String description) -> UUID` — `occurredOn` null means the school's today (`SchoolCalendar.zone` + `Clock`). Validates nothing (callers do); audits `CREATE` with all fields in `after`.
  - `GET /api/v1/students/{id}/disciplinary-records` → `200 [RecordView]`, not cancelled, ordered `occurred_on DESC, recorded_at DESC`.
  - `POST /api/v1/students/{id}/disciplinary-records` body `{category, measure?, occurredOn?, description}` → `201 RecordView`. `measure` defaults to `NONE`, `occurredOn` to today.
  - `RecordView(UUID id, UUID studentId, UUID alertId, String category, String measure, LocalDate occurredOn, String description, String status, String closeNote, String recordedByName, Instant recordedAt, Instant closedAt)`
  - Error codes: `discipline.category_required` (field `category`), `discipline.description_required` (`description`, blank after strip, or over 500), `discipline.occurred_on_range` (`occurredOn`).

- [ ] **Step 1: Write the migration test and the failing endpoint tests**

In `DisciplinaryRecordTests` (`@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class, TestClock.Config.class) @Transactional`, `TestWorld` as in `ChildContactsTests`; `clock.reset()` in `@AfterEach`):

```java
@TestFactory Stream<DynamicTest> onlyTheStudentsDmRecords()   // POST for studentA
	// 201 DM_1; 404 DM_2 (other block); 403 ADMIN_X, ADMIN_Y, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER (not a DM); 401 ANONYMOUS
@TestFactory Stream<DynamicTest> theStudentsDmAndTheAdminRead() // GET for studentA
	// 200 DM_1, ADMIN_X; 404 DM_2 (other block), ADMIN_Y (other school); 403 TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER; 401 ANONYMOUS
@Test void recordsAnIncidentWithItsCategoryDateAndMeasure()
	// POST {category:"FIGHT", measure:"DETENTION", occurredOn: today-2, description:"  Bagarre à la récréation  "}
	// → 201, category FIGHT, measure DETENTION, occurredOn today-2, description "Bagarre à la récréation", status OPEN, recordedByName = DM_1's name
	// and one audit_log row (CREATE, entity disciplinary_record, after.category = FIGHT)
@Test void measureAndDateDefaultToNoneAndToday()
@Test void listsNewestIncidentFirstAndLeavesOutCancelledOnes()  // cancelled via jdbc UPDATE setting the three cancel columns
@Test void theDateOfTheIncidentFollowsTheSchoolDay()           // Review Focus 1
	// clock.set(2026-11-09T23:30:00Z) = 10 Nov 00:30 in Douala: occurredOn 2026-11-10 → 201
	// occurredOn 2026-11-11 → 400 field occurredOn; 2026-10-11 (30 days back) → 201; 2026-10-10 → 400
@Test void aBlankDescriptionOrMissingCategoryIsRefused()        // Review Focus 5: "   " → 400 description; no category → 400 category; 501 chars → 400 description
@Test void aStudentWhoMovedBlockBelongsToTheNewBlocksDm()      // Review Focus 2: UPDATE student SET class_id = classB → DM_1 GET 404, DM_2 GET 200 with the record
@Test void theMigratedRowsFromAlertsHaveACategoryAndADate()    // an alert decision DISCIPLINARY (via POST /alerts/{id}/decision as DM_1) → record category OTHER, measure NONE, occurredOn today
@Test void aRecordCannotBeDeletedOrRewritten()                 // jdbc DELETE and UPDATE description → DataAccessException (trigger)
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd backend && ./mvnw test -Dtest=DisciplinaryRecordTests`
Expected: compilation fails (`DisciplinaryCategory` missing), then, once the enums exist, 404s on the new paths.

- [ ] **Step 3: Write `V28__disciplinary_record_details.sql`**

Add `category` (`NOT NULL DEFAULT 'OTHER'`, then `DROP DEFAULT`), `measure` (`NOT NULL DEFAULT 'NONE'`, kept), `occurred_on date` (added nullable, filled with `(recorded_at AT TIME ZONE s.timezone)::date` from `school s`, then `SET NOT NULL`), `close_note text CHECK (<= 500)`, `closed_by uuid`, `closed_at timestamptz`, `cancelled_by uuid`, `cancelled_at timestamptz`, `cancel_reason text CHECK (char_length BETWEEN 1 AND 500)`; foreign keys `(school_id, closed_by)` and `(school_id, cancelled_by)` to `app_user`; CHECKs: category and measure in the lists, `(status = 'CLOSED') = (closed_at IS NOT NULL AND closed_by IS NOT NULL)`, the three cancel columns all null or all set. Then a `disciplinary_record_guard()` trigger (BEFORE UPDATE OR DELETE) modelled on `risk_alert_guard` in V22: no DELETE; a cancelled row never changes; `id, school_id, student_id, alert_id, category, measure, occurred_on, description, recorded_by, recorded_at` never change; `status` only goes `OPEN` → `CLOSED`, and the close columns only change with it. Comment each block with D37 / UC-D5.

- [ ] **Step 4: Implement the enums, the new `recordDisciplinary`, `DisciplinaryRecords.list/create` and the controller**

Scope as in `SummonsService.summon` and `ChildHistory.ofStudent`: `Students.classOf(school, studentId)`; write requires `user.has(Role.DM)` (else 403) and the class in `user.classesAs(Role.DM)` (else 404); read allows `Role.ADMIN` too. `recordedByName` = `first_name || ' ' || last_name` of `recorded_by`. Date check against `clock.instant().atZone(calendar.zone(school)).toLocalDate()`. Error messages in both properties files, e.g. FR `error.discipline.occurred_on_range=La date de l'incident doit être entre il y a 30 jours et aujourd'hui.` / EN `error.discipline.occurred_on_range=The date of the incident must be between 30 days ago and today.`

- [ ] **Step 5: Run the tests, then the whole suite**

Run: `./mvnw test -Dtest=DisciplinaryRecordTests` then `./mvnw verify`
Expected: all pass (AlertTests still pass: they only see `outcome_id`).

- [ ] **Step 6: Update SPEC and PLAN**

SPEC: UC-D5 row "Pilot (D37)"; the `DisciplinaryRecord` row lists `category, measure, occurred_on, description, status, cancelled_*`; API rows for the two endpoints of this task (Task 2 adds its two). PLAN: row D37 "Disciplinary records outside alerts" stating the lists, the 30-day window, no edit (cancel + new), no parent notification, counsellor read deferred.

- [ ] **Step 7: Commit**

```bash
git add backend/src docs/SPEC.md docs/PLAN.md
git commit -m "UC-D5, D37: a DM records a disciplinary incident with its category, date and measure"
```

### Task 2: Close and cancel

**Files:**
- Modify: `discipline/internal/DisciplinaryRecords.java`, `DisciplinaryRecordController.java`, `messages_*.properties`, `docs/SPEC.md` (two API rows)
- Test: `DisciplinaryRecordTests.java` (same class)

**Interfaces:**
- Consumes: Task 1's table, `RecordView`, scope helper.
- Produces:
  - `POST /api/v1/disciplinary-records/{id}/close` body `{note?}` (≤ 500) → `200 RecordView` (status `CLOSED`, `closeNote`, `closedAt`). 409 `discipline.already_closed`.
  - `POST /api/v1/disciplinary-records/{id}/cancel` body `{reason}` → `204`. 400 `discipline.reason_required` (field `reason`).
  - Both: DM of the student's current class, row locked `FOR UPDATE`; cancelled, other school's or out of scope → 404; other roles 403.

- [ ] **Step 1: Write the failing tests**

```java
@TestFactory Stream<DynamicTest> onlyTheStudentsDmCloses()   // 200 DM_1; 404 DM_2; 403 ADMIN_X, ADMIN_Y, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER; 401 ANONYMOUS — a fresh record per dynamic test
@TestFactory Stream<DynamicTest> onlyTheStudentsDmCancels()  // 204 DM_1; same forbidden rows
@Test void closingKeepsTheNoteAndIsAudited()                 // note "Parents reçus" → CLOSED, closeNote, one audit UPDATE row before.status OPEN after.status CLOSED
@Test void closingTwiceIsAConflictAndAuditsOnce()            // Review Focus 3: second close → 409 discipline.already_closed; audit rows for this id with action UPDATE = 1
@Test void aClosedRecordCanStillBeCancelled()
@Test void cancellingNeedsAReasonAndHidesTheRecord()         // reason "  " → 400 reason; reason "Erreur d'élève" → 204, GET list empty, audit CANCEL with reason
@Test void aCancelledRecordIsNotFoundForCloseOrCancel()       // both → 404
@Test void theOldBlocksDmCannotCloseAfterTheStudentMoved()    // Review Focus 2 for writes: class_id → classB; DM_1 close → 404; DM_2 close → 200
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw test -Dtest=DisciplinaryRecordTests`
Expected: the new tests fail with 404 or 405 on the new paths; Task 1's tests still pass.

- [ ] **Step 3: Implement `close(CurrentUser, UUID id, String note) -> RecordView` and `cancel(CurrentUser, UUID id, String reason) -> void`**

`SELECT … FROM disciplinary_record WHERE id = :id AND school_id = :school AND cancelled_at IS NULL FOR UPDATE`, then the scope check on the student's current class, then the update and the audit row in the same transaction. Note and reason are stripped; a blank note is stored as null.

- [ ] **Step 4: Run the tests, then `./mvnw verify`**

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src docs/SPEC.md
git commit -m "UC-D5, D37, NFR-11: a DM closes a disciplinary incident or cancels one made by mistake"
```

### Task 3: The alert decision records category and measure

**Files:**
- Modify: `backend/src/main/java/com/schoollink/detection/internal/Alerts.java` (`DecisionRequest` gains `DisciplinaryCategory category, DisciplinaryMeasure measure`, both optional)
- Test: `backend/src/test/java/com/schoollink/detection/internal/AlertTests.java`

**Interfaces:**
- Consumes: Task 1's `recordDisciplinary`.
- Produces: `POST /alerts/{id}/decision` accepts `category` and `measure` for `DISCIPLINARY` (ignored for other decisions); missing → `OTHER` / `NONE`; `occurredOn` = today. The note stays required and becomes the record's description.

- [ ] **Step 1: Write the failing tests**

```java
@Test void aDisciplinaryDecisionRecordsTheChosenCategoryAndMeasure()  // {decision:DISCIPLINARY, note:"Insolence en classe", category:"INSOLENCE", measure:"WARNING"} → GET /students/{id}/disciplinary-records [0].category INSOLENCE, measure WARNING, alertId = the alert
@Test void anOlderAppWithoutCategoryStillRecordsTheIncident()         // Review Focus 4: no category/measure → OTHER / NONE
```

- [ ] **Step 2: Run them to see the first fail** (`./mvnw test -Dtest=AlertTests`): category is `OTHER`.

- [ ] **Step 3: Pass `request.category()` / `request.measure()` (or the defaults) to `recordDisciplinary`.**

- [ ] **Step 4: Run `AlertTests`, then `./mvnw verify`.** Expected: all pass.

- [ ] **Step 5: Commit** — `git commit -m "UC-D2, UC-D5, D37: the disciplinary decision on an alert records its category and measure"`

### Task 4: Android — the Discipline tab on the DM's student page

**Files:**
- Create: `mobile/lib/features/dm/discipline.dart` (model, enums, labels, provider), `mobile/lib/features/dm/discipline_tab.dart` (list + FAB), `mobile/lib/features/dm/discipline_sheets.dart` (`RecordIncidentSheet`, `CloseIncidentSheet`)
- Modify: `mobile/lib/features/dm/dm.dart` (repository methods; `decide` gains `category`, `measure`), `student_history_screen.dart` (tabs), `dm_sheets.dart` (`DecisionSheet` shows category and measure for `Decision.disciplinary`), `alert_screen.dart` (passes them), `mobile/lib/l10n/app_fr.arb`, `app_en.arb`
- Test: `mobile/test/features/dm_discipline_test.dart`; `mobile/test/features/dm_test.dart` (existing student-page finders, if the tabs move them)

**Interfaces:**
- Consumes: Tasks 1–3's endpoints and `RecordView` JSON.
- Produces (Dart):
  - `enum DisciplinaryCategory { behaviour('BEHAVIOUR'), …, other('OTHER') }`, `enum DisciplinaryMeasure { none('NONE'), warning('WARNING'), detention('DETENTION'), suspension('SUSPENSION'), other('OTHER') }`, each with `final String wire`; `String categoryLabel(DisciplinaryCategory, AppLocalizations)`, `String measureLabel(DisciplinaryMeasure, AppLocalizations)`.
  - `class DisciplinaryRecord { id, category, measure, occurredOn (DateTime, date only), description, open (bool), closeNote, recordedByName, alertId; factory fromJson }`
  - `final disciplinaryRecordsProvider = FutureProvider.autoDispose.family<List<DisciplinaryRecord>, String>` (student id).
  - `DmRepository`: `Future<List<DisciplinaryRecord>> disciplinaryRecords(String studentId)`, `Future<DisciplinaryRecord> recordIncident(String studentId, {required DisciplinaryCategory category, required DisciplinaryMeasure measure, required DateTime occurredOn, required String description})`, `Future<void> closeIncident(String id, {String? note})`, `Future<void> cancelIncident(String id, String reason)`; `decide(…, {DisciplinaryCategory? category, DisciplinaryMeasure? measure})`.

- [ ] **Step 1: Write the failing widget tests** (fake server and harness as in `dm_test.dart`; FR unless stated)

```dart
testWidgets('the student page has History and Discipline tabs (UC-D5)')
	// open the student → Tab 'Historique' and Tab 'Discipline'; tap Discipline → the records: 'Bagarre', '3 nov.', 'Retenue', status chip 'En cours'
testWidgets('an empty Discipline tab says what to do')
	// [] → 'Aucun incident enregistré.' and the FAB 'Signaler un incident'
testWidgets('recording an incident sends category, date, measure and text')
	// FAB → sheet; choose chip 'Bagarre', measure 'Retenue', type text, save → POST body {category:FIGHT, measure:DETENTION, occurredOn:'2026-11-02', description:…}; snackbar 'Incident enregistré.'; list re-read
testWidgets('the save button stays disabled for a blank description or no category')  // Review Focus 5
testWidgets('the date picker allows today back to 30 days only')                       // firstDate = today-30, lastDate = today
testWidgets('a record can be closed with a note, or cancelled with a reason')
	// tap the record → sheet with 'Clôturer' and 'Annuler l'incident'; close → POST /disciplinary-records/d1/close {note}; cancel uses CancelRecordSheet → POST …/cancel {reason}
testWidgets('the disciplinary decision on an alert asks for category and measure')
	// alert screen → decision 'Incident disciplinaire' → chips + measure in the sheet; POST /alerts/a1/decision has category and measure
testWidgets('the Discipline tab reads in English (NFR-12)')  // 'Record an incident', 'Fight', 'Detention', 'Open'
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd mobile && flutter test test/features/dm_discipline_test.dart`
Expected: fails to load (`discipline.dart` missing).

- [ ] **Step 3: Implement**

Patterns from the existing DM screens: `showActionSheet` with `_SheetFrame`/`_Submitting` for the two sheets (category as `ChoiceChip`s in a `Wrap`, measure as a `DropdownMenu`, date as an `OutlinedButton` opening `showDatePicker` with the 30-day window, description `TextField` with `maxLength: 500`); the list as `Card`s with the category label as title, the date and measure as subtitle, the description, and a status chip (`StatusColors`); `EmptyState` when empty; `Skeleton` while loading; `FailureView` with retry; pull to refresh. The student page becomes a `DefaultTabController(length: 2)` with the `TabBar` styled like the parent child page (`onHeader` / `onHeaderMuted`, transparent divider); the justify bottom bar shows only on the History tab. ARB keys (FR / EN) for the 9 categories, 5 measures, `disciplineTab`, `recordIncident`, `incidentRecorded`, `noIncidents`, `incidentOpen`, `incidentClosed`, `closeIncident`, `cancelIncident`, `incidentDate`, `incidentMeasure`, `incidentCategory`, `incidentDescription`, `closeNote`; run `flutter gen-l10n`.

- [ ] **Step 4: Run the tests, `flutter analyze`, then `flutter test`**

Expected: all pass, no issues.

- [ ] **Step 5: Check on the emulator**

Backend on 8081 with the dev profile (the user's runs on 8080), `flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8081/api/v1`, sign in as a dev-seed DM, open a student, record an incident, close it, record and cancel another. One screenshot of the Discipline tab for the report.

- [ ] **Step 6: Commit**

```bash
git add mobile/lib mobile/test
git commit -m "UC-D5, D37: the DM records, closes and cancels incidents from the student page on Android"
```

### Task 5: Web — the Discipline tab on the DM's student page

**Files:**
- Modify: `web/vite.config.ts` (proxy target `process.env.API_PROXY ?? 'http://localhost:8080'`, so a test backend can run beside the user's)
- Modify: `web/src/api/dm.ts` (types, `DM_PATHS.disciplinary(studentId)`, `close(id)`, `cancelIncident(id)`; `DecisionRequest` gains `category?`, `measure?`), `web/src/routes.tsx` (`/students/:id/discipline`), `web/src/pages/dm/StudentHistoryPage.tsx` (tabs), `web/src/pages/dm/dialogs.tsx` (`RecordIncidentDialog`, `CloseIncidentDialog`; `DecisionForm` shows category and measure for `DISCIPLINARY`), `web/src/i18n/fr.json`, `en.json`
- Create: `web/src/pages/dm/DisciplineTab.tsx`
- Test: `web/src/pages/dm/discipline.test.tsx`

**Interfaces:**
- Consumes: the same endpoints.
- Produces (TS): `type DisciplinaryCategory = 'BEHAVIOUR' | … | 'OTHER'`, `CATEGORIES`, `type DisciplinaryMeasure`, `MEASURES`, `interface DisciplinaryRecord` (fields of `RecordView`), `interface IncidentRequest { category; measure; occurredOn; description }`.

- [ ] **Step 1: Write the failing tests** (`fakeFetch`, `renderApp`, `tokens` as in `dm.test.tsx`)

```ts
it('shows History and Discipline tabs, each with its own URL')         // /students/s1 → tab History selected; click Discipline → location /students/s1/discipline; reload there → Discipline selected
it('lists incidents newest first with category, date, measure and status')
it('records an incident and shows it')                                  // dialog: select category, measure, date input (min today-30, max today), text → POST body; success alert 'Incident recorded'
it('keeps Save disabled for a blank description or no category')       // Review Focus 5
it('closes with a note and cancels with a reason')                      // ConfirmDialog flows; POST paths
it('asks for category and measure on a disciplinary alert decision')
it('reads in French (NFR-12)')
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd web && npm test -- discipline`
Expected: fails (`DisciplineTab` missing, no route).

- [ ] **Step 3: Implement**

MUI `Tabs` under the page header, driven by the route (`useMatch`), each `Tab` a link; the Discipline tab is a table (sticky header: date, category, measure, description, recorded by, status pill, actions menu) with an empty state and a "Record an incident" button in the page's right-hand action area next to Summon. Dialogs reuse `ActionForm`; the date is an `<input type="date">` (`TextField type="date"`) with `min`/`max`. All strings in `fr.json` and `en.json`.

- [ ] **Step 4: Run `npm test`, `npm run lint`, `npm run build`**

Expected: all pass.

- [ ] **Step 5: Check in a browser**

`npm run dev` on 5173 is the user's: leave it. Run `API_PROXY=http://localhost:8081 npx vite --port 5174 --strictPort` against the 8081 test backend. Sign in as a dev-seed DM, open a student, Discipline tab, record, close, cancel. One screenshot.

- [ ] **Step 6: Commit**

```bash
git add web/src
git commit -m "UC-D5, D37: the DM records, closes and cancels incidents from the student page on the web"
```
