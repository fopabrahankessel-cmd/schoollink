# SchoolLink — Functional & Technical Specification

Oct 28, 2026 · @Fops

## 1. Overview

SchoolLink turns one daily event, a student marked absent or late, into a same-day notification to the parent, and turns repeated patterns into risk alerts that the Discipline Master (DM) decides on.

**Problem.** In many secondary schools, absence and lateness are recorded on paper. Parents are rarely told the same day, repeated patterns are noticed late, and no structured record exists of what the school did about them. Early detection and management of truancy both suffer.

**Objectives**

1. Give parents a direct, same-day channel from the school about absences and lateness.
2. Detect truancy risk early from attendance patterns, with a human (the DM) deciding at every step.
3. Record and follow up interventions, so truancy management is documented.
4. Pilot the platform in two schools and evaluate it: technical tests, usability, acceptance, and the effect on communication and case handling.

**Design principle.** The platform never replaces human judgement. Rules only raise alerts or send notifications; the DM decides. This keeps the project ethically defensible and the detection logic simple enough to explain.

**In scope**

- One Android app (Flutter) for parents, teachers, assistant DMs, DMs and counsellors, with role-based screens.
- One web dashboard for administrators, DMs and the researcher.
- Attendance per period, lateness at the gate, notifications (push and SMS), risk detection, DM decisions, counsellor case management.
- French and English throughout, including SMS.
- Anonymised research export.
- Pilot in two schools.

**Out of scope**

- Replies from parents in the app or by SMS: parents are informed only.
- Grades, fees, timetables and other school-management functions.
- iOS app (possible later with the same Flutter code).

## 2. Assumptions and open decisions

Three questions were still open when this spec was written; the defaults below are used throughout and can be changed.

| # | Question | Default taken in this spec |
| --- | --- | --- |
| 1 | What do the absence thresholds count, and over what window? | Absences are counted in **periods**. "15 unjustified absences" = 15 unjustified absent periods in the current **term**. Each rule stores its own unit and window, so a school can change this in settings. |
| 2 | How is a teacher error fixed after the notification is sent? | The teacher cannot edit. The **DM can cancel** a sent record with a reason; the system sends the parent an automatic correction message and keeps both versions in the audit log. |
| 3 | Is DM-to-parent messaging one-way? | **Yes.** The DM sends messages and summons; parents read them. Parents answer in person or by phone, outside the app. |

**Decisions already confirmed**

- Attendance is recorded **per period** by the teacher.
- Lateness is recorded **once per day at the gate** by the assistant DM, after 7:30. Attendance and lateness are separate records and never override each other.
- Before saving, the teacher confirms the absent list **twice**. Once confirmed, the record is locked and the notification is queued.
- Every threshold produces a **risk alert to the DM**, never an automatic action. Thresholds are configurable per school in the DM's settings.
- A **full-day absence** gives the DM a one-tap option to summon the parent.
- Each DM and assistant DM works within **their own scope** of classes.
- The counsellor sees a student **only after DM referral**.
- Parents do not reply. Justifications are handed to the DM in person, and the DM records them.
- Students can have several guardians; a parent can have several children.
- Each parent has a preferred language (FR or EN).
- SMS costs are paid by the project owner during the pilot, so SMS volume is kept low by design (see section 4.6).

## 3. Actors and permissions

Seven roles use SchoolLink; every role except the administrator and researcher is limited to a scope of classes or children.

| Role | Channel | Scope | Main responsibilities |
| --- | --- | --- | --- |
| School administrator | Web | Whole school | School structure, accounts, student import, parent invitations, announcements, school-wide dashboard |
| Teacher | App | Classes they teach | Record attendance per period, double-confirm absentees |
| Assistant DM | App | Classes assigned by the admin | Record lateness at the gate after 7:30 |
| Discipline Master (DM) | App + web | Classes assigned by the admin | Justify absences and lateness, review risk alerts, summon and message parents, record disciplinary issues, refer to the counsellor, cancel erroneous records, configure thresholds |
| Guidance counsellor | App + web | Students referred to them | Review referrals, open cases, study attendance patterns, keep private notes |
| Parent | App or SMS only | Their own children | Receive alerts and messages, consult attendance history |
| Researcher | Web | Pilot schools, anonymised | Research view, anonymised export, technical logs, delivery statistics |

**Permission matrix** (R = read, W = write, — = no access)

| Data | Admin | Teacher | Asst. DM | DM | Counsellor | Parent | Researcher |
| --- | --- | --- | --- | --- | --- | --- | --- |
| School structure, accounts | W | R (own classes) | R (own scope) | R (own scope) | R | — | — |
| Student list, guardians | W | R (own classes) | R (own scope) | R (own scope) | R (referred) | R (own children) | Anonymised |
| Attendance records | R | W before lock (own classes) | R (own scope) | R + justify + cancel (own scope) | R (referred) | R (own children) | Anonymised |
| Lateness records | R | — | W (own scope) | R + justify (own scope) | R (referred) | R (own children) | Anonymised |
| Risk alerts | R (counts) | — | — | W (own scope) | — | — | Counts only |
| Summons, DM messages | R | — | — | W (own scope) | — | R (own children) | Counts only |
| Disciplinary records | R | — | — | W (own scope) | R (referred) | — | — |
| Cases (status) | R (counts) | — | — | R (own referrals) | W | — | Counts only |
| Private case notes | — | — | — | — | W (own cases) | — | — |
| Threshold settings | R | — | — | W (school) | — | — | R |
| Announcements | W | R | R | R | R | R | — |
| Audit and technical logs | R | — | — | — | — | — | R |

**Two rules behind the matrix**

- A user's scope is set by assignments, not by role alone: a teacher sees only classes assigned to them, a DM only the classes in their block.
- Private case notes are readable **only by the counsellor who wrote them**. The DM sees only the case status (open, in follow-up, closed).

## 4. Business rules

These rules are the core of SchoolLink; each one has an identifier (BR-x) so that tests and code can refer to it.

### 4.1 Attendance (per period)

- **BR-1.** An attendance session = one class, one date, one period, one teacher. Only a teacher assigned to that class can open it.
- **BR-2.** Every student starts as **Present**; the teacher taps only the absent ones.
- **BR-3.** On *Save*, the app shows the list of students marked absent and asks for confirmation. A second confirmation ("Confirm and notify parents") follows. Cancelling at either step returns to editing.
- **BR-4.** After the second confirmation the session is **locked**: the teacher can no longer edit it. Notifications are queued (section 4.6).
- **BR-5.** A session with zero absentees is still saved, so the school can prove attendance was taken.
- **BR-6.** A session can be recorded offline; the lock time is the device confirmation time, and notifications are queued when the device syncs.

### 4.2 Lateness (gate)

- **BR-7.** Lateness is recorded by the assistant DM for any student entering after **7:30** (configurable school start time). One lateness record per student per day.
- **BR-8.** Lateness and attendance are independent. A student who arrives late but reaches class before roll call is *present* for that period *and* late. A student who arrives after roll call is *absent* for that period *and* late.
- **BR-9.** Neither record overrides the other, and no automatic reconciliation is done.

### 4.3 Justification

- **BR-10.** Every absence and lateness starts as **Unjustified**.
- **BR-11.** Only the DM in charge of the student's class can mark a record **Justified**, with a reason (illness, family, school activity, other) and an optional note. The parent hands the justification in person.
- **BR-12.** The DM can justify several periods or days at once (e.g., a three-day illness).

### 4.4 Correction of errors

- **BR-13.** Only the DM can **cancel** a locked absence, with a mandatory reason.
- **BR-14.** If the parent was already notified, a correction message is sent automatically ("Correction: Paul was present in period 2 on 28/10").
- **BR-15.** Cancelled records stay in the database and audit log; they are excluded from counts and thresholds.

### 4.5 Risk detection

- **BR-16.** Thresholds are stored as rules: type, count, unit, window, active or not. Defaults for the pilot:

| Rule | Default | Unit | Window | Produces |
| --- | --- | --- | --- | --- |
| Full-day absence | 1 day | All periods of the day absent | Same day | Alert + one-tap summons option |
| Consecutive lateness | 5 | School days in a row with lateness | Rolling | Risk alert |
| Unjustified absences | 15 | Unjustified absent periods | Current term | Risk alert |

- **BR-17.** Rules run after each locked session or lateness record and again in a nightly job. A rule raises at most **one open alert per student per rule**; a new alert of the same kind is raised only after the previous one is closed.
- **BR-18.** An alert goes only to the DM in charge of the student's class. The DM chooses one decision: **Dismiss** (with reason), **Summon parent**, **Record disciplinary issue**, or **Refer to counsellor**. Every decision is recorded with who, when and why.
- **BR-19.** Justifying an absence later does not delete an alert already raised; the DM closes it.

### 4.6 Notifications

- **BR-20.** Each event (absence for a period, lateness, summons, DM message, correction, announcement) creates a notification per guardian, written in that guardian's language.
- **BR-21.** Push notifications (FCM) are sent **immediately**.
- **BR-22.** SMS is sent as **one daily digest** per student, at a configurable time (default 13:00, second run at 17:00), only to guardians who have no app or did not open the related push. Example: *"SchoolLink: Paul absent P1–P3 and late (07:48) on 28/10. Collège X."*
- **BR-23.** **Summons** are sent by push and SMS immediately, not in the digest.
- **BR-24.** Every notification stores its status: queued, sent, delivered (SMS report), opened (app), failed. Failed sends retry with backoff, up to 3 times.
- **BR-25.** A per-school SMS counter and monthly limit appear on the admin dashboard; when the limit is reached, only summons go by SMS.

## 5. Use cases

The use cases are numbered by actor; the MVP column says which ones the first pilot release must include.

| ID | Actor | Use case | MVP |
| --- | --- | --- | --- |
| UC-A1 | Administrator | Create the academic year, terms, periods (timetable slots) and classes | Yes |
| UC-A2 | Administrator | Create staff accounts (teacher, assistant DM, DM, counsellor) and assign classes | Yes |
| UC-A3 | Administrator | Import students and guardian phone numbers from CSV/Excel, with a preview and error list before commit | Yes |
| UC-A4 | Administrator | Generate parent invitation codes, link guardians to children, print or SMS the codes | Yes |
| UC-A5 | Administrator | Send a school-wide or class announcement | Wave 2 |
| UC-A6 | Administrator | Consult the school dashboard: attendance trends, alerts, delivery rate, SMS usage | Yes |
| UC-T1 | Teacher | Open today's session for a class and period, mark absentees, confirm twice, save | Yes |
| UC-T2 | Teacher | Record attendance offline and sync later | Yes |
| UC-T3 | Teacher | See which of today's sessions are not yet recorded | Yes |
| UC-AD1 | Assistant DM | Search a student at the gate and record lateness with arrival time | Yes |
| UC-AD2 | Assistant DM | See today's lateness list for their scope | Yes |
| UC-D1 | DM | Justify absences or lateness for one or several records | Yes |
| UC-D2 | DM | Review risk alerts and decide: dismiss, summon, disciplinary issue, refer | Yes |
| UC-D3 | DM | Summon a parent (one tap after a full-day absence, or from an alert) | Yes |
| UC-D4 | DM | Send a one-way message to a student's guardians | Wave 2 |
| UC-D5 | DM | Record a disciplinary issue for a student | Wave 2 |
| UC-D6 | DM | Cancel an erroneous locked absence with a reason | Yes |
| UC-D7 | DM | Configure thresholds, school start time and SMS digest times | Yes |
| UC-D8 | DM | Consult a student's attendance and lateness history and the scope dashboard | Yes |
| UC-C1 | Counsellor | See referrals from DMs and open a case | Wave 2 |
| UC-C2 | Counsellor | Study a student's attendance pattern (by day of week, period, subject) | Wave 2 |
| UC-C3 | Counsellor | Write private case notes, set follow-up dates, close a case | Wave 2 |
| UC-P1 | Parent | Activate the account with the invitation code and an SMS one-time code | Yes |
| UC-P2 | Parent | Receive absence, lateness, summons and correction alerts | Yes |
| UC-P3 | Parent | Consult each child's attendance history | Yes |
| UC-P4 | Parent | Read DM messages and announcements | Wave 2 |
| UC-R1 | Researcher | Consult the research view: usage, delivery and response times, alert and case counts | Wave 2 |
| UC-R2 | Researcher | Export anonymised datasets (CSV) | Wave 2 |
| UC-R3 | Researcher | Consult technical logs | Wave 2 |

**Detail of the most important use case, UC-T1**

1. The teacher opens the app; today's sessions for their classes appear, with the current period first.
2. They open the session; all students are listed as present.
3. They tap the absent students.
4. They tap *Save*. The app lists the absent students: "These 3 students are marked absent. Correct?"
5. They tap *Yes*. The app asks a final confirmation: "Confirm and notify parents?"
6. They confirm. The session is locked, saved locally, and synced. Push notifications go out; SMS waits for the digest.
7. **Alternative:** at step 4 or 5 the teacher taps *Back* and returns to step 3.
8. **Alternative:** no network. The session is locked and stored on the device; a banner shows "Waiting to sync"; notifications are sent after sync.

Target: a class of 60 students recorded in **under 60 seconds** when 5 or fewer are absent.

## 6. Key flows

Two flows carry the whole system: events going out to parents, and alerts going to the DM for a decision.

### 6.1 From attendance and lateness to the parent

&#91;embedded content: notification flow · 2 sources, 1 outbox, 2 channels\]

Attendance and lateness are separate records, but both feed the same outbox. Push goes out at once; SMS is a single daily digest per student for guardians who have no app or did not open the push.

### 6.2 From a risk alert to a case

&#91;embedded content: alert-to-case flow · 1 decision, 4 outcomes\]

The counsellor never sees a student until the DM refers them. Case notes stay private to the counsellor; the DM sees only the case status.

## 7. Data model

The model has about 25 tables in PostgreSQL; every school-owned row carries a `school_id`, so two pilot schools share one database safely.

| Area | Entity | Key fields | Notes |
| --- | --- | --- | --- |
| School | `School` | id, name, language, start\_time, timezone | One row per pilot school |
| School | `AcademicYear`, `Term` | school\_id, label, start\_date, end\_date | Thresholds use the current term |
| School | `Period` | school\_id, number, start\_time, end\_time | P1 to P8 of the timetable |
| School | `SchoolClass` | school\_id, year\_id, name, level | e.g., 4e A, Tle C |
| People | `User` | id, school\_id, phone, email, password\_hash, language, status | Staff and parents |
| People | `RoleAssignment` | user\_id, role, scope\_type, scope\_id | Role + scope (class or block) |
| People | `TeachingAssignment` | teacher\_id, class\_id, subject | Which teacher teaches which class |
| People | `Student` | id, school\_id, matricule, names, class\_id, sex, birth\_year | Current class enrolment |
| People | `Guardian` | id, user\_id (nullable), phone, language, sms\_only | A guardian may have no app account |
| People | `StudentGuardian` | student\_id, guardian\_id, relation, is\_primary | Many-to-many |
| People | `InvitationCode` | guardian\_id, code\_hash, expires\_at, used\_at | Single-use, 30-day expiry |
| Attendance | `AttendanceSession` | id (UUID from device), class\_id, date, period\_id, teacher\_id, confirmed\_at, synced\_at | Unique on class + date + period |
| Attendance | `AttendanceRecord` | session\_id, student\_id, status (PRESENT, ABSENT), justification\_id, cancelled\_at, cancel\_reason | Only absentees need rows if you prefer; storing all gives clean statistics |
| Attendance | `LatenessRecord` | id, student\_id, date, arrival\_time, recorded\_by, justification\_id | Unique on student + date |
| Attendance | `Justification` | id, reason, note, recorded\_by, recorded\_at | Shared by absences and lateness |
| Detection | `ThresholdRule` | school\_id, type, count, unit, window, active | Edited by the DM |
| Detection | `RiskAlert` | id, student\_id, rule\_id, raised\_at, status, decision, decided\_by, decided\_at, decision\_note | One open alert per student per rule |
| Discipline | `Summons` | id, student\_id, alert\_id (nullable), reason, meeting\_date, sent\_by | Sent by push + SMS |
| Discipline | `DisciplinaryRecord` | id, student\_id, type, description, recorded\_by, date | Wave 2 |
| Discipline | `DmMessage` | id, student\_id, author\_id, body | One-way, wave 2 |
| Counselling | `CounsellingCase` | id, student\_id, referred\_by, alert\_id, counsellor\_id, status, opened\_at, closed\_at | Status visible to DM |
| Counselling | `CaseNote` | case\_id, author\_id, body\_encrypted, created\_at, follow\_up\_date | Readable by author only |
| Messaging | `Announcement` | id, school\_id, audience (school, class), title, body, author\_id | Wave 2 |
| Notification | `NotificationOutbox` | id, guardian\_id, event\_type, event\_id, channel (PUSH, SMS), language, payload, status, attempts, sent\_at, delivered\_at, opened\_at | Drives delivery and the research metrics |
| Notification | `DeviceToken` | user\_id, fcm\_token, platform, last\_seen | One per installed app |
| Audit | `AuditLog` | actor\_id, action, entity, entity\_id, before, after, at | Append-only |

**Relations in one line each**

- A `SchoolClass` has many `Student`s; a `Student` has many `Guardian`s through `StudentGuardian`.
- An `AttendanceSession` has many `AttendanceRecord`s; a `Student` has at most one `LatenessRecord` per day.
- A `RiskAlert` may lead to one `Summons`, one `DisciplinaryRecord`, or one `CounsellingCase`.
- Every event that concerns a parent creates one `NotificationOutbox` row per guardian per channel.

## 8. Architecture and stack

SchoolLink is one Spring Boot modular monolith behind one REST API, used by a Flutter Android app and a React web dashboard; two pilot schools do not justify microservices.

&#91;embedded content: architecture · 2 clients, 1 backend, 3 services\]

Modules talk to each other through Java interfaces and events, not HTTP. An attendance confirmation publishes an event; the notification and detection modules react to it, so a slow SMS gateway never blocks a teacher.

| Layer | Choice | Role |
| --- | --- | --- |
| Mobile IDE | Android Studio | Flutter development and emulator |
| Mobile app | Flutter (Dart), one app, role-based navigation | Parent, teacher, assistant DM, DM, counsellor |
| State | Riverpod | Screens and sync state |
| Offline storage | drift (SQLite) + sync queue | Sessions and lateness saved without network, synced by device UUID |
| HTTP | dio | JWT and refresh-token interceptors |
| Push (app) | firebase\_messaging + flutter\_local\_notifications | Receive and show alerts |
| Secure storage | flutter\_secure\_storage | Tokens |
| App i18n | flutter\_localizations + intl (ARB files) | French and English |
| Crash reports | Firebase Crashlytics | Stability metrics for the evaluation |
| Backend | Java 21 + Spring Boot 3 | REST API, business rules |
| Security | Spring Security, JWT, BCrypt | Roles and scopes |
| Database | PostgreSQL 16 + Flyway | Data and versioned schema |
| Jobs | Spring `@Scheduled` (or db-scheduler) | Nightly rules, SMS digest, retries |
| Push (server) | Firebase Admin SDK | Send FCM notifications |
| SMS | Gateway behind an `SmsProvider` interface | Pick by price, delivery reports, MTN and Orange coverage |
| Import | Apache POI + OpenCSV | Student and guardian import |
| Encryption | Jasypt or pgcrypto | Case notes at rest |
| Web dashboard | React + Vite + TypeScript, MUI, Recharts, i18next | Admin, DM and researcher screens |
| Tests | JUnit 5, Testcontainers, Flutter widget tests | Rules, permissions, offline |
| Deployment | Docker Compose, Nginx, Let's Encrypt on a VPS | Hosting for the pilot |

## 9. API outline

One REST API under `/api/v1`, secured by JWT; the server derives the school and scope from the token, never from the request body.

| Module | Method and path | Used by | Purpose |
| --- | --- | --- | --- |
| Auth | `POST /auth/login` | Staff | Phone or email + password, returns access + refresh tokens |
| Auth | `POST /auth/parent/activate` | Parent | Invitation code + phone, sends an SMS one-time code |
| Auth | `POST /auth/parent/verify` | Parent | One-time code, returns tokens |
| Auth | `POST /auth/refresh` | All | New access token |
| School | `GET/POST /classes`, `/periods`, `/terms` | Admin | School structure |
| People | `POST /students/import` (preview), `POST /students/import/{id}/commit` | Admin | CSV/Excel import in two steps |
| People | `GET /guardians?classId=` | Admin | A class's guardians, their children and account or invitation status |
| People | `POST /guardians/{id}/invitations` | Admin | Generate an invitation code |
| People | `POST /classes/{id}/invitations` | Admin | Invitation codes for every guardian of a class without an account (D7) |
| Attendance | `GET /me/sessions?date=` | Teacher | Today's sessions to record |
| Attendance | `PUT /sessions/{uuid}` | Teacher | Create or sync a confirmed session (idempotent by device UUID) |
| Attendance | `POST /records/{id}/cancel` | DM | Cancel an erroneous absence with a reason |
| Lateness | `GET /lateness/students?q=` | Assistant DM | Students of their scope for the gate search; without `q`, the whole scope for offline search |
| Lateness | `PUT /lateness/{uuid}` | Assistant DM | Record lateness (idempotent) |
| Lateness | `GET /lateness?date=` | Admin, Assistant DM, DM | The day's lateness in scope (UC-AD2) |
| Discipline | `POST /justifications` | DM | Justify one or several records |
| Detection | `GET /alerts?status=open` | DM | Alerts in scope |
| Detection | `POST /alerts/{id}/decision` | DM | Dismiss, summon, disciplinary, refer |
| Detection | `GET/PUT /settings/thresholds` | DM | Configure rules |
| Discipline | `POST /summons` | DM | Summon a parent |
| Counselling | `GET /cases`, `POST /cases`, `POST /cases/{id}/notes` | Counsellor | Case management |
| Parent | `GET /me/children`, `GET /children/{id}/history` | Parent | Children and history |
| Parent | `GET /me/notifications`, `POST /notifications/{id}/opened` | Parent | Inbox + open receipt for the research metrics |
| Devices | `PUT /me/device-token` | All app users | Register FCM token |
| Reporting | `GET /dashboard/school`, `GET /dashboard/scope` | Admin, DM | Counts and trends |
| Research | `GET /research/summary`, `GET /research/export?dataset=` | Researcher | Anonymised statistics and CSV |
| Webhooks | `POST /webhooks/sms/delivery` | SMS gateway | Delivery reports |

Errors follow one JSON shape (`code`, `message`, `field`), with messages in the user's language.

## 10. Non-functional requirements

The four that decide whether the pilot succeeds are speed of entry, offline operation, reliable delivery and protection of minors' data.

| ID | Area | Requirement | How it is checked |
| --- | --- | --- | --- |
| NFR-1 | Usability | Recording a class of 60 with 5 or fewer absentees takes under 60 s | Timed tests with teachers during the pilot |
| NFR-2 | Usability | A parent can read an alert and the history without training | Usability test with 5+ parents per school |
| NFR-3 | Offline | Teacher and assistant DM functions work with no network; data syncs automatically, with no duplicates | Airplane-mode tests; idempotent device UUIDs |
| NFR-4 | Devices | Runs on Android 8.0+ and low-end phones (2 GB RAM); APK under 30 MB | Test on a cheap reference phone |
| NFR-5 | Delivery | 95% of push notifications sent within 1 min of sync; SMS digest sent within 5 min of its scheduled time | Outbox timestamps |
| NFR-6 | Reliability | No notification lost after a server restart or network failure | Outbox pattern + retry tests |
| NFR-7 | Security | HTTPS only; passwords hashed with BCrypt; JWT access token 15 min, refresh 30 days; rate limit on login and activation | Security test checklist |
| NFR-8 | Access | Every query filtered by school and scope on the server | Integration tests per role |
| NFR-9 | Privacy | Case notes encrypted at rest and readable only by their author; phone numbers never in URLs or logs | Code review + tests |
| NFR-10 | Privacy | Minimal data: no photos, no full birth dates (year only), no health details beyond "illness" | Data review |
| NFR-11 | Audit | Every create, edit, cancel, justification, decision and export is logged with actor and time | Audit log review |
| NFR-12 | Language | Every screen, message and SMS template in French and English; per-user choice | Review of both languages |
| NFR-13 | Availability | 99% during school hours (Mon–Fri, 07:00–18:00); daily database backup, restore tested once | Uptime monitor + restore test |
| NFR-14 | Cost | SMS per school and month counted and capped (BR-25) | Admin dashboard |

**Legal and ethical requirements**

- Written authorisation from each pilot school's head teacher.
- An information notice and consent form for parents, in French and English, explaining what is collected, why, who sees it, and how long it is kept.
- Approval from your academic supervisors and, if IAI has one, its ethics process before collecting research data.
- Check obligations under Cameroon's personal data protection law (Law No. 2024/017 of December 2024) with your supervisors.
- Data retention: attendance data kept for the academic year plus one year; research exports contain no names, phones or matricules.

## 11. Research and evaluation plan

The evaluation compares each pilot school before and after deployment, on the four dimensions named in the objectives.

**Baseline (before deployment, 2 to 4 weeks)**

- How absences and lateness are recorded today, and by whom.
- Time from an absence to the parent learning of it (short parent survey, or sampled cases).
- Number of parent summons and documented follow-ups per month (from registers, if they exist).
- Staff and parent expectations (short questionnaire).

**Measures during the pilot**

| Dimension | Indicator | Source |
| --- | --- | --- |
| Technical | Push and SMS delivery rate; median time from confirmation to delivery; sync failures; crash-free sessions | `NotificationOutbox`, logs, Firebase Crashlytics |
| Adoption | Share of expected sessions actually recorded per day; active parents per week; share of parents activated | Database counts |
| Usability | System Usability Scale (SUS) for teachers, DMs and parents; time to record a class | Questionnaire, timed tests |
| Acceptance | Perceived usefulness and ease of use (TAM-style questionnaire); semi-structured interviews with DMs and counsellors | Questionnaire, interviews |
| Communication | Time from absence to parent informed, compared with baseline; share of parents informed the same day | Outbox vs baseline survey |
| Case handling | Alerts raised; share decided within 3 school days; summons, referrals and cases opened; share of cases with a documented follow-up | `RiskAlert`, `CounsellingCase` |

**Anonymised export rules**

- Students and staff are replaced by random IDs that differ from database IDs; the mapping is never exported.
- No names, phones, matricules or exact dates of birth; dates shifted to week numbers where possible.
- Any group with fewer than 5 students is suppressed or merged in aggregated tables.
- Every export is logged (who, when, which dataset).

**Technical tests**

- Unit tests on business rules BR-1 to BR-25.
- Integration tests with Testcontainers (PostgreSQL) on permissions per role and scope.
- Offline and sync tests on a low-end Android phone.
- Load test: 2 schools × 40 classes × 8 periods synced within the same 10 minutes.

## 12. MVP scope and delivery plan

The MVP is the loop from attendance to parent notification to DM decision; counselling, messaging and research views come in wave 2, once the loop works in a real school.

**MVP (must work for the pilot)**: UC-A1 to A4, A6; UC-T1 to T3; UC-AD1, AD2; UC-D1 to D3, D6 to D8; UC-P1 to P3.

**Wave 2**: UC-A5; UC-D4, D5; UC-C1 to C3; UC-P4; UC-R1 to R3.

| Phase | Content | Exit criterion | Estimate |
| --- | --- | --- | --- |
| 0. Preparation | School authorisations, consent forms, baseline data, SMS gateway account, VPS | Both schools agree in writing; baseline collected | 2–3 weeks, in parallel with phase 1 |
| 1. Foundations | Spring Boot project, PostgreSQL schema, auth, roles and scopes, school structure, CSV import, admin web screens | Admin can set up a school and import students | 3 weeks |
| 2. Attendance loop | Flutter app shell, teacher attendance with double confirmation, offline sync, lateness at the gate, outbox, FCM push, parent activation and inbox | A teacher's confirmation reaches a parent's phone | 4 weeks |
| 3. DM and detection | Justification, cancel with correction, threshold rules and alerts, decisions, summons, SMS digest, dashboards | A full test day produces correct alerts and one digest per student | 3 weeks |
| 4. Pilot wave 1 | Training staff, issuing parent codes, live use in 2 schools, bug fixes | 80% of expected sessions recorded daily for 2 weeks | 4–6 weeks |
| 5. Wave 2 | Counsellor cases, DM messages, disciplinary records, announcements, research view and export | Counsellor opens a real case from a referral | 3 weeks |
| 6. Evaluation | Questionnaires, interviews, data analysis, report | Results chapter written | 3 weeks |

**Main risks and responses**

| Risk | Response |
| --- | --- |
| Teachers stop recording | Default "all present", under 60 s per class, DM sees unrecorded sessions daily |
| Weak network in classrooms | Offline-first app, automatic sync |
| SMS cost too high | Push first, one daily SMS digest, monthly cap |
| Parents without smartphones | SMS-only guardians supported from day one |
| Wrong absence notified | Double confirmation, DM cancel with automatic correction |
| Data protection concerns | Consent forms, scoped access, encrypted notes, anonymised exports |
