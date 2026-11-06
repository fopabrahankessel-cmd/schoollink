# SchoolLink architecture

This document explains how SchoolLink is built and why. The rules themselves (BR-x, UC-x, NFR-x) are in [SPEC.md](SPEC.md), and the decisions (D-x) in [PLAN.md](PLAN.md). This is the map that connects them to the code.

There is no single "design pattern" behind SchoolLink. The overall architecture is a **modular monolith**. Inside it, a small set of well-known patterns each solve one concrete problem from the SPEC. The three that carry the most weight are the **modular monolith**, the **transactional outbox**, and **offline-first sync with idempotent UUIDs**. Together they make SchoolLink reliable on unreliable networks.

## 1. The system at a glance

```
 Android app (Flutter)                       Web dashboard (React)
 teachers, assistant DMs, DMs,               admin, DM, researcher
 counsellors, parents
   │  local SQLite + sync queue                │
   └──────────── HTTPS, REST /api/v1, JWT ─────┘
                         │
 ┌──────────── backend: one Spring Boot application ────────────┐
 │  auth  school  people  attendance  lateness  justification    │
 │  detection  discipline  notification  reporting  audit shared │
 └───────────────┬───────────────────────────────┬───────────────┘
                 │                               │
          PostgreSQL 16                 Firebase (push), Twilio (SMS)
```

The three parts:
- **One backend** (Java 21, Spring Boot 4) with one REST API under `/api/v1`.
- **One Android app** for every role. The screens change with the user's role.
- **One web dashboard** for administration, the DM's desk work and research.

They share one PostgreSQL database. Two schools live side by side in it, with every row tagged with its `school_id`.

## 2. Overall architecture: a modular monolith

The backend is **one deployable application**, divided into 12 modules, one Java package each under `com.schoollink`:

| Module | Responsibility |
| --- | --- |
| `auth` | Sign-in, tokens (JWT), passwords, parent activation and SMS codes |
| `school` | Schools, calendar, periods, classes, timetable, letterhead |
| `people` | Staff, students, guardians, import, school bootstrap |
| `attendance` | Roll-call sessions and absences (BR-1–BR-7) |
| `lateness` | Lateness recorded at the gate (BR-8, BR-9) |
| `justification` | Justifying and cancelling records (BR-10–BR-15) |
| `detection` | Threshold rules that raise risk alerts (BR-16–BR-19) |
| `discipline` | The DM's decisions: summons, disciplinary records, referrals |
| `notification` | Outbox, push, SMS, digest, parent inbox (BR-20–BR-25) |
| `reporting` | Dashboards, full-day absence report, research export |
| `audit` | The append-only audit log (NFR-11) |
| `shared` | Small common types used by every module |

**The rules that make it modular, not just one big application:**
- **Each module hides its internals.** Its `internal` sub-package is private to it. Other modules reach it only through its public Java interfaces or through the events it publishes, never over HTTP.
- **A test enforces the boundaries.** `ModularityTests` uses Spring Modulith to verify them, so a module that reaches into another one's internals fails the build.

**Why a monolith and not microservices:** the pilot has two schools. Microservices would add network calls, separate deployments and distributed failures without any benefit at this size. The project rules forbid them, and also forbid message brokers. Clear module boundaries keep the option to split one module out later if SchoolLink ever grows.

## 3. Backend patterns

### 3.1 Domain events (observer)

When something happens in one module, it publishes a Spring application event. Other modules listen and react, without the publisher knowing who listens.

**Example:** `attendance` locks a session and publishes an event. Then:
- `notification` writes the parents' notifications;
- `detection` evaluates the risk rules;
- `audit` records the action.

`attendance` depends on none of them. This is how modules stay independent while still working together.

### 3.2 Transactional outbox

**The problem:** a parent must never miss a notification, even if the server crashes or Firebase is down right after a teacher confirms a roll call (NFR-6). Sending inline, inside the request, would lose the message whenever the send fails.

**The pattern:**
1. The absence and its notifications are written in the **same database transaction**: the event listeners that write to the `notification_outbox` table run inside the teacher's transaction. Either both are saved or neither is.
2. A separate **dispatcher** (`OutboxDispatcher`, a scheduled job) reads the queued rows and sends them through Firebase or Twilio.
3. A failed send is **retried with a growing delay**. Each row keeps its status: queued, sent, failed, held, withdrawn.

The outbox table also serves as the delivery record that the research evaluation measures (BR-24, NFR-5).

### 3.3 Strategy (pluggable providers)

Sending is hidden behind two interfaces with interchangeable implementations:

| Interface | Production implementation | Development and test implementation |
| --- | --- | --- |
| `PushProvider` | `FirebasePushProvider` (Firebase Admin SDK) | `LoggingPushProvider` |
| `SmsProvider` | Twilio | logging |

The logging implementations write to the log instead of sending. The configuration picks the implementation at start-up. It **fails closed**: outside the `dev` and `test` profiles, the server refuses to start without the real credentials, so production can never silently fall back to logging. Adding a cheaper SMS provider means writing one more `SmsProvider`.

### 3.4 Idempotent commands with a client-generated UUID

**The problem:** a teacher's phone on a weak network may send the same roll call twice, for example when the first answer never arrived.

**The pattern:**
- The phone generates a UUID for each session or lateness record when it is created.
- The server stores it with `PUT /sessions/{uuid}` and `PUT /lateness/{uuid}`.
- Sending the same UUID again changes nothing and creates no duplicate (BR-6).

### 3.5 Repository

Database access sits in repository classes: Spring Data JPA, and `Jdbc…` classes for hand-written SQL. Business logic never builds SQL itself.

### 3.6 Invariants enforced by the database

Some rules are too important to trust to application code alone. PostgreSQL triggers enforce them, so even a bug cannot break them (D20):
- a locked roll-call session can never be edited or deleted (BR-4);
- a cancellation, once set, can never be undone (BR-13, BR-15);
- the audit log is append-only (NFR-11).

### 3.7 Multi-tenancy by `school_id`

Every school-owned row carries a `school_id`. Every query filters on the school and the user's scope, both taken from the signed-in user's token, **never from the request** (NFR-8). Asking for another school's record answers "not found", as if it did not exist (D17). Every endpoint has an integration test per role, covering both the allowed and the forbidden case.

### 3.8 Append-only audit log

Every create, edit, cancellation, justification, decision and export writes a row to `audit_log` (NFR-11). The log can only grow: the database refuses updates and deletes.

## 4. Mobile app patterns (Flutter)

### 4.1 Feature-first structure

```
lib/
  core/        shared by every feature: api, auth, db, sync, push, cache, storage, ui, theme
  features/    one folder per role or area: teacher, lateness, dm, parent, auth, shell, splash
  l10n/        French and English texts
```

Each role's screens and logic live together, so changing the teacher's roll call touches only `features/teacher` (and `core` if something shared changes).

### 4.2 Riverpod providers: dependency injection and state

Screens never create their services. They read Riverpod providers (`core/providers.dart` and each feature's providers) for the API client, the database, the signed-in user and the push client. In tests, any provider can be replaced by a fake, which is how the widget tests run without a server, a database or Firebase.

### 4.3 Offline-first with a sync queue

**The problem:** schools often have no reliable network, yet a roll call must take seconds (BR-6, NFR-3).

**The pattern:**
1. The teacher's and assistant DM's records are saved first in a local SQLite database (drift, `core/db`), so the screen never waits for the network.
2. A sync queue (`core/sync`) sends them in the background. It retries with a backoff when there is no network, a timeout or a server error. It sends everything waiting at start-up, at sign-in and when the network returns.
3. It stops for good on an answer that will never change, such as a conflict or a refusal, and shows it to the user (D22).

Because each record has its own UUID (3.4), sending it twice is harmless. This is what makes the retries safe.

### 4.4 Interceptor (chain of responsibility)

All HTTP calls go through dio with an interceptor chain (`core/api/auth_interceptor.dart`). It adds the access token to each request. When the server answers that the token expired, it refreshes the token once and replays the request. Screens never deal with tokens.

### 4.5 Adapter behind an interface for push

`PushClient` is the app's own interface for push: permission, token, received and tapped messages. `FirebasePushClient` adapts Firebase Messaging to it, and tests use a fake. `PushRegistration` sends the phone's token to the server at sign-in, at start-up, when Firebase replaces the token and when the network returns, and deletes it at sign-out.

**How the app finds the Firebase project (D23):** the project's settings are never committed. The app gets them in one of two ways:
- from `android/app/google-services.json`, which stays on the developer's machine and out of git; the Android build turns it into resources, so **every build from that machine has push**, however it is installed;
- from `--dart-define` values given at build time.

With neither, the app works fully, without push.

## 5. Web dashboard (React)

| Part | Where | Role |
| --- | --- | --- |
| Pages | `web/src/pages/…` | One folder per feature: dashboard, guardians, import, DM desk… |
| API client | `web/src/api/` | One module that calls the REST API, keeps the access token in memory only, and has one file per area |
| Translations | `web/src/i18n/fr.json`, `en.json` | Every text in both languages (NFR-12) |
| Components | MUI | Interface components |
| Charts | Recharts | Dashboard charts |

## 6. One absence, end to end

How the patterns work together when a teacher marks a student absent:

1. **Phone, offline-first:** the teacher confirms the roll call. The session is saved in SQLite with its UUID and queued (4.3).
2. **Sync, idempotent:** the queue sends `PUT /sessions/{uuid}` when the network allows, and again if needed. The server stores it once (3.4).
3. **Server, invariants:** the session is locked. The database will refuse any later edit (3.6). The server checks the class belongs to the teacher's school and scope (3.7).
4. **Events and outbox:** the attendance module publishes an event. In the same transaction, the notification module writes a push row for each guardian with the app, and an SMS row held for the digest for every guardian (3.1, 3.2). The audit module writes its row (3.8).
5. **Dispatcher, strategy:** within seconds, the dispatcher sends the push through `FirebasePushProvider` and retries it if Firebase fails (3.2, 3.3).
6. **Parent's phone:** the push arrives. Opening it marks the message as read, and an opened push removes that guardian's SMS from the next digest (D25, D33).
7. **Digest:** at the school's digest time, each guardian who did not open the push gets one SMS for that child and day (BR-22).
8. **Detection:** after the commit, the rules run. If a threshold is reached, a risk alert is created for the DM, who alone decides what to do (BR-16–BR-19).
