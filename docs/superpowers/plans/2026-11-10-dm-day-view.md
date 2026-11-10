# DM Day View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A DM sees one day of attendance in their classes, period by period: what was recorded with its absentees, what was not recorded yet, and what is still to come. The day's lateness is listed under each class. From there the DM justifies (one or several) or cancels, as they already can from a student's history (D40, UC-D10).

**Architecture:**
- One read-only endpoint, `GET /api/v1/attendance/day?date=`, in the `reporting` module, next to the full-day report (`FullDayReports`). It adds no rule and writes nothing.
- It reads through public interfaces only. Three small methods are added to them:
  - `SchoolCalendar.slotsOf`: the class's timetable for that date, with subject and teacher;
  - `AttendanceHistory.sessionsOn`: the day's sessions with every absence, cancelled ones included;
  - `LatenessHistory.onDay`: the day's lateness of given students, cancelled ones included.
- Justify and cancel reuse the existing endpoints (`POST /justifications`, `POST /records/{id}/cancel`, `POST /lateness/{id}/cancel`) and the existing sheets and dialogs. After an action, the clients reload the day.
- The server computes every status and count, so Android and the web show the same thing.

**Tech Stack:** Spring Boot 4 (JDBC, Testcontainers), Flutter (Riverpod, go_router, dio, ARB), React + MUI + i18next (Vitest).

**Spec:** `docs/superpowers/specs/2026-11-09-dm-workspace-messages-design.md` (§3 goals, §4.6, §5.1 patterns, §5.2 sidebar, §6, §8 row "Today's attendance", §11 call 6).

## Global Constraints

- Modular monolith: the service lives in `reporting/internal`. It uses `school.SchoolCalendar`, `school.SchoolClasses`, `attendance.AttendanceHistory`, `lateness.LatenessHistory`, `people.Students` and `people.UserAccounts`, never another module's `internal` package.
- NFR-8: the DM role only. The classes come from `user.classesAs(Role.DM)`.
  - Any other role gets 403; anonymous gets 401.
  - A DM sees only the classes of their blocks.
  - One integration test per role, allowed and forbidden.
- BR-4: the DM never records or edits a roll call here. A period that was not recorded is information only, with no button to record it and no reminder sent (§11 call 6).
- BR-8, BR-9: absences and lateness are shown side by side, never merged.
- BR-15: cancelled absences and lateness are listed with the state "cancelled" but never counted.
- NFR-9: no phone numbers in the response.
- NFR-12: every new string exists in FR and EN: ARB, i18next and `messages_*.properties`.
- Screen names: FR "Présences du jour", EN "Today's attendance".
- Period statuses, in the response and on screen:
  - `RECORDED`: FR "Saisie", EN "Recorded";
  - `NOT_RECORDED`: FR "Non saisie", EN "Not recorded";
  - `UPCOMING`: FR "À venir", EN "Upcoming".
- Record states: `UNJUSTIFIED` (FR "Non justifiée", EN "Unjustified"), `JUSTIFIED` (FR "Justifiée", EN "Justified"), `CANCELLED` (FR "Annulée", EN "Cancelled").
- "Today" and "the period has started" are read in the school's time zone (`SchoolCalendar.zone`).
- No real student data. Fixtures use made-up names.
- Commit messages cite D40 and UC-D10. Each ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Time zone at the edges.** The school is in Africa/Douala (UTC+1). At 23:30 UTC on 2 November it is already 3 November at school, so a request without a date must give 3 November. A period starting at 07:30 is `UPCOMING` at 06:29 UTC and `NOT_RECORDED` at 06:31 UTC.
2. **A cancelled absence** is listed with state `CANCELLED`, but it is not counted in `absentCount` or in `totals.absentStudents` (BR-15). Cancelling one from the day view makes the count drop on reload.
3. **A session for a period no longer in the timetable** (the timetable changed after the roll call) still shows as `RECORDED` with its absentees. It must not be dropped.
4. **A student absent in three periods** appears once per period, but counts once in `totals.absentStudents`.
5. **A weekend or a holiday** answers 200 with `schoolDay: false` and no classes, not an error: on a Saturday, the screen opens on today by default.

Each line has its test in Task 1. Lines 2 and 5 also have a client test in Tasks 2 and 3.

---

### Task 1: Backend: the day of attendance

**Files:**
- Modify: `backend/src/main/java/com/schoollink/school/SchoolCalendar.java` and `school/internal/JdbcSchoolCalendar.java` (add `slotsOf`)
- Modify: `attendance/AttendanceHistory.java` and `attendance/internal/JdbcAttendanceHistory.java` (add `sessionsOn`)
- Modify: `lateness/LatenessHistory.java` and `lateness/internal/JdbcLatenessHistory.java` (add `onDay`)
- Create: `reporting/internal/DayAttendance.java` and `reporting/internal/DayAttendanceController.java`
- Modify: `backend/src/main/resources/messages_fr.properties` and `messages_en.properties` (`attendance.date_in_future`)
- Modify: `docs/SPEC.md` (UC-D10 in the pilot use cases, the endpoint in §9) and `docs/PLAN.md` (row D40)
- Test: `backend/src/test/java/com/schoollink/reporting/internal/DayAttendanceTests.java`

**Interfaces:**
- Produces, Java:
  - `List<Slot> SchoolCalendar.slotsOf(UUID schoolId, UUID classId, LocalDate date)` with `record Slot(UUID periodId, int periodNumber, LocalTime startTime, LocalTime endTime, String subject, UUID teacherId)`. It is empty when the date is not a school day, and follows the same academic-year rule as `periodsOf`.
  - `List<DaySession> AttendanceHistory.sessionsOn(UUID schoolId, Set<UUID> classIds, LocalDate date)` with:
    - `record DaySession(UUID classId, UUID periodId, UUID teacherId, Instant confirmedAt, List<DayRecord> absences)`;
    - `record DayRecord(UUID recordId, UUID studentId, boolean justified, boolean cancelled)`;
    - every `ABSENT` record, cancelled ones included.
  - `List<LatenessOnDay> LatenessHistory.onDay(UUID schoolId, Collection<UUID> studentIds, LocalDate date)` with `record LatenessOnDay(UUID recordId, UUID studentId, LocalTime arrivalTime, boolean justified, boolean cancelled)`, cancelled ones included.
- Produces, HTTP: `GET /api/v1/attendance/day?date=YYYY-MM-DD`. The date is optional and defaults to today in the school's zone. The response, which Tasks 2 and 3 consume field for field:

```json
{
  "date": "2026-11-02",
  "schoolDay": true,
  "totals": { "notRecorded": 1, "absentStudents": 2, "lateness": 1 },
  "classes": [{
    "classId": "…", "className": "6e A",
    "periods": [{
      "periodId": "…", "number": 1, "startTime": "07:30", "endTime": "08:25",
      "subject": "Maths", "teacherName": "Paul Enseignant",
      "status": "RECORDED", "absentCount": 2,
      "absences": [{ "recordId": "…", "studentId": "…", "firstName": "Awa", "lastName": "Fictif",
                     "matricule": "TW-0002", "state": "UNJUSTIFIED" }]
    }],
    "lateness": [{ "recordId": "…", "studentId": "…", "firstName": "…", "lastName": "…", "matricule": "…",
                   "arrivalTime": "07:52", "state": "JUSTIFIED" }]
  }]
}
```

- Rules the service applies:
  - A class is listed when it has a timetabled period that day or a session that day. Classes are sorted by name.
  - Periods are the union of the timetabled periods and the periods with a session, sorted by number.
    - With a session: `RECORDED`. `teacherName` is whoever recorded the session; `subject` comes from the timetable, or null when the period is no longer timetabled.
    - Without a session: `NOT_RECORDED` when the date is before today, or when it is today and the period's start time ≤ now (school time). Otherwise `UPCOMING`. `teacherName` comes from the timetable.
  - `absences` are sorted by last name, then first name. `absentCount` counts the states other than `CANCELLED`.
  - Lateness belongs to the student's current class and is sorted by arrival time.
  - Totals:
    - `notRecorded`: the number of `NOT_RECORDED` periods;
    - `absentStudents`: the distinct students with at least one live absence;
    - `lateness`: the number of live lateness records.
  - A date after today gives 400 with code `attendance.date_in_future`. A date that is not a school day gives 200 with `schoolDay: false`, empty `classes` and zero totals.
  - Reading this view is not audited: it is not an export (UC-D9 is).

- [ ] **Step 1: Write the failing tests** in `DayAttendanceTests` (`@Transactional`, `TestWorld`, `TestClock`). The world is Monday 2 November 2026, with `period1` at 07:30 and `slotA` (6e A, Maths, TEACHER_A). The test adds `period2` at 08:30, slots in 6e A and 5e B, and students Awa and Bruno in 6e A.

```java
@TestFactory Stream<DynamicTest> theDayIsForTheDmOnly()            // DM_1 200, DM_2 200; TEACHER_A, ASSISTANT_DM_1, ADMIN_X, COUNSELLOR_X, PARENT_A, RESEARCHER 403; ANONYMOUS 401
@Test void aDmSeesOnlyTheClassesOfTheirBlocks()                    // DM_1: classes == ["6e A"]; DM_2: ["5e B"]
@Test void aRecordedPeriodListsItsAbsenteesWithTheirState()        // Awa unjustified, Bruno justified, a third cancelled → absentCount 2, states as given, sorted by name
@Test void aCancelledAbsenceIsListedButNotCounted()                // POST /records/{id}/cancel as DM_1, then GET: state CANCELLED, absentCount drops by 1, totals.absentStudents too
@Test void aPastPeriodWithoutRollCallIsNotRecorded()               // clock Tuesday 3 Nov, date=2026-11-02, no session in period2 → NOT_RECORDED, teacherName from the timetable
@Test void todayAPeriodIsUpcomingUntilItStarts()                   // 06:29Z → period1 UPCOMING; 06:31Z → NOT_RECORDED (07:30 Douala)
@Test void withoutDateItIsTodayAtSchool()                          // clock 2026-11-03T23:30Z → date "2026-11-04"
@Test void aSessionOutsideTheTimetableIsStillShown()               // delete the slot after the session: RECORDED, subject null, absentees kept
@Test void aStudentAbsentAllDayCountsOnce()                        // Awa absent in period1 and period2 → two rows, totals.absentStudents 1
@Test void theDaysLatenessIsUnderTheStudentsClass()                // one live, one cancelled lateness → both listed by arrival time, totals.lateness 1
@Test void aFutureDateIsRefused()                                  // 400, code attendance.date_in_future
@Test void aWeekendIsNotASchoolDay()                               // date=2026-11-07 → 200, schoolDay false, classes [], totals all 0
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd backend && ./mvnw -q test -Dtest=DayAttendanceTests`
Expected: FAIL, with 404 on `/api/v1/attendance/day` (or compile errors on the new interface methods).

- [ ] **Step 3: Implement** the three interface methods, then `DayAttendance.day(CurrentUser user, LocalDate date)` and the controller. Read the role as `FullDayReports.dmSchool` does. Fetch teacher names once per teacher id with `UserAccounts.fullName`. Fetch students with `Students.cards` and `Students.idsInClass`. Add `attendance.date_in_future` in FR ("La date ne peut pas être dans le futur.") and EN ("The date cannot be in the future."). Add D40 to PLAN, and UC-D10 and the endpoint to SPEC.

- [ ] **Step 4: Run the tests, then the whole suite**

Run: `cd backend && ./mvnw -q test -Dtest=DayAttendanceTests` then `./mvnw -q verify`
Expected: PASS, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src docs/SPEC.md docs/PLAN.md
git commit -m "UC-D10, D40: the DM reads one day of attendance in their classes, period by period"
```

---

### Task 2: Android: "Présences du jour"

**Files:**
- Create: `mobile/lib/features/dm/day/day_models.dart` (`DayAttendance`, `DayClass`, `DayPeriod`, `DayAbsence`, `DayLateness`, enums `PeriodStatus`, `RecordState`, with `fromJson`), and `mobile/lib/features/dm/day/day_screen.dart` (`DayScreen`)
- Modify: `mobile/lib/features/dm/dm.dart`. Add `Future<DayAttendance> day(DateTime? date)` to the repository and `dayProvider` (a `FutureProvider.family<DayAttendance, DateTime?>`).
- Modify: `mobile/lib/router.dart`: route `day` under `/home/:tab`, giving `/home/alerts/day`.
- Modify: `mobile/lib/features/dm/dm_home_screen.dart`: an entry `dm-day-entry` above the full-day report entry, styled like `dm-messages-entry`, with icon `Icons.event_available_outlined`.
- Modify: `mobile/lib/l10n/app_fr.arb` and `app_en.arb`
- Test: `mobile/test/features/dm_day_test.dart`

**Interfaces:**
- Consumes: Task 1's JSON; `DmRepository.justify`, `cancelAbsence` and `cancelLateness`; `showActionSheet`, `JustifySheet(count:, send:)`, `CancelRecordSheet(what:, send:)` from `dm_sheets.dart`; `HeaderBand`, `SectionHeader`, `EmptyState`, `StatusPill`.
- Produces: the route `/home/alerts/day`, which plan 5's "Aujourd'hui" tab will reuse.

Screen layout:
- A `HeaderBand` with the title and a date chip `day-date`. The chip opens `showDatePicker`: last date today, first date 1 September of the previous year, weekends selectable. The screen opens on today.
- One `SectionHeader` per class. Under it, one row per period `day-period-<number>-<className>`, showing "P1 · 07:30 · Maths · Paul Enseignant" and a `StatusPill` (it takes `background` and `foreground` colours). Use the app's existing status colours, as `messages_screen.dart` does for its pills:
  - `RECORDED` → the brand colour, "Saisie · 2 absents";
  - `NOT_RECORDED` → the warning colour;
  - `UPCOMING` → a neutral surface colour.
- Tapping a `RECORDED` row opens a sheet listing the absentees:
  - each absentee has a checkbox (`day-absence-<recordId>`) and a state pill;
  - "Justifier (n)" opens `JustifySheet` for the ticked ones, which must be unjustified and not cancelled;
  - each row's trailing menu has "Annuler l'absence", which opens `CancelRecordSheet`;
  - "Voir l'historique" pushes the student page.
- Under the periods, the class's lateness ("Retards", arrival time, state pill), with the same justify and cancel.
- Cancelled rows are struck through and greyed, with no checkbox and no menu.
- After any action, invalidate `dayProvider(date)`. Pull to refresh does the same.
- Weekend or holiday: `EmptyState` "Pas de cours ce jour-là." / "No lessons that day." No classes: `EmptyState` "Aucune classe dans vos blocs." / "No class in your blocks."

- [ ] **Step 1: Write the failing tests** (`FakeServer`, `pumpApp`, signed in as a DM, the same layout as `dm_messages_test.dart`)

```dart
testWidgets('the DM home leads to today\'s attendance')                      // tap dm-day-entry → title "Présences du jour", GET /attendance/day without date
testWidgets('periods show their status')                                     // RECORDED "Saisie · 2 absents", NOT_RECORDED "Non saisie", UPCOMING "À venir"
testWidgets('a recorded period opens its absentees and justifies several')   // tick two, "Justifier (2)", pick Maladie → POST /justifications absenceIds both, latenessIds []; day read again
testWidgets('an absence is cancelled with a reason')                         // menu → "Annuler l'absence", reason → POST /records/<id>/cancel {reason}; day read again
testWidgets('a cancelled absence is struck through and cannot be ticked')   // state CANCELLED: no checkbox, text "Annulée"
testWidgets('the day\'s lateness is justified from the class')               // tick lateness → POST /justifications latenessIds [id], absenceIds []
testWidgets('another date is asked for')                                     // pick 2026-11-02 in the date picker → GET /attendance/day?date=2026-11-02
testWidgets('a day without lessons says so')                                 // schoolDay false → "Pas de cours ce jour-là."
testWidgets('reads in English (NFR-12)')                                     // "Today's attendance", "Not recorded"
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd mobile && flutter test test/features/dm_day_test.dart`
Expected: FAIL, because `dm-day-entry` is not found.

- [ ] **Step 3: Implement** as in Files. Add the ARB keys to both files in the same change, then run `flutter gen-l10n`.

- [ ] **Step 4: Run the tests, analyze, and the whole suite**

Run: `cd mobile && flutter test test/features/dm_day_test.dart && flutter analyze && flutter test`
Expected: all pass; analyze reports "No issues found!".

- [ ] **Step 5: Check on the emulator** (emulator-5554 only) as the dev-seed DM, against a backend on current code: open today, open a recorded period, cancel nothing. Then commit:

```bash
git add mobile/lib mobile/test
git commit -m "UC-D10, D40: the DM sees today's attendance on Android and justifies or cancels from it"
```

---

### Task 3: Web: "Présences du jour"

**Files:**
- Create: `web/src/api/day.ts` (types matching Task 1's JSON, `DAY_PATH = '/attendance/day'`), and `web/src/pages/dm/DayPage.tsx` (`/day`, with the date in the URL as `?date=`)
- Modify: `web/src/routes.tsx` (`{ path: '/day', element: dm(<DayPage />) }`), and `web/src/components/AppLayout.tsx` (DM nav "Présences du jour" second in the discipline group, before Alertes, with icon `CalendarCheckIcon` from `src/icons.ts`, added there with CRLF kept)
- Modify: `web/src/i18n/fr.json` and `en.json` (`nav.day` and a `day.*` section)
- Test: `web/src/pages/dm/day.test.tsx`

**Interfaces:**
- Consumes: Task 1's JSON; `JustifyDialog({count, onSubmit, onClose})` and `CancelRecordDialog({what, onSubmit, onClose})` from `pages/dm/dialogs.tsx`; `DM_PATHS.justifications`, `cancelAbsence` and `cancelLateness`; `PageHeader`, `Pill`, `useResource`.

Page layout:
- A `PageHeader` with a date field (`type="date"`, `max` today) and "Aujourd'hui" and "Jour précédent" buttons. Changing the date updates `?date=`, so the URL is a deep link.
- Three totals across the top: "Périodes non saisies", "Élèves absents", "Retards".
- One `Paper` per class: a table of periods with columns Période, Horaire, Matière, Enseignant, Statut, Absents.
  - A `RECORDED` row expands in place (a disclosure button `aria-expanded`) to its absentees, with checkboxes, a state pill and a "Annuler" action per row.
  - Above them, "Justifier la sélection (n)".
- The class's lateness follows in its own small table, with the same actions.
- Cancelled rows: `text-decoration: line-through`, muted, with no checkbox or action.
- After an action, reload the resource and show a success `Alert`.

- [ ] **Step 1: Write the failing tests** (`fakeFetch`, `renderApp`, `tokens`)

```ts
it('lists each class with its periods and their status')            // "Saisie", "Non saisie", "À venir"; totals "1 période non saisie", "2 élèves absents", "1 retard"
it('justifies several absences of a period')                        // expand P1, tick two, "Justifier la sélection (2)", reason → POST /justifications {absenceIds:[a,b], latenessIds:[]}; GET again
it('cancels an absence with a reason')                              // "Annuler" on a row → POST /records/<id>/cancel
it('shows a cancelled absence struck through, without actions')
it('reads another day from the URL')                                // renderApp('/day?date=2026-11-02') → GET /attendance/day?date=2026-11-02
it('says when there are no lessons that day')                       // schoolDay false → "Pas de cours ce jour-là."
it('is in the DM menu only')                                        // DM: link "Présences du jour" → /day; ADMIN: /day redirects to /
it('reads in English (NFR-12)')
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd web && npx vitest run src/pages/dm/day.test.tsx`
Expected: FAIL, because the "Présences du jour" link is not found.

- [ ] **Step 3: Implement** as in Files. Do not run prettier.

- [ ] **Step 4: Run the whole suite, lint and build**

Run: `cd web && npx vitest run --maxWorkers=2 && npm run lint && npm run build`
Expected: all pass. (Under emulator load, the default parallel run times out; `--maxWorkers=2` is the reliable run.)

- [ ] **Step 5: Check in a browser** as the dev-seed DM (sg1@lycee-demo-a.test): today's view, then the previous day through the button. Then commit:

```bash
git add web/src
git commit -m "UC-D10, D40: the DM sees the day's attendance on the web and justifies or cancels from it"
```
