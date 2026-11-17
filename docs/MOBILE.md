# SchoolLink mobile app

This document describes the Android app as it is built today: how it starts, how it decides which screens a user gets, every screen with the API calls behind it, and how the offline parts work. It is written from the code under `mobile/lib`.

Related documents:

- [API.md](API.md): every endpoint the app calls, with its fields.
- [ARCHITECTURE.md](ARCHITECTURE.md) §4: the patterns (feature-first, Riverpod, offline sync, interceptor).
- [SPEC.md](SPEC.md) and [PLAN.md](PLAN.md): the rules (BR, UC, NFR) and decisions (D) cited below.
- [mobile/README.md](../mobile/README.md): the backend address and the build commands.

## Contents

1. [At a glance](#1-at-a-glance)
2. [Start-up and sign-in](#2-start-up-and-sign-in)
3. [Navigation](#3-navigation)
4. [Screens by role](#4-screens-by-role)
5. [The core layer](#5-the-core-layer)
6. [Offline: what works without a network](#6-offline-what-works-without-a-network)
7. [Push notifications](#7-push-notifications)
8. [Languages](#8-languages)
9. [Tests](#9-tests)

---

## 1. At a glance

- **One app for every role.** A teacher, an assistant DM, a DM and a parent install the same app. The tabs depend on the roles of the account (SPEC §8).
- **Flutter, Android only** (minimum Android 7, API 24).
- **State and dependency injection:** Riverpod. Screens read providers; tests replace them with fakes.
- **Navigation:** go_router, driven by who is signed in.
- **HTTP:** dio, with one interceptor for tokens and language.
- **Local storage:** drift (SQLite) for the sync queue and the read cache; flutter_secure_storage for the tokens.
- **Texts:** ARB files in French and English (NFR-12).

```
lib/
  main.dart          reads the saved language, starts push, runs the app
  app.dart           MaterialApp.router with the theme and the locale
  router.dart        the routes and the redirect rule
  core/              shared by every feature
    api/             ApiFailure, AuthInterceptor
    auth/            SessionStore, UserInfo, Role
    cache/           ResponseCache (last answer of read endpoints)
    db/              the drift database: sync queue and cache tables
    push/            PushClient, FirebasePushClient, PushRegistration
    storage/         DeviceStore (secure storage)
    sync/            SyncQueue, SyncScheduler and their providers
    ui/              shared widgets and loading skeletons
    theme.dart       colours, type, status colours
  features/
    auth/            sign-in, parent sign-in, first password
    shell/           the tab bar, the account menu, the sync banner
    teacher/         sessions of the day, the roll call
    lateness/        the gate
    dm/              the DM's four tabs and everything under them
    parent/          children, history, contacts, inbox
    splash/          shown while the session is restored
  l10n/              app_fr.arb, app_en.arb and the generated classes
```

Each feature keeps its models, its repository (the API calls), its providers and its screens together.

---

## 2. Start-up and sign-in

### 2.1 Start-up

1. `main.dart` reads the language chosen last time from secure storage, before the first frame.
2. It starts the push client. Without a Firebase project in the build, the client is null and the app runs without push.
3. `AuthController` restores the session from the device: the tokens and the user are stored, so the app opens **with no network** (NFR-3). While it loads, the router shows the splash screen.
4. The router then sends the user where they belong (section 3.1).

### 2.2 Staff sign-in (`LoginScreen`)

- Fields: school (picker, D16), phone or email, password.
- The picker opens on the last school used on this device, or on the only school there is.
- A French/English switch sits at the top; before sign-in the app uses this choice, afterwards the account's language.
- Calls: `GET /auth/schools`, `POST /auth/login`.
- If the account has a temporary password, the router goes to `ChangePasswordScreen` (D18), which calls `POST /me/password`. Nothing else is reachable until it is done.

### 2.3 Parent sign-in (`ParentSignInScreen`, UC-P1, D24, D45)

A parent signs in on the sign-in screen with their phone and password, like staff; no SMS. This screen is for the first time and a forgotten password, opened from "Première connexion ou mot de passe oublié".

- **Step 1:** school and phone. The number is checked on the phone first (a 9-digit Cameroon number, with or without `+237`). `POST /auth/parent/sign-in`.
- **Step 2:** the 6-digit code, with "Send a new code" and "Change the number" right under it, then the new password typed twice (8 to 72 characters). `POST /auth/parent/sign-in/verify`. The parent then lands on their children.
- The screen says "if the school knows this number, a code was sent", because the server never says whether a number is registered (NFR-7).
- "Send a new code" asks again; "Change the number" returns to step 1 with what was typed.
- When the server refuses (too many codes, SMS not sent), its message is shown under the field, and the line above keeps the validity time of the last code actually sent.
- The phone number travels in request bodies only (NFR-9).

### 2.4 Signing out

From the account menu (top right) or the DM's More tab. `signOutChecked` asks first in two cases:

- records are still waiting to be sent (D22): they stay on the phone for this user's next sign-in;
- the user is a parent (D45): they are told to come back with their phone and password.

Signing out calls `POST /auth/logout`, removes the push token and clears the session.

---

## 3. Navigation

### 3.1 The redirect rule (`redirectFor` in `router.dart`)

The router re-evaluates this rule whenever the signed-in user changes:

| State | Where the user is sent |
| --- | --- |
| Session still loading | `/splash` |
| Signed out | `/login` (or `/parent-sign-in` if already there) |
| Temporary password | `/change-password` |
| Signed in, no mobile screen (admin, researcher) | `/web-only` |
| Signed in, on a tab their roles do not give | their first tab |

### 3.2 Tabs (`destinations.dart`)

Each role gives one or more tabs, in this fixed order:

| Tab | Role | Screen |
| --- | --- | --- |
| Appel (`sessions`) | Teacher | `SessionsScreen` |
| Retards (`gate`) | Assistant DM | `GateScreen` |
| Aujourd'hui (`today`) | DM | `TodayScreen` |
| Élèves (`students`) | DM | `StudentSearchView` |
| Messages (`messages`) | DM | `SentMessagesView` |
| Suivis (`cases`) | Counsellor | a placeholder: the counsellor's screens are not built yet |
| Enfants (`children`) | Parent | `ChildrenScreen` |
| Notifications (`inbox`) | Parent | `InboxScreen` |
| Plus (`more`) | DM | `MoreScreen` |

Rules:

- A user with several roles gets the tabs of each (D21): a DM who teaches has Appel first.
- With one tab there is no tab bar.
- The bar holds five tabs at most. Past five, it shows the first four and Plus, and Plus lists the rest (D41).
- The unread count of the parent's inbox is shown on the Notifications tab, or on Plus when the inbox is listed there.

### 3.3 Screens under a tab

Every pushed screen lives under its tab: `/home/<tab>/<sub>`. `underTab(context, sub)` builds the path from the current tab, so the tab stays selected and Back returns to it (D41).

| Sub-path | Screen |
| --- | --- |
| `attendance/:classId/:periodId` | `AttendanceScreen` (the roll call) |
| `alerts`, `alerts/closed` | `OpenAlertsScreen`, `ClosedAlertsScreen` |
| `alert/:alertId` | `AlertScreen` |
| `student/:studentId` | `StudentHistoryScreen` |
| `day` | `DayScreen` |
| `full-day` | `FullDayScreen` |
| `dashboard` | `ScopeDashboardScreen` |
| `settings` | `SettingsScreen` |
| `write?studentId=` | `MessageComposerScreen` |
| `child/:childId` | `ChildHistoryScreen` |
| `message/:id` | `MessageScreen` |

### 3.4 The shell (`HomeShell`)

Around every tab: the app bar with the tab's name and the account menu, the tab bar, and at the bottom the `SyncBanner` when records are waiting to be sent, with a "Send now" button (BR-6, UC-T2).

---

## 4. Screens by role

### 4.1 Teacher (`features/teacher`)

| Screen | What it does | API |
| --- | --- | --- |
| `SessionsScreen` (UC-T3) | Today's sessions: the one under way first, those still to record highlighted. Pull down to refresh. | `GET /me/sessions`, `GET /me/classes` |
| `AttendanceScreen` (UC-T1, UC-T2) | The roll of one session. Every student is present until tapped (BR-2). Saving asks twice (BR-3). Once confirmed, the session is locked and the screen only shows it (BR-4). | `PUT /sessions/{id}` through the sync queue |

How a roll call is saved:

1. `AttendanceRecorder` gives the session a UUID and writes it to the sync queue. That queued copy is the saved, locked session.
2. The queue sends it when it can (section 6.2).
3. `localSessionsProvider` streams the sessions recorded on this phone, so the list shows "recorded" at once, with or without network.

### 4.2 Assistant DM (`features/lateness`)

| Screen | What it does | API |
| --- | --- | --- |
| `GateScreen` (UC-AD1, UC-AD2) | Search a student by name or matricule, record the arrival time, see today's lateness. | `GET /lateness/students`, `GET /lateness`, `PUT /lateness/{id}` through the sync queue |

- The whole scope's students are kept on the phone, and the search runs on the phone.
- The day's list merges the server's records with those recorded here and not sent yet, each marked sent or waiting.

### 4.3 DM (`features/dm`)

The DM's reads and decisions need the network; only the teacher and gate screens work offline (D35). All calls go through `DmRepository`.

**Today tab (`TodayScreen`, D41)**

- Three cards, each opening its list: alerts to decide, periods not recorded, the day's absences and lateness.
- On a day without lessons, one card says so and still opens the day view.
- A count that could not be read shows a retry, never 0.
- Below: the full-day report.
- API: `GET /alerts`, `GET /attendance/day`.

**Students tab (`StudentSearchView`, D35)** – search by name or matricule in the DM's classes; opens the student page. API: `GET /lateness/students`.

**Messages tab (`SentMessagesView`, UC-D4)** – the messages the DM sent, newest first, with delivery counts; a button writes a new one. API: `GET /messages`.

**More tab (`MoreScreen`)** – the tabs that did not fit, then decided alerts, the scope dashboard, the settings, and signing out.

**Screens under the tabs**

| Screen | What it does | API |
| --- | --- | --- |
| `OpenAlertsScreen` (UC-D2) | The open alerts, the longest waiting first. | `GET /alerts` |
| `AlertScreen` (UC-D2, UC-D3) | One alert, what its rule found, and the four decisions. Nothing happens until the DM chooses. | `POST /alerts/{id}/decision` |
| `ClosedAlertsScreen` | The decided alerts with their decision; each opens the student page. | `GET /alerts?status=closed` |
| `StudentHistoryScreen` (UC-D8) | Three tabs: History, Discipline, Messages. One "Agir" button: record an incident, message the parents, summon. On History, tick records to justify several at once, or cancel one. | `GET /students/{id}/history`, `POST /justifications`, `POST /records/{id}/cancel`, `POST /lateness/{id}/cancel`, `POST /summons`, `GET /messages?studentId=` |
| `DisciplineTab` and its sheets (UC-D5, D37) | The student's incidents; record one, close one, cancel one. | `GET` and `POST /students/{id}/disciplinary-records`, `POST /disciplinary-records/{id}/close`, `…/cancel` |
| `DayScreen` (UC-D10, D40) | One day, period by period, with each class's lateness. Justify several records or cancel one. Cancelled rows stay listed, struck through. | `GET /attendance/day?date=`, `POST /justifications`, the two cancel calls |
| `FullDayScreen` (UC-D9, D36) | Pick a school day, generate the full-day absence report, preview the PDF and share it. | `GET /reports/full-day-absences/default-date`, `GET /reports/full-day-absences?date=`, `GET /school/letterhead`, `GET /school/letterhead/logo` |
| `ScopeDashboardScreen` (UC-D8, D34) | The DM's classes over a range, the last 30 days by default. | `GET /dashboard/scope` |
| `SettingsScreen` (UC-D7, D28) | The alert rules, the start of school, the SMS digest times. A value out of bounds blocks saving and says why. | `GET` and `PUT /settings/thresholds` |
| `MessageComposerScreen` (UC-D4, D38) | Write to the parents of one student or one class. A preview, asked 400 ms after the last change, gives who gets it and the SMS cost. Sending asks for confirmation. | `GET /messages/classes`, `POST /messages/preview`, `POST /messages` |

Notes:

- The full-day PDF is drawn on the phone (`report_document.dart`) with the bundled Noto Sans font, under the school's letterhead, and handed to Android's share sheet.
- The message id is made once per composer, so a double tap or a retry sends once.
- Sheets (`dm_sheets.dart`, `discipline_sheets.dart`) hold the small forms: decision note, summons, justification, cancellation, incident.

### 4.4 Parent (`features/parent`)

| Screen | What it does | API |
| --- | --- | --- |
| `ChildrenScreen` (UC-P3) | The parent's children; each opens their history. | `GET /me/children` |
| `ChildHistoryScreen` (UC-P3, UC-P5) | Absences and lateness over the last 90 days: totals first, then day by day. A second tab lists whom to contact. | `GET /children/{id}/history`, `GET /me/children/{id}/contacts` |
| `InboxScreen` (UC-P2) | The messages, newest first, by day. Unread ones stand out. | `GET /me/notifications` |
| `MessageScreen` | One message in full. Opening it sends the receipt (BR-24). A message about attendance leads to the child's history. | `POST /notifications/{id}/opened` |

- Contacts: call and SMS open the phone's own apps. SchoolLink sends nothing itself.
- The inbox loads by pages (`before` = the last entry shown).
- A receipt that could not be sent (no network) is kept and sent first the next time the inbox loads.

---

## 5. The core layer

### 5.1 HTTP and tokens

- `dioProvider` builds one dio client on `apiBaseUrl` (`core/config.dart`): `http://10.0.2.2:8080/api/v1` by default, or `--dart-define=API_BASE_URL=…`.
- `AuthInterceptor` adds `Authorization` and `Accept-Language` to each request. On a 401 it refreshes the tokens once and replays the request. Calls that fail together share one refresh, because a refresh token is single use.
- Every failure reaches the screens as an `ApiFailure` (`status`, `code`, `message`, `field`). `failureText` turns it into the text to show: the server's message, or the app's own text for "no network".

### 5.2 Session

- `SessionStore` keeps both tokens and the user in secure storage, and the access token in memory.
- `AuthController` (`authControllerProvider`) holds who is signed in: loading, a user, or null. The router and the screens follow it.

### 5.3 Providers

| Provider | Gives |
| --- | --- |
| `deviceStoreProvider` | Secure storage (a memory store in tests) |
| `httpAdapterProvider` | Null in the app; the fake server in tests |
| `dioProvider`, `sessionStoreProvider` | The HTTP client and the session |
| `authControllerProvider` | The signed-in user |
| `appLocaleProvider` | The language: the account's when signed in, the device choice otherwise |
| `clockProvider` | The phone's clock (fixed in tests) |
| `appDatabaseProvider` | The drift database (in memory in tests) |
| `syncQueueProvider`, `syncSchedulerProvider`, `pendingSyncProvider`, `onlineProvider` | The offline queue and its state |
| `pushClientProvider`, `pushRegistrationProvider` | Push |

Failed providers are not retried automatically (`noRetry`): each screen offers its own "Retry".

### 5.4 Shared widgets (`core/ui`)

`HeaderBand` (the navy band that opens each home screen), `InitialsAvatar` (no photos, NFR-10), `StatusPill` (always a word, never colour alone), `SectionHeader`, `InfoBanner`, `EmptyState`, `FailureView`, and the `Skeleton` loading placeholders.

---

## 6. Offline: what works without a network

| Screen | Offline behaviour |
| --- | --- |
| Teacher: sessions list | Opens from the phone's copy of the last answer |
| Teacher: roll call | Fully offline: recorded and locked on the phone, sent later |
| Assistant DM: gate | Fully offline: students on the phone, records sent later |
| Parent: children, history, inbox | Shows the last answer kept on the phone |
| DM screens | Need the network |

### 6.1 The local database (`core/db/database.dart`)

Two tables:

- **`SyncItems`**: one row per record made offline. It holds the device UUID, the user who made it, the kind (`session` or `lateness`), the API path, the JSON body, its state, the number of attempts, the time of the next attempt, and the server's last answer.
- **`CachedResponses`**: the last answer of a read endpoint, per user and key (for example `sessions:2026-11-02`). Answers older than 7 days are deleted.

A record belongs to the user who made it: only their token can send it.

### 6.2 The sync queue (`core/sync`)

- **`SyncQueue`** sends the due records one at a time, oldest first, with `PUT <path>`. The server is idempotent on the UUID, so a record sent twice is stored once.
- **States:**
  - *pending*: not on the server yet;
  - *synced*: accepted (201, or 200 for a replay);
  - *conflict*: 409, the server holds a different record for the same thing. Never retried;
  - *rejected*: 400, 403 or 404. Never retried.
- **Retries:** after no network, a timeout, a 408, a 429 or a server error, the record waits 5 seconds, then 10, 20, and so on, up to 15 minutes.
- **`SyncScheduler`** decides when to run: at sign-in and start-up, when the network comes back (waiting times skipped), as soon as a record is queued, and when the next wait is over.
- Synced rows are kept 30 days, then deleted.
- It runs only while a user is signed in with a usable session, not while a temporary password must be replaced.

---

## 7. Push notifications

- `PushClient` is the app's own interface; `FirebasePushClient` adapts Firebase Messaging and local notifications to it.
- The Firebase project is never committed. It comes from `android/app/google-services.json` on the developer's machine, or from `--dart-define` values (`FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_SENDER_ID`, `FIREBASE_PROJECT_ID`). With neither, the app works without push (D23).
- `PushRegistration` sends the phone's token to the server (`PUT /me/device-token`) at sign-in, at start-up, when Firebase replaces the token, and when the network returns after a failed attempt. It removes the token at sign-out.
- A push carries its title and body already in the user's language, and the id of the notification. Tapping it opens the app.

---

## 8. Languages

- Every text is in `lib/l10n/app_fr.arb` and `app_en.arb`; `flutter gen-l10n` generates the Dart classes. A key is always added to both files in the same change (NFR-12).
- Before sign-in: the language chosen on the sign-in screen, kept on the device; before the first choice, English if the phone is in English, French otherwise.
- After sign-in: the language stored on the account.
- Messages from the server (errors, notification texts) arrive already translated.

---

## 9. Tests

- `test/support/fake_server.dart`: a fake backend plugged into dio. A test declares the answers (`server.on('GET /me', …)`) and reads what was sent (`server.sent('PUT /sessions/…')`).
- `test/support/harness.dart`: `pumpApp` starts the whole app with the fake server, a memory device store, an in-memory database and a fixed clock.
- `test/core`: the interceptor, the database migration, push registration, the sync queue.
- `test/features`: one file per screen or flow (sign-in, routing, roll call, gate, each DM screen, parent screens, offline sync, languages).

Tests use made-up names only; no real student data.

Commands (from `mobile/`):

```bash
flutter pub get
dart run build_runner build --delete-conflicting-outputs   # drift code
flutter gen-l10n                                           # texts
flutter analyze
flutter test
flutter run                                                # emulator
flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1   # real phone over USB, after: adb reverse tcp:8080 tcp:8080
flutter build apk --release --split-per-abi                # NFR-4: each APK under 30 MB
```
