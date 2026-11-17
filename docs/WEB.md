# SchoolLink web dashboard

This document describes the web dashboard as it is built today: who uses it, how sign-in and the session work, every page with the API calls behind it, and the shared building blocks. It is written from the code under `web/src`.

Related documents:

- [API.md](API.md): every endpoint the dashboard calls, with its fields.
- [ARCHITECTURE.md](ARCHITECTURE.md) §5: where the web fits in the system.
- [MOBILE.md](MOBILE.md): the Android app, which shares the DM's features.
- [SPEC.md](SPEC.md) and [PLAN.md](PLAN.md): the rules (BR, UC, NFR) and decisions (D) cited below.

## Contents

1. [At a glance](#1-at-a-glance)
2. [Sign-in and session](#2-sign-in-and-session)
3. [Routes and menu](#3-routes-and-menu)
4. [Pages for the administrator](#4-pages-for-the-administrator)
5. [Pages for the DM](#5-pages-for-the-dm)
6. [The API layer](#6-the-api-layer)
7. [Shared building blocks](#7-shared-building-blocks)
8. [Languages](#8-languages)
9. [Tests and commands](#9-tests-and-commands)

---

## 1. At a glance

- **Who uses it:** the administrator (school set-up, staff, import, announcements, school dashboard) and the DM (the desk work also available on Android, D41).
- **Other roles:** the counsellor and the researcher are web roles too, but their pages are not built yet: they see a welcome page with their roles. A teacher, assistant DM or parent who signs in also sees a note that their work is in the mobile app.
- **Stack:** React, Vite, TypeScript, MUI for components, Recharts for charts, i18next for French and English, React Router.
- **No server-side rendering and no state library:** each page fetches what it shows with two small hooks.

```
src/
  main.tsx, App.tsx     start-up: theme, i18n, auth, router
  routes.tsx            every route and its role
  api/                  one HTTP client and one module per area of the API
  auth/                 the auth context, the route guards, the remembered school
  components/           layout, menu, shared components, dashboard charts
  hooks/                useResource, useList, useLogoUrl
  pages/                one folder per feature
  i18n/                 fr.json, en.json
  test/                 the fake API and test setup
  theme.ts              the MUI theme
  format.ts, icons.ts   date and number formats, the icon set
```

---

## 2. Sign-in and session

### 2.1 Sign-in (`LoginPage`)

- Fields: phone or email, password (D15).
- The school picker appears only when needed (D16): when the account exists in several schools, or when a school was picked before on this device (`schoolMemory`, kept in `localStorage`).
- Calls: `GET /auth/schools`, `POST /auth/login`.
- Parents do not sign in here: they use the SMS code in the mobile app.

### 2.2 First password (`ChangePasswordPage`, D18)

While the password is temporary, the server answers only `/me` and `/me/password`. `RequireAuth` therefore sends every page to the password page until it is changed (`POST /me/password`).

### 2.3 The session (`api/client.ts`, `auth/AuthContext.tsx`)

- The **access token lives in memory only**.
- The **refresh token is in `sessionStorage`**: reloading the page keeps the session, closing the tab ends it (NFR-7).
- On a 401 the client refreshes once, shared by the requests that failed together, then retries.
- Signing out calls `POST /auth/logout`, so the refresh token stops working on the server.
- `AuthContext` gives every page the status (`loading`, `signedIn`, `signedOut`) and the user; `useAuth()` reads it.

### 2.4 Guards

- `RequireAuth` wraps every signed-in page: no session sends to `/login`, and it remembers where the user was going.
- `RequireRole` wraps each page with the role it needs (`ADMIN` or `DM`). A user without it is sent to the home page.
- The guards are for navigation only. **The server checks every request again** (NFR-8).

---

## 3. Routes and menu

### 3.1 Routes (`routes.tsx`)

| Path | Page | Role |
| --- | --- | --- |
| `/login` | `LoginPage` | Public |
| `/` | `HomePage` | Any signed-in user |
| change-password path | `ChangePasswordPage` | Any signed-in user |
| `/dashboard` | `SchoolDashboardPage` | Admin |
| `/setup` | `SchoolSettingsPage` | Admin |
| `/structure/calendar` | `CalendarPage` | Admin |
| `/structure/periods` | `PeriodsPage` | Admin |
| `/structure/classes` | `ClassesPage` | Admin |
| `/structure/timetable` | `TimetablePage` | Admin |
| `/structure/letterhead` | `LetterheadPage` | Admin |
| `/staff`, `/staff/:id` | `StaffListPage`, `StaffDetailPage` | Admin |
| `/school/students`, `/school/students/:id` | `StudentsAdminPage`, `StudentAdminPage` | Admin |
| `/students/import`, `/students/import/:id` | `ImportUploadPage`, `ImportPreviewPage` | Admin |
| `/guardians` | `GuardiansPage` | Admin |
| `/announcements`, `/announcements/new` | `AnnouncementsPage`, `ComposeAnnouncementPage` | Admin |
| `/day` | `DayPage` | DM |
| `/report` | `ReportPage` | DM |
| `/alerts` | `AlertsPage` | DM |
| `/students` | `StudentsPage` | DM |
| `/students/:id`, `/students/:id/discipline`, `/students/:id/messages` | `StudentHistoryPage` (one page, three tabs) | DM |
| `/settings` | `SettingsPage` | DM |
| `/messages`, `/messages/new` | `MessagesPage`, `ComposeMessagePage` | DM |

Any other path goes to `/`.

### 3.2 The home page (`HomePage`)

- **Admin:** `SetupOverview`, the set-up steps of the school year.
- **DM:** `ScopeDashboard`, the DM's classes.
- **Anyone else:** a welcome with their roles. A user with no web role (teacher, assistant DM, parent) also gets a note that their work is in the mobile app.

### 3.3 The menu (`AppLayout`)

The sidebar depends on the roles:

- **Admin:** Home, School dashboard, Announcements, School settings; then people (Staff, Students, Import, Guardians). The calendar, periods, classes, timetable and letterhead are reached from School settings, whose entry stays highlighted on each of them (D44).
- **DM:** Dashboard; then Day, Alerts, Students, Messages, Day report, Settings.
- **Admin who is also a DM:** the admin menu followed by the DM's group.

On a narrow screen the sidebar becomes a drawer opened from the top bar. The footer of the sidebar shows the user's roles, the language switch and the sign-out button.

---

## 4. Pages for the administrator

### 4.1 Home: set-up overview (`SetupOverview`, UC-A1, UC-A2)

Answers one question: can teachers take attendance this year? It lists the set-up steps in the order they depend on each other (calendar, periods, classes, staff, timetable, students), what each still lacks, and a ring showing how many are ready. It also names the current term, week and sequence. Missing sequences are noted on the calendar step but do not hold attendance back. The steps come from `pages/setup/useSetup.tsx`, shared with School settings.

### 4.1b School settings (`SchoolSettingsPage`, D44)

Everything that defines the school, in one place. First what is defined once, in the order to fill it in: calendar (year, terms, sequences, holidays), periods, classes, timetable, letterhead (optional). Then the people, kept up to date all year and still in the menu: staff and students. Each line says what is in place and what is missing, and opens its screen; each of those screens has a link back. Here the calendar is complete only when every term of the current year has its two sequences.

### 4.2 School structure (UC-A1)

All four pages are built on `CrudSection` (section 7.2): a list with create, edit and delete, where the server checks every rule and its translated messages appear on the matching field.

| Page | What it manages | API |
| --- | --- | --- |
| `CalendarPage` | Academic years, their terms, the two sequences (D44) and the holidays (D10) of each term. A new sequence is proposed with the next number and a label counted across the year (Séquence 1 to 6) | `/academic-years`, `/terms`, `/sequences`, `/holidays` |
| `PeriodsPage` | The lesson periods of the day | `/periods` |
| `ClassesPage` | Blocks (D5) and classes, per academic year | `/blocks`, `/classes` |
| `TimetablePage` | One class's week, Monday to Friday by period (D1) | `/timetable-slots`, `/classes`, `/periods`, `/staff` |

Timetable rules shown to the admin: a lesson can only be given by a teacher assigned to that class for that subject (BR-1), and the server refuses a teacher who is busy in another class at that time.

### 4.3 Letterhead (`LetterheadPage`, UC-A7, D36)

- The admin chooses the layout (bilingual, French only, English only), types the lines of each column and uploads a logo.
- `LetterheadPreview` shows the result on a sketched page as they type.
- API: `GET` and `PUT /school/letterhead`, `PUT` and `DELETE /school/letterhead/logo`.
- The DM's day report uses the same header (`LetterheadHeader`), without the sketch.

### 4.4 Staff (UC-A2)

| Page | What it does | API |
| --- | --- | --- |
| `StaffListPage` | Every staff member with roles and status; create a new one. | `GET /staff`, `POST /staff` |
| `StaffDetailPage` | Identity, status, temporary password, roles with their block, and for a teacher the classes and subjects taught. | `GET`, `PUT /staff/{id}`, `PUT /staff/{id}/status`, `POST /staff/{id}/password-reset`, roles and teaching calls |

- Creating an account, or resetting a password, shows the temporary password once (`TemporaryPasswordDialog`) for the admin to hand over (D18).
- A DM or assistant DM is given a block; the server allows one DM per block (D5).

### 4.5 Student import (UC-A3, D19)

Three steps, shown by `ImportSteps`:

1. **Upload** (`ImportUploadPage`): choose the academic year and a CSV or Excel file. A template can be downloaded (`public/import-template.csv`, documented in [IMPORT.md](IMPORT.md)). `POST /students/import`.
2. **Preview** (`ImportPreviewPage`): the checked file with its errors, grouped by row. `GET /students/import/{id}`.
3. **Commit:** only a file without errors commits, all or nothing and once. `POST /students/import/{id}/commit`. With errors, the admin corrects the file and uploads again.

### 4.5b Students and their guardians (UC-A8, D43)

After the import the admin keeps the lists right here. The DM's own student pages stay at `/students`; these are at `/school/students`.

- **List** (`StudentsAdminPage`): a year and a class, or a search by name or matricule across the school (two characters, after a short pause). Students who left show only with the switch, greyed, with the day they left. **Add a student** opens a form with the details and one or two guardians, then the new student's page. **Move the class up** is the end of the year: all the students of the class are ticked, the admin unticks those who repeat, chooses the class of any year, and they move in one step.
- **Student** (`StudentAdminPage`): the details, the class, and the guardians with the primary first, their phone, language and whether they use the app. Actions: edit the record, change class, mark as left (with the date) or re-enrol. Per guardian: edit (name, phone, language, relation, primary) and remove; the only guardian cannot be removed, and removing the primary asks who takes over when several stay.
- **Saying what changes first:** a class change, a departure and a removal each state their effect in the dialog. Editing the phone of a guardian who uses the app warns that they are signed out (D24); a guardian with several children shows that the change touches them all.
- The forms are in `pages/students/dialogs.tsx`; a refusal is shown on its field when the server names one, else at the top of the dialog.

### 4.6 Guardians (`GuardiansPage`, UC-A4)

A class's guardians, their children, and who has signed in to the app. Nothing is sent from here: a parent signs in by themselves with an SMS code. API: `GET /guardians?classId=`.

### 4.7 Announcements (UC-A5, D38)

- `AnnouncementsPage`: the announcements sent, with delivery counts. `GET /announcements`.
- `ComposeAnnouncementPage`: writes to the parents of the whole school or one class, using the shared `MessageComposer` (section 5.7). `POST /announcements/preview`, `POST /announcements`.

### 4.8 School dashboard (`SchoolDashboardPage`, UC-A6)

The whole school over a range, the last 30 days by default:

- attendance and lateness day by day, kept apart (BR-8, BR-9);
- the classes with their counts;
- alerts per rule;
- how many notifications reached the parents (BR-24);
- this month's SMS against the school's cap (BR-25). At the cap, only summons still go by SMS;
- whether sending works right now (NFR-6): a warning when sends failed in the last hour or have waited more than 10 minutes, and the time of the last SMS digest.

API: `GET /dashboard/school?from=&to=`. The charts (`TrendCharts`) are loaded lazily, so Recharts is not in the first download.

---

## 5. Pages for the DM

The DM's calls are in `api/dm.ts`, `api/day.ts`, `api/report.ts` and `api/messages.ts`. The small forms (justify, cancel, summon, decision note, incident) are dialogs in `pages/dm/dialogs.tsx`.

### 5.1 Home: scope dashboard (`ScopeDashboard`, UC-D8)

The DM's classes over a range, with a link to the open alerts. API: `GET /dashboard/scope?from=&to=`.

### 5.2 Day (`DayPage`, UC-D10, D40)

- One day of the DM's classes, period by period: recorded with its absentees, not recorded, or upcoming; then each class's lateness.
- "Not recorded" is information only: the DM never records a roll call (BR-4).
- The DM ticks several records to justify at once (BR-12), or cancels one (BR-13). Cancelled rows stay listed, struck through, never counted (BR-15).
- The date is in the address (`/day?date=2026-11-12`), so a day can be linked to.
- API: `GET /attendance/day?date=`, `POST /justifications`, `POST /records/{id}/cancel`, `POST /lateness/{id}/cancel`.

### 5.3 Alerts (`AlertsPage`, UC-D2)

- Open alerts, the longest waiting first, with what the rule saw in words; decided alerts keep who decided what and why.
- Each decision opens its dialog. The page never decides by itself (BR-16 to BR-19).
- API: `GET /alerts?status=`, `POST /alerts/{id}/decision`.

### 5.4 Students (`StudentsPage`, `StudentHistoryPage`)

- `StudentsPage`: search a student of the DM's classes by name or matricule. `GET /lateness/students?q=`.
- `StudentHistoryPage` has three tabs, each at its own address:
  - **History** (UC-D8): absences and lateness, newest first. Justify several records, cancel one, or summon the parents. `GET /students/{id}/history`, `POST /justifications`, the two cancel calls, `POST /summons`.
  - **Discipline** (`DisciplineTab`, UC-D5, D37): record, close or cancel an incident. `/students/{id}/disciplinary-records`, `/disciplinary-records/{id}/close`, `…/cancel`.
  - **Messages** (`StudentMessages`, D41): the messages sent to this student's parents, with a button to write one. `GET /messages?studentId=`.

### 5.5 Day report (`ReportPage`, UC-D9, D41)

- Opens on the default day (today from 17:00, otherwise the previous school day), and the DM can pick another.
- Shows the students absent the whole day under the school letterhead, by block and class, with the classes whose lessons were not recorded.
- "Print or save as PDF" uses the browser's print. In print, only the sheet is on the page.
- The Print button appears only once the sheet can show. The footer gives when the day shown was loaded.
- When nobody was absent, the page says so, and still names the lessons not recorded.
- API: `GET /reports/full-day-absences/default-date`, `GET /reports/full-day-absences?date=`, `GET /school/letterhead`, the logo.

### 5.6 Settings (`SettingsPage`, UC-D7)

The alert rules (count and on/off, within each rule's bounds), the start of school, and one or two SMS digest times. They apply to the whole school. API: `GET` and `PUT /settings/thresholds`.

### 5.7 Messages (UC-D4, D38)

- `MessagesPage`: the messages the DM sent, in `SentMessagesTable`, with delivery counts. `GET /messages`.
- `ComposeMessagePage`: writes to the parents of one student (`?studentId=`) or one class.
- `MessageComposer` is shared with announcements:
  - the form on the left; on the right, what parents will see (the app notification, and the SMS for those without the app), who gets it and what it costs;
  - the preview is asked once the author pauses typing;
  - sending asks for confirmation;
  - the message id is made once, so a double click or a retry sends once.
- API: `GET /messages/classes`, `POST /messages/preview`, `POST /messages`.

---

## 6. The API layer

### 6.1 The client (`api/client.ts`)

One class does every HTTP call to `/api/v1`:

- sends JSON, or multipart when the body is a `FormData` (file uploads);
- adds the access token and `Accept-Language`;
- refreshes once on a 401 and retries;
- can fetch a file as a blob (the school logo), answering null on a 404;
- turns failures into three kinds of error:
  - `ApiError`: the server's error (`code`, `message`, `field`, `details`), with a helper that gives the message for one field;
  - a network error: no answer from the server, the UI shows its own message;
  - a non-JSON answer, such as a proxy error page.

In development Vite proxies `/api` to `http://localhost:8080` (or `API_PROXY`).

### 6.2 One module per area

| Module | Covers |
| --- | --- |
| `auth.ts` | Schools, login, `/me`, password change |
| `structure.ts` | Years, terms, holidays, periods, blocks, classes, timetable |
| `staff.ts` | Staff accounts, roles, teaching |
| `imports.ts` | Student import: upload, preview, commit |
| `students.ts` | Students and guardians kept up to date: list, add, correct, move, leave, return, guardian links |
| `guardians.ts` | Guardians of a class |
| `letterhead.ts` | Letterhead and logo |
| `dashboard.ts` | School dashboard |
| `dm.ts` | Alerts, decisions, history, justification, cancellation, summons, discipline, settings, scope dashboard, student search |
| `day.ts` | The day of attendance |
| `report.ts` | The full-day report |
| `messages.ts` | Messages and announcements: classes, preview, send, list |
| `errors.ts` | Turning an error into the text to show |

Each module exports the TypeScript types of its answers and small functions that call the client.

---

## 7. Shared building blocks

### 7.1 Data hooks (`hooks/`)

- `useResource(path)` GETs one object; `useList(path)` GETs a list. A null path waits (for example until a class is picked).
- Both return the data, the error, a loading flag and a `reload` function. `useResource` also returns `loadedAt`, the time the answer arrived.
- `useLogoUrl` fetches the logo as an object URL, again when its version changes, and releases the old one.

### 7.2 Components (`components/`)

| Component | Role |
| --- | --- |
| `AppLayout` | The sidebar, the top bar on narrow screens, the page area |
| `PageHeader` | The title, description and actions of a page |
| `CrudSection` | A list with create, edit and delete, and its form dialog (used by the structure pages) |
| `ConfirmDialog` | "Are you sure?" before a delete or a send |
| `YearPicker` | The academic year selector |
| `Pill`, `PersonAvatar`, `Mark` | A status word in a pill, initials in a circle (no photos, NFR-10), the logo mark |
| `LanguageSwitch` | French or English |
| `dashboard/*` | The figures, parts and charts of both dashboards |

### 7.3 Theme

`theme.ts` defines the MUI theme: colours, type, component defaults. Status is always a word, never colour alone.

---

## 8. Languages

- Every text is in `i18n/fr.json` and `en.json`. A test checks that both files have the same keys (NFR-12).
- The language is the one chosen on this device (`localStorage`), else the browser's if it is French or English, else French.
- Error messages and notification texts come from the server, already translated.

---

## 9. Tests and commands

- `test/fakeApi.tsx`: a fake `fetch` that answers from a script (`'GET /alerts?status=open': json(200, …)`); an unknown route fails the test. `renderApp` starts the whole app at a given path with a signed-in client.
- Tests sit next to the pages (`pages/dm/day.test.tsx`, `pages/structure/structure.test.tsx`, …) and use Vitest with Testing Library.
- Tests use generated data only; no real student data.

Commands (from `web/`):

```bash
npm ci
npm run dev        # Vite dev server, proxies /api to :8080
npm run lint       # oxlint
npm test           # Vitest
npm run build      # tsc -b, then vite build
```
