# DM navigation and parity (D41) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the DM four Android tabs (Today, Students, Messages, More), bring the scope dashboard and the threshold settings to Android, give the student page a Messages tab on both platforms, and bring the full-day report to the web, so every row of the spec's parity table reads "both".

**Architecture:** Mostly client work. One backend change: `GET /messages` gains an optional `studentId` filter for the student page's Messages tab. Android replaces the single DM `alerts` destination with four destinations and makes pushed DM screens open under the current tab. The web gains a "Rapport de la journée" page that prints through the browser.

**Tech Stack:** Spring Boot 4 (NamedParameterJdbcTemplate, Testcontainers), Flutter (Riverpod, go_router, ARB), React + MUI + i18next (Vitest).

**Spec:** `docs/superpowers/specs/2026-11-09-dm-workspace-messages-design.md` (§5.1, §5.2, §8, §11 call 8)

## Global Constraints

- NFR-8: scope from the JWT. DM: classes of their blocks; the `studentId` filter only narrows the DM's own messages.
- NFR-9: no phone numbers in URLs, logs or the report.
- NFR-12: every new string in FR and EN (ARB, i18next, `messages_*.properties`), in the same commit.
- Mobile patterns (§5.1): pull to refresh, skeletons while loading, empty states that say what to do, 48 dp touch targets, strings only from ARB files. The teacher and gate screens are not changed.
- Web patterns (§5.2): URL per page and per tab, keyboard reachable, FR/EN through i18next.
- Visual direction (§5.4): reuse `HeaderBand`, `SectionHeader`, `EmptyState`, `InfoBanner`, `Skeleton` on mobile; `PageHeader`, the status pills and dialogs on the web; the palette of commit 709b259.
- Settings bounds (D28, as the web holds them): start time 05:00–12:00; digest times 06:00–20:00, the second later than the first; each rule count within its `min`..`max`.
- Line endings: keep each file's existing endings (CRLF files edited with the scratchpad `ed.py` helper).
- No real student data in tests. Commit after each task; cite D41 and the UC in the message. Never commit `backend/.run/SchoolLink (dev).run.xml`.

## Review Focus

1. A DM who also teaches and is a parent (seven destinations): the bar shows five, the rest are in "Plus", and `/home/children` still opens by deep link. → Task 2 test `overflow goes to More`.
2. A student opened from the Students tab: the Students tab stays selected and back returns to the search. → Task 2 test `a pushed screen stays under its tab`.
3. On Today, one request fails and the other succeeds: the working cards show their counts, and the failed card offers a retry. It never shows 0. → Task 3 test `a failed count offers a retry, never zero`.
4. Settings with the second digest time before the first, or a time out of bounds: Save is disabled with the reason shown. → Task 6 test `an out-of-order digest blocks saving`.
5. Web report for a day the server refuses, or a day with nobody absent: the message is shown and there is no print button. → Task 9 tests `a refused day shows the server's message` and `nobody absent: no sheet, no print`.

---

### Task 1: Backend: a student's messages

**Files:**
- Modify: `backend/src/main/java/com/schoollink/notification/internal/ParentMessageController.java` (`sentMessages`)
- Modify: `backend/src/main/java/com/schoollink/notification/internal/ParentMessages.java` (`sent`)
- Modify: `docs/SPEC.md` (API row of `GET /messages`), `docs/PLAN.md` (D41 row)
- Test: `backend/src/test/java/com/schoollink/notification/internal/ParentMessageTests.java`

**Interfaces:**
- Produces: `GET /api/v1/messages?studentId=<uuid>&limit=`. It returns the caller's own `DM_MESSAGE` rows with `audience = STUDENT` and that `student_id`, newest first, in the same JSON as now. Without `studentId` the behaviour is unchanged.

- [ ] **Step 1: Write the failing tests** in `ParentMessageTests`:
  - `aStudentsMessagesAreFilteredByStudent`: DM_1 sends one message to `studentA2` and one to `classA`. Then `GET /api/v1/messages?studentId={studentA2}` → `$.length() == 1`, `$[0].audience == "STUDENT"`, `$[0].studentId == studentA2`.
  - `anotherDmsStudentListsNothing`: DM_2 calls `GET /api/v1/messages?studentId={studentA2}` → 200, `$.length() == 0`. The DM's own messages only, so nothing leaks (NFR-8).
  - `aMalformedStudentIdIsRefused`: `?studentId=abc` → 400.
- [ ] **Step 2: Run them.** `cd backend && ./mvnw test -Dtest=ParentMessageTests`. Expected: the first fails (2 rows), the third fails (200).
- [ ] **Step 3: Implement.** Change the signature to `sent(CurrentUser user, Kind kind, Integer limit, UUID studentId)`; the announcements call passes `null`. Add `AND (CAST(:student AS uuid) IS NULL OR m.student_id = CAST(:student AS uuid))` to the query. The controller takes `@RequestParam(required = false) UUID studentId`; a bad UUID already maps to 400 through the existing handler. If it does not, map it to `request.malformed` on `studentId`.
- [ ] **Step 4: Run them.** Same command. Expected: all ParentMessageTests pass.
- [ ] **Step 5: Docs.** In SPEC §9, the `GET /messages` row gets "optional `studentId`: that student's messages (D41)". Add a PLAN row: `D41 | DM navigation: Android tabs Today, Students, Messages, More; web sidebar with Rapport de la journée; parity of dashboard, settings and report`.
- [ ] **Step 6: Commit.** `D41, UC-D4: the DM lists the messages sent about one student`.

### Task 2: Android: four DM tabs and tab-relative screens

**Files:**
- Modify: `mobile/lib/features/shell/destinations.dart`, `mobile/lib/features/shell/home_shell.dart`, `mobile/lib/router.dart`
- Create: `mobile/lib/features/shell/tab_path.dart`
- Modify: `mobile/lib/features/dm/student_search_screen.dart`, `mobile/lib/features/dm/messages_screen.dart`, and every `'/home/alerts/…'` push (alert_screen, day_screen ×2, dm_home_screen, messages_screen, student_history_screen, student_search_screen)
- Modify: `mobile/lib/l10n/app_fr.arb`, `app_en.arb`
- Test: `mobile/test/features/dm_navigation_test.dart` (new), `mobile/test/features/routing_test.dart`. Also update the DM tests that open screens through the old home (`dm_test`, `dm_day_test`, `dm_messages_test`, `full_day_screen_test`) so they use the new tabs and keys.

**Interfaces:**
- Produces:
  - `enum Destination { sessions, gate, today, students, messages, cases, children, inbox, more }`. `today`, `students`, `messages` and `more` have `Role.dm`; `alerts` is removed. The paths are the names. The icons are `today_outlined/today`, `school_outlined/school`, `forum_outlined/forum` and `more_horiz/more_horiz`.
  - `List<Destination> barFor(List<Destination> all)`: `all` when `all.length <= 5`. Otherwise the first four that are not `more`, followed by `more`.
  - `List<Destination> overflowOf(List<Destination> all)`: the destinations in `all` that are not in `barFor(all)`.
  - `String underTab(BuildContext context, String sub)` returns `'/home/<current tab>/<sub>'`, with the tab taken from `GoRouterState.of(context).uri.pathSegments[1]`.
  - `class StudentSearchView` is the search body without a Scaffold. `class SentMessagesView` is the sent-messages body without a Scaffold.
  - `Future<void> signOutChecked(BuildContext context, WidgetRef ref)` is the current `_signOut`, made public, with the pending count read inside.
  - ARB keys: `navToday` "Aujourd'hui"/"Today", `navStudents` "Élèves"/"Students", `navMessages` "Messages"/"Messages", `navMore` "Plus"/"More".

- [ ] **Step 1: Write the failing tests** in `dm_navigation_test.dart`:
  - `a DM has four tabs in order`: a DM user. The bar labels are `Aujourd'hui, Élèves, Messages, Plus`, and the start route is `/home/today`.
  - `a DM who teaches keeps the teacher tab first`: roles TEACHER and DM give `Séances` first, then the four DM tabs.
  - `overflow goes to More` (unit test on `barFor`/`overflowOf`): with TEACHER, DM and PARENT, `barFor` is `[sessions, today, students, messages, more]` and `overflowOf` is `[children, inbox]`. In `routing_test`, `redirectFor(user, '/home/children')` is null for that user.
  - `the Students tab searches without its own app bar`: tap `Élèves`, find the search field, and find exactly one `AppBar`.
  - `a pushed screen stays under its tab`: from the Students tab, search and tap a student. The location is `/home/students/student/<id>` and the selected index is still `Élèves`. Going back shows the search again.
  - `the Messages tab lists sent messages and writes a new one`: tap `Messages`, see a sent row, tap `dm-new-message` → location `/home/messages/write`.
  - `reads in English (NFR-12)`: the labels are `Today, Students, Messages, More`.
  - In `routing_test`, replace `'/home/alerts'` with `'/home/today'`.
- [ ] **Step 2: Run them.** `cd mobile && flutter test test/features/dm_navigation_test.dart test/features/routing_test.dart`. Expected: compile errors or failures on the missing destinations.
- [ ] **Step 3: Implement.**
  - The shell builds its `NavigationBar` from `barFor(destinationsFor(roles))`. When the current destination is in the overflow, the bar selects `more`.
  - The bodies are: `today` → `DmHomeScreen` (temporary, replaced in Task 3), `students` → `StudentSearchView`, `messages` → `SentMessagesView`, `more` → the placeholder (replaced in Task 4).
  - On `messages` the shell's Scaffold carries a `FloatingActionButton.extended` (key `dm-new-message`, the existing "new message" label) that pushes `underTab(context, 'write')`.
  - Remove the `students` and `messages` subroutes. Every `'/home/alerts/x'` push becomes `underTab(context, 'x')`.
  - Run `flutter gen-l10n` after the ARB edit.
- [ ] **Step 4: Run them, then the whole suite.** `flutter test && flutter analyze`. Expected: all pass, analyze clean. Fix the four DM tests by opening through the tabs. Their assertions do not change.
- [ ] **Step 5: Commit.** `D41: the DM's Android tabs are Today, Students, Messages and More`.

### Task 3: Android: the Today tab

**Files:**
- Create: `mobile/lib/features/dm/today_screen.dart` (`TodayScreen`), `mobile/lib/features/dm/alerts_list.dart` (`AlertList`, moved from `dm_home_screen.dart`, and `OpenAlertsScreen`)
- Delete: `mobile/lib/features/dm/dm_home_screen.dart`
- Modify: `home_shell.dart` (`today` → `TodayScreen`), `router.dart` (subroute `alerts` → `OpenAlertsScreen`), ARB files
- Test: `mobile/test/features/dm_today_test.dart` (new); move the alert-list assertions of `dm_test` to it

**Interfaces:**
- Consumes: `openAlertsProvider`, `dayProvider(null)` (its `totals.notRecorded`, `totals.absentStudents`, `totals.lateness`), and `underTab` (Task 2).
- Produces:
  - Card keys `today-alerts` → `underTab('alerts')`, `today-not-recorded` → `underTab('day')`, `today-absences` → `underTab('day')`, and `open-full-day` (kept) → `underTab('full-day')`.
  - The `open-search` field in the header → `context.go('/home/students')`.
  - ARB keys (EN in brackets):
    - `todayAlerts` `{count, plural, =0{Aucune alerte à traiter} =1{1 alerte à traiter} other{{count} alertes à traiter}}` ["No alert to decide" / "1 alert to decide" / "{count} alerts to decide"].
    - `todayNotRecorded` `{count, plural, =0{Toutes les périodes sont saisies} =1{1 période non saisie} other{{count} périodes non saisies}}` ["Every period recorded" / "1 period not recorded" / "{count} periods not recorded"].
    - `todayAbsences` `{absent, plural, =1{1 absent} other{{absent} absents}} · {late, plural, =1{1 retard} other{{late} retards}}` ["{absent} absent · {late} late", with the same plural forms].
    - `openAlertsTitle` "Alertes à traiter"/"Alerts to decide".

- [ ] **Step 1: Write the failing tests** in `dm_today_test.dart`, against a FakeServer with 3 open alerts and day totals `{notRecorded: 2, absentStudents: 14, lateness: 6}`:
  - `today shows the three counts`: the texts are `3 alertes à traiter`, `2 périodes non saisies` and `14 absents · 6 retards`.
  - `each card opens its list`: `today-alerts` leads to `/home/today/alerts`, which lists the three alerts. `today-not-recorded` leads to `/home/today/day`.
  - `a failed count offers a retry, never zero`: `GET /attendance/day` returns 500. The alerts card still says `3 alertes à traiter`. The day cards show a retry button (key `today-retry-day`) and the text `0` does not appear. Tapping the retry calls the endpoint again.
  - `pull to refresh reads both again`: after `fling` down, `GET /alerts` and `GET /attendance/day` have each been sent twice.
  - `reads in English (NFR-12)`: `3 alerts to decide`, `14 absent · 6 late`.
- [ ] **Step 2: Run them.** `flutter test test/features/dm_today_test.dart`. Expected: FAIL, `TodayScreen` is missing.
- [ ] **Step 3: Implement.** A `HeaderBand` with the date and the search field. Below it, three tappable cards, each with a skeleton while loading and `FailureView`-style retry on error. Then the full-day report entry. `OpenAlertsScreen` is a Scaffold titled `openAlertsTitle` around `AlertList` with the existing empty state. The gate lateness list that sat on the old DM home goes away, because the day view lists each class's lateness.
- [ ] **Step 4: Run the whole suite.** `flutter test && flutter analyze`. Expected: all pass.
- [ ] **Step 5: Commit.** `D41, UC-D2, UC-D10: the DM's Today tab counts alerts, missing roll calls and the day's absences`.

### Task 4: Android: the More tab and decided alerts

**Files:**
- Create: `mobile/lib/features/dm/more_screen.dart` (`MoreScreen`), `mobile/lib/features/dm/closed_alerts_screen.dart` (`ClosedAlertsScreen`)
- Modify: `dm.dart`:
  - `RiskAlert` gains `decision` (String?), `decisionNote` (String?) and `decidedAt` (DateTime?, local);
  - add `closedAlerts()` (`GET /alerts?status=closed`) and `closedAlertsProvider`.
- Modify: `home_shell.dart` (`more` → `MoreScreen(overflow: overflowOf(all))`), `router.dart` (subroutes `alerts/closed`, plus `dashboard` and `settings` pointing to placeholders until Tasks 5 and 6), ARB files
- Test: `mobile/test/features/dm_more_test.dart`

**Interfaces:**
- Consumes: `overflowOf`, `signOutChecked`, `underTab` (Task 2).
- Produces:
  - Tile keys: `more-closed-alerts`, `more-dashboard`, `more-settings`, `more-sign-out`, and `more-tab-<path>` for each overflow destination (it opens `/home/<path>`).
  - ARB keys: `closedAlertsTitle` "Alertes traitées"/"Decided alerts", `dashboardTitle` "Tableau de bord"/"Dashboard", `settingsTitle` "Paramètres"/"Settings", `decidedOn` "Décidée le {date} : {decision}"/"Decided on {date}: {decision}".
  - Decision labels reuse the existing `decisionSummon` / dismiss / disciplinary / refer strings.

- [ ] **Step 1: Write the failing tests:**
  - `More lists the DM's other screens`: the four tiles are present.
  - `decided alerts show the decision`: `GET /alerts?status=closed` returns one `SUMMON` alert decided `2026-11-05T09:00:00Z`. The screen shows the student's name and `Décidée le …` with the summon label, and the query sent is `{status: closed}`.
  - `no decided alert says so`: an empty list shows an `EmptyState`.
  - `sign out from More`: tapping `more-sign-out` signs out (the location becomes `/login`).
  - `overflow tabs are listed`: TEACHER, DM and PARENT give `more-tab-children` and `more-tab-inbox`, and tapping the first opens `/home/children` with `Plus` selected in the bar.
  - English labels.
- [ ] **Step 2: Run them.** `flutter test test/features/dm_more_test.dart`. Expected: FAIL.
- [ ] **Step 3: Implement** to the interfaces above: a list of `ListTile`s in cards, 48 dp, with the overflow destinations first.
- [ ] **Step 4: Run the whole suite.** `flutter test && flutter analyze`. Expected: pass.
- [ ] **Step 5: Commit.** `D41, UC-D2: the DM's More tab, with decided alerts and sign-out`.

### Task 5: Android: the scope dashboard

**Files:**
- Create: `mobile/lib/features/dm/dashboard/scope_dashboard_models.dart`, `scope_dashboard_screen.dart` (`ScopeDashboardScreen`)
- Modify: `dm.dart` (`scopeDashboard(DateTime from, DateTime to)` → `GET /dashboard/scope?from&to`, and `scopeDashboardProvider` as a family over a `({DateTime from, DateTime to})` record), `router.dart` (`dashboard`), ARB files
- Test: `mobile/test/features/dm_dashboard_test.dart`

**Interfaces:**
- Consumes: the JSON of `web/src/api/dm.ts` `ScopeDashboard`: `totals: Counts`, `classes[{classId, name, counts}]`, `alerts[{rule, open, raised, closed}]`. `days` is not shown on mobile.
- Produces: `class Counts {sessions, studentPeriods, absentPeriods, unjustifiedAbsentPeriods, lateness, unjustifiedLateness}`, with `double? get absenceRate` (null when `studentPeriods == 0`).

- [ ] **Step 1: Write the failing tests:**
  - `the last 30 days by default`: the clock is 2026-11-10, so the query sent is `{from: 2026-10-12, to: 2026-11-10}`, the same default as the web.
  - `the figures`: `studentPeriods 400, absentPeriods 20` shows `5 %` (FR) and `5%` (EN). The unjustified absences, the lateness and the open-alert sum are shown.
  - `classes, most absences first`: two classes come back unordered and are shown sorted by `absentPeriods` descending.
  - `another range`: the `dashboard-range` picker sends the new `from/to`.
  - `no lesson in the range`: `studentPeriods 0` shows `—` for the rate, never `0 %`.
- [ ] **Step 2: Run them.** `flutter test test/features/dm_dashboard_test.dart`. Expected: FAIL.
- [ ] **Step 3: Implement.** A range chip that opens `showDateRangePicker`, with `lastDate` today. Four figure tiles in a 2×2 grid, the class list with a thin bar for each class's absent periods, and the alerts table by rule. Absences and lateness stay apart (BR-8, BR-9). Each tile is labelled, so nothing depends on colour alone.
- [ ] **Step 4: Run the whole suite.** Expected: pass, analyze clean.
- [ ] **Step 5: Commit.** `D41, UC-D8: the DM's scope dashboard on Android`.

### Task 6: Android: thresholds and digest settings

**Files:**
- Create: `mobile/lib/features/dm/settings/settings_models.dart`, `settings_screen.dart` (`SettingsScreen`)
- Modify: `dm.dart` (`settings()` → `GET /settings/thresholds`; `saveSettings(SettingsRequest)` → `PUT /settings/thresholds`), `router.dart` (`settings`), ARB files
- Test: `mobile/test/features/dm_settings_test.dart`

**Interfaces:**
- Consumes: the JSON of `Settings` and `SettingsRequest` in `web/src/api/dm.ts`.
- Produces: `String? settingsError(...)`, which returns the first problem with the form, or null. It checks the bounds in Global Constraints.

- [ ] **Step 1: Write the failing tests:**
  - `the current settings are shown`: the start time `07:30`, two digest times, and three rules with their switch and count.
  - `saving sends the whole form`: change the `FULL_DAY_ABSENCE` count from 1 to 2 with the stepper and tap Save. `PUT /settings/thresholds` receives `{startTime: "07:30", digestTimes: [...], rules: [{type, count: 2, active}, …]}`, and a snackbar says it was saved.
  - `an out-of-order digest blocks saving`: set the second digest to `06:30` when the first is `12:00`. The order message shows and the Save button is disabled.
  - `a count stays within its bounds`: at `max`, the + button is disabled.
  - `a server refusal is shown`: a 400 with a message shows that message.
  - English labels.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement.** Pick times with `showTimePicker`. Each rule gets a switch and a −/+ stepper (disabled when the rule is off). The FR/EN copy is the same as the web's `dm.settings*` strings, carried into ARB keys `settings*`.
- [ ] **Step 4: Run the whole suite.** Expected: pass.
- [ ] **Step 5: Commit.** `D41, UC-D7: thresholds and digest times on Android`.

### Task 7: Android: the student page's Messages tab and one action button

**Files:**
- Modify: `mobile/lib/features/dm/student_history_screen.dart`, `dm.dart` (`sentMessages({String? studentId})` and `studentMessagesProvider` as a family by student id), ARB files
- Test: `mobile/test/features/dm_student_page_test.dart`; the summon and message tests in `dm_test` and `dm_messages_test` move to the new action menu

**Interfaces:**
- Consumes: `GET /messages?studentId=` (Task 1), `SentMessagesView`'s row widget (Task 2; extract it as `SentMessageTile` if it is private).
- Produces:
  - A third tab "Messages". The two app-bar icons are replaced by one `FloatingActionButton.extended` (key `student-actions`, label "Agir"/"Act") that opens a sheet with `action-incident`, `action-message` and `action-summon`.
  - `action-incident` switches to the Discipline tab and opens the existing record sheet.

- [ ] **Step 1: Write the failing tests:**
  - `three tabs`: the tabs are `Historique`, `Discipline` and `Messages`.
  - `the Messages tab lists this student's messages`: `GET /messages` is sent with query `{studentId: st-1}`, and the rows are shown. An empty list shows an `EmptyState` whose text tells the DM to use "Agir".
  - `one action button`: tap `student-actions`. `action-message` pushes `…/write?studentId=st-1`, and `action-summon` opens the summon sheet.
  - `record an incident from the action button`: `action-incident` shows the Discipline tab and its record sheet.
- [ ] **Step 2: Run them.** Expected: FAIL.
- [ ] **Step 3: Implement** as in Interfaces.
- [ ] **Step 4: Run the whole suite.** Expected: pass, analyze clean.
- [ ] **Step 5: Commit.** `D41, UC-D4, UC-D5: the student page gets a Messages tab and one action button`.

### Task 8: Web: the student page's Messages tab

**Files:**
- Modify: `web/src/pages/dm/StudentHistoryPage.tsx`, `web/src/api/messages.ts` (`sentMessagesPath(studentId?: string)`), router (`/students/:id/messages`), `web/src/i18n` FR/EN
- Test: `web/src/pages/dm/discipline.test.tsx` (or a new `student-messages.test.tsx`)

**Interfaces:**
- Consumes: `GET /messages?studentId=` (Task 1), `SentMessagesTable` (existing).
- Produces: the tab `value="messages"` links to `${base}/messages`. Its "Écrire aux parents" button goes to the existing compose page for that student.

- [ ] **Step 1: Write the failing tests:**
  - `the Messages tab reads this student's messages`: `/students/s1/messages` sends `GET /messages?studentId=s1`, the tab is selected, and the table shows the row.
  - `no message yet`: shows the empty state with the write button.
  - `English`: the tab label is "Messages".
- [ ] **Step 2: Run them.** `cd web && npx vitest run --maxWorkers=2 src/pages/dm`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run them.** `npm run lint && npx vitest run --maxWorkers=2 && npm run build`. Expected: all pass.
- [ ] **Step 5: Commit.** `D41, UC-D4: the web student page lists the messages about the student`.

### Task 9: Web: the full-day report page

**Files:**
- Create: `web/src/api/report.ts` (types matching the JSON keys of `mobile/lib/features/dm/full_day/full_day_models.dart`, `REPORT_PATH`, `reportPath(date)`, `DEFAULT_DATE_PATH`), `web/src/pages/dm/ReportPage.tsx`, `web/src/pages/dm/report.test.tsx`
- Modify: `web/src/components/AppLayout.tsx` (the `DM_NAV` item `{ to: '/report', label: 'nav.report', icon: FileTextIcon }` between Messages and Settings; `className="no-print"` on the sidebar and top bar), the router (`/report`, DM only), `web/src/index.css` or the theme's global styles (`@media print`: hide `.no-print`, the sheet at full width with no shadow), i18n FR/EN

**Interfaces:**
- Consumes: `GET /reports/full-day-absences/default-date` → `{date}`, `GET /reports/full-day-absences?date=`, `GET /school/letterhead`, the logo through `fetchLogo`, and `LetterheadPreview`.
- Produces:
  - i18n keys: `nav.report` "Rapport de la journée"/"Day report", and `report.*`.
  - The `report.*` copy is the Android ARB `report*` strings carried over. They are title "Élèves absents toute la journée", columns "N°", "Nom et prénom", "Matricule", the class heading with a count, the "Heures non saisies…" line, footer "Généré par SchoolLink le {date} à {time}", and the nobody message.
  - The button is `report.print` "Imprimer ou enregistrer en PDF"/"Print or save as PDF".

- [ ] **Step 1: Write the failing tests** in `report.test.tsx`:
  - `opens on the default day`: `/report` reads the default date and then that day's report. The `Jour` field holds it and the URL becomes `?date=…`.
  - `the sheet`: it shows the title, the date spelt out, one table per class with numbered rows, and the unrecorded line when `unrecorded` is not empty.
  - `prints`: clicking "Imprimer ou enregistrer en PDF" calls `window.print` once (spy).
  - `nobody absent: no sheet, no print`: when `blocks` is empty, the nobody message shows and there is no print button.
  - `a refused day shows the server's message`: a 400 with `report.day_not_finished` (the existing code) shows that message and no print button.
  - `DM menu only`: the link `Rapport de la journée` points to `/report`. An ADMIN on `/report` is sent to `/`.
  - `English`.
- [ ] **Step 2: Run them.** `npx vitest run --maxWorkers=2 src/pages/dm/report.test.tsx`. Expected: FAIL.
- [ ] **Step 3: Implement.** Draw an A4-proportioned sheet (`Paper`, 210 mm wide on print) with the letterhead preview at the top. Use the page header and date field above the sheet, with the date field limited to today at most. Printing goes through the browser, whose dialog also saves as PDF, so no PDF library is added.
- [ ] **Step 4: Run them.** `npm run lint && npx vitest run --maxWorkers=2 && npm run build`. Expected: all pass. Then check it in the real app: open `/report` in Playwright against the dev backend and take a print-media screenshot (`browser_emulate_media print`) to confirm the chrome is hidden.
- [ ] **Step 5: Commit.** `D41, UC-D9: the full-day report on the web, to print or save as PDF`.

---

## Calls in this plan to check

1. **The web report prints through the browser.** Its print dialog's "Save as PDF" is the download, so no PDF library is added and the page needs no backend PDF.
2. **The student page's Messages tab lists only messages sent to that student,** not class messages that reached their parents too.
3. **The gate lateness list leaves the DM's Android home.** The day view shows each class's lateness.
4. **On the web, the student page keeps its current action buttons.** Only the Messages tab is added. On Android, the two icons become one "Agir" button with a menu, as §5.1 asks.
5. **With more than five tabs, the bar shows the first four and "Plus".** The rest are listed at the top of "Plus".
