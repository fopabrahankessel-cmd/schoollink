# DM workspace, parent messages, announcements and school contacts: design

Date: 2026-11-09. Status: approved by the user on 2026-11-09, with staff phone numbers shown to parents. The user skipped the questions ("skip the questions and write the design"), so every decision below not quoted from them is **my call** and is marked so.

## 1. What the user asked for

> Can the Discipline master of each block record disciplinary issues, manage attendance, manage absences, message parents. Concerning notifications, the administrators also can broadcast message to all parents or one class through SMS or notifications. Also parents should be able to easily get the names of teachers and discipline masters of the child. The DM wants to manage all its features both on Android and web. For the UI/UX, apply good and updated design patterns for mobile and web.

Five pieces of work follow from it:

1. **Disciplinary records** (UC-D5): the DM records an incident for a student of their blocks, at any time, not only as a decision on a risk alert.
2. **DM messages to parents** (UC-D4, UC-P4): the DM writes to the guardians of one student, or of one class of their blocks.
3. **Announcements** (UC-A5, UC-P4): the administrator writes to the guardians of the whole school or of one class.
4. **School contacts for parents** (new UC-P5): a parent sees, for each child, the class, the teachers with their subject, and the block's DMs and assistant DMs.
5. **The DM's whole job on Android and on the web**, including a new **attendance-of-the-day view** ("manage attendance, manage absences"), under a navigation redesign on both.

## 2. Why now

The SPEC put UC-A5, UC-D4, UC-D5 and UC-P4 in Wave 2, after the pilot. The user wants them in the pilot (one school, two classes, about 120 students, 4 months, started when the app is "well functional"). The DM is the person who uses SchoolLink most; if they have to switch device to finish a task, they stop using it. Parents, for their part, often do not know who teaches their child or whom to contact.

## 3. Goals, non-goals, anti-goals

**Goals**
- A DM can do every DM task on both platforms (parity table, §8).
- A DM can, from one student page, see the history, record an incident, message the parents and summon them.
- A DM sees today's attendance of their classes period by period, sees which periods were not recorded, and justifies or cancels from there.
- An administrator can reach every parent of the school or of one class, and knows **before sending** how many SMS it will cost.
- A parent finds the names of their child's teachers and DMs in two taps.

**Non-goals (pilot)**
- Replies from parents. Messages stay one-way (UC-D4 says so); parents who want to answer call the school.
- A DM taking or editing a roll call. Teachers record attendance (UC-T1); a locked session is never edited (BR-4). A DM who sees a period not recorded can remind the teacher, not record it. *My call: recording on behalf of an absent teacher is a later decision.*
- Showing disciplinary records to parents. The permission matrix (SPEC §3) gives parents no access to them. A DM who wants the family to know sends a message (§4.2), which they write themselves.
- Staff emails for parents. Parents see names, subjects, roles and phone numbers (§4.5, the user's decision on 2026-11-09).
- Attachments, images, scheduled sending, editing a sent message.
- The counsellor's screens (UC-C1 to C3) stay in Wave 2.

**Anti-goals: it would be a failure even if it worked, if…**
- an announcement to the whole school silently spends the month's SMS budget (BR-25);
- a DM can message, or see, students outside their blocks (NFR-8);
- detection code creates a disciplinary record or sends a message by itself (BR-16 to BR-19: rules only raise alerts);
- the mobile and web versions of a feature behave differently (different rules, different counts);
- the redesign makes the teacher roll call or the gate screen slower (they are the daily, outdoor, one-handed tasks).

## 4. Behaviour

### 4.1 Disciplinary records (UC-D5)

The table `disciplinary_record` exists (V22) and is written today only by the alert decision "disciplinary issue". It is extended (new migration) with:

| Field | Values | Notes |
| --- | --- | --- |
| `category` | `BEHAVIOUR`, `INSOLENCE`, `FIGHT`, `ABSENTEEISM`, `CHEATING`, `DRESS`, `PHONE`, `DAMAGE`, `OTHER` | *My call*, a short list the DM can scan; labels in FR/EN |
| `occurred_on` | a date, not in the future, at most 30 days back | the day of the incident, may differ from the day recorded |
| `measure` | `NONE`, `WARNING`, `DETENTION`, `SUSPENSION`, `OTHER` | what the DM decided, optional |
| `description` | 1 to 500 characters | exists |
| `status` | `OPEN`, `CLOSED` | exists; closing takes an optional note |
| `cancelled_at`, `cancelled_by`, `cancel_reason` | | a record made by mistake is cancelled with a reason, never deleted; cancelled records are hidden from lists and counts (same spirit as BR-13 to BR-15) |

Existing rows from alert decisions get `category = OTHER`, `occurred_on = recorded_at`'s school date, `measure = NONE`.

- Created from the student page, or from an alert decision as today (the alert's sheet gains the category and measure fields).
- Only a DM whose blocks contain the student's class at the time of recording; the admin reads (SPEC §3).
- No edit after saving. *My call*: corrections are a cancel plus a new record, so the audit trail stays simple (NFR-11 audits create, close, cancel).
- No automatic notification to parents (non-goal above).

API: `GET /students/{id}/disciplinary-records`, `POST /students/{id}/disciplinary-records`, `POST /disciplinary-records/{id}/close`, `POST /disciplinary-records/{id}/cancel`.

### 4.2 DM messages (UC-D4)

The DM writes a message to:
- the guardians of **one student** of their blocks (from the student page), or
- the guardians of **one class** of their blocks (from the Messages screen). *My call: the user said "message parents"; a class-wide message is the common case of a DM ("bring the PE kit tomorrow").*

### 4.3 Announcements (UC-A5)

The administrator writes to the guardians of **the whole school** or of **one class**, on the web. *My call: the admin works on the web only (SPEC §3); not on mobile.*

### 4.4 Rules shared by messages and announcements

One mechanism, two authors. A `parent_message` table (new migration): id, school, author, kind (`DM_MESSAGE` or `ANNOUNCEMENT`), audience (`STUDENT`, `CLASS`, `SCHOOL`) and its target id, body, `sms` flag, created_at. Rows are append-only; a message is never edited or deleted. *My call.*

**Who receives it.** Every guardian linked to the targeted students, once each even with several children in the audience (a mother of two in the class gets one message). Students of the current academic year only.

**Channels.** *My call*, following D7 and BR-21:
- guardians with the app get a **push** at once;
- guardians without the app get an **SMS at once** (not in the digest; it is not an absence or a lateness), **only if the author ticks "Also by SMS for parents without the app"**. Ticked by default for a DM message to one student, unticked by default for a class or school message, because that is where the cost is.
- a guardian with the app who does not open the push does **not** get an SMS later. *My call: for announcements a late duplicate costs more than it helps; summons keep their own rule (BR-23).*

**Cost shown before sending (BR-25, NFR-14).** The composer shows, live: "N parents: P by the app, S by SMS (about X FCFA)" and the SMS left this month. With the SMS option ticked, sending is refused when S is more than the SMS left, with a message saying so and suggesting to untick SMS. Messages and announcements count against the monthly limit like every outbox SMS. *My call: the FCFA estimate uses a per-SMS price the admin sets in the settings (default 181 FCFA); if this is too much for the pilot, the estimate can show SMS counts only.*

**Length.** Body 1 to 480 characters. The SMS is built from a template, `SchoolLink - {school}: {body}` for announcements and `SchoolLink - SG {school}: {body}` for DM messages (FR/EN wording in the templates), and the composer shows how many SMS units one message takes. A letter outside GSM-7 (ç, ê, ô…) switches to 70-character units; the composer says so, with a one-tap "replace special letters" that turns ç into c, ê into e, and so on, in the SMS only. *My call.*

**Delivery report.** The sender's list of sent messages shows, for each one: sent, delivered (SMS report), opened (push), failed, from the outbox statuses (BR-24). No recipient phone numbers appear (NFR-9).

**Parents (UC-P4).** Messages and announcements appear in the parent's inbox, which already knows the types `DM_MESSAGE` and `ANNOUNCEMENT`, with the author's role ("Surveillant général", "Administration"), the child concerned when there is one, and the full text. Opening it marks it opened (as today).

**Transactions and audit.** The message row, the outbox rows (one per guardian and channel) and the audit entry are written in one transaction (BR-20, NFR-6, NFR-11). The dispatcher sends them, with retries.

API: `POST /messages` (DM; `studentId` or `classId`), `POST /announcements` (admin; `classId` or none for the school), `POST /messages/preview` and `POST /announcements/preview` returning the counts and cost without sending, `GET /messages` and `GET /announcements` (the author's or the school's sent list with delivery counts).

### 4.5 School contacts for parents (UC-P5, new)

On each child's page in the parent app, a "Who to contact" section: the class name, then each teacher with their subject (from `teaching_assignment`), then the DM(s) and assistant DM(s) of the class's block (from `role_assignment`). Disabled staff accounts are left out.

**Phone numbers.** The user decided that parents see the staff members' phone numbers. Each contact shows the number of their account (`app_user.phone`) with two actions, call and send an SMS, which open the phone's own dialler or messaging app (SchoolLink sends nothing and pays nothing). A staff member whose account has an email and no phone shows "no number" instead. The numbers travel in the response body only, never in a URL or a log (NFR-9), and only to the guardians of a child that this staff member teaches or supervises. *My call: the parent information notice and the staff briefing say that staff numbers are shown to parents, since staff gave them to the school as a sign-in identifier.*

API: `GET /me/children/{id}/contacts`, parent only, own children only (NFR-8).

### 4.6 The DM's attendance of the day ("manage attendance, manage absences")

A new DM screen, "Présences du jour" / "Today's attendance":
- a date (default today; past school days allowed, not future), and the DM's classes;
- for each class, one row per timetabled period: **recorded** with the count of absentees, **not recorded yet** (the period has started and no session is locked), or **upcoming**;
- tapping a recorded period shows its absentees, each with its state (unjustified, justified, cancelled), and the same actions the DM already has: justify (one or several), cancel with reason;
- the day's lateness of the class is listed under it, with justify and cancel.

This reads existing data (sessions, records, lateness, timetable); it adds no new rule. API: `GET /attendance/day?date=` (DM, own blocks), returning classes, periods and statuses. *My call: the "not recorded" state is information only; no automatic reminder to the teacher in this round.*

## 5. Navigation and screens

### 5.1 Mobile (Flutter, Material 3)

The DM's tabs today: one "Alertes" tab holding alerts, search and lateness. New, *my call*, bottom navigation bar with four destinations (Material 3 recommends three to five):

```
+--------------------------------------+
| Aujourd'hui                     (o)  |  <- large title, school + block
|                                      |
|  [ 3 alertes à traiter        > ]    |  <- summary cards, tap to go
|  [ 2 périodes non saisies     > ]    |
|  [ 14 absents · 6 retards     > ]    |
|                                      |
|  Rapport des absences de la journée  |
+--------------------------------------+
| Aujourd'hui | Élèves | Messages | Plus |
+--------------------------------------+
```

- **Aujourd'hui (Today)**: summary cards (alerts to decide, periods not recorded, today's absences and lateness), each opening its list; the full-day report entry stays here.
- **Élèves (Students)**: search at the top (as D35), then the **student page**: header with name, class, a few counts; tabs History, Discipline, Messages; one primary action button with a menu: record an incident, message the parents, summon.
- **Messages**: sent messages with delivery counts; a "New message" button (student or class of the blocks).
- **Plus (More)**: alerts history, scope dashboard, settings (thresholds, start time, digest times), sign out.

A DM who also teaches keeps the teacher's tab first (D21-style multi-role), with the DM tabs after it and "Plus" holding the overflow when there are more than five.

Patterns: action sheets for one-step actions (already used for justify, cancel, summon); a full-screen composer for messages (long text, live counts); pull to refresh; skeletons while loading; empty states that say what to do; 48 dp touch targets; FR/EN strings only from ARB files. The teacher and gate screens are not changed.

### 5.2 Web (React, MUI)

The DM sidebar becomes, *my call*:

```
Tableau de bord
Présences du jour      <- new
Alertes
Élèves                 <- list + student page with tabs
Messages               <- new
Rapport de la journée  <- new on web (preview, download PDF, print)
Paramètres
```

The admin sidebar gains **Annonces** (Announcements): the list of sent announcements with delivery counts, and "New announcement" opening a two-column composer: the text on the left, a live preview of the push and the SMS on the right, with the count and cost line above the send button and a confirmation dialog repeating them.

Web patterns: the student page uses tabs and a right-hand action area; tables keep sticky headers and empty states; dialogs for short actions, a page for the composer; keyboard reachable; URL per page and per tab (deep links); FR/EN through i18next.

### 5.3 Parent app

The child's page gains the "Who to contact" section (§4.5) and the inbox shows messages and announcements (§4.4). The parent tabs stay "Enfants" and "Messages".

### 5.4 Visual direction

Keep the existing identities: the mobile app's brand blue theme and status colours, the web's teal tokens and Plus Jakarta Sans. New screens reuse `HeaderBand`, `SectionHeader`, `EmptyState`, `InfoBanner` on mobile and `PageHeader`, the status pills and `ConfirmDialog` on the web, so the redesign changes structure and navigation, not the brand. *My call; the memory note "creative professional UI" applies to the new screens' layout and copy.*

## 6. Constraints

- Modular monolith: disciplinary records stay in `discipline`; `parent_message` in `notification` (it owns the outbox and templates); contacts in `people`; the day view in `reporting` or `attendance`. Modules talk through their public interfaces and events.
- NFR-8: scope from the JWT. DM: classes of their blocks. Admin: own school. Parent: own children. One integration test per role per endpoint, allowed and forbidden.
- NFR-9: no phone numbers in URLs, logs or the delivery report.
- NFR-11: create, close and cancel of a record; every message and announcement sent; all audited.
- NFR-12: every new string in FR and EN (ARB, i18next, `messages_*.properties`), including the push and SMS templates.
- BR-25: messages and announcements count against the monthly SMS limit; only summons pass the limit.
- No real student data in tests or seeds.

## 7. Changes to the SPEC and PLAN

- UC-A5, UC-D4, UC-D5, UC-P4 move from Wave 2 to the pilot scope; add UC-P5 and a UC for the DM day view (UC-D10).
- New decisions in PLAN: D37 disciplinary records outside alerts; D38 parent messages and announcements (channels, SMS opt-in, cost check); D39 school contacts for parents; D40 DM day view; D41 navigation (mobile four tabs, web sidebar).
- SPEC §9 API outline gets the endpoints of §4.

## 8. DM parity, after this work

| DM feature | Android today | Web today | After |
| --- | --- | --- | --- |
| Alerts and decisions | yes | yes | both |
| Justify, cancel | yes (from history) | yes | both, also from the day view |
| Summon | yes | yes | both |
| Student search and history | yes | yes | both, as the student page |
| Scope dashboard | no | yes | both |
| Thresholds and digest settings | no | yes | both |
| Full-day absence report | yes (share PDF) | no | both (web: download and print) |
| Today's attendance | no | no | both |
| Disciplinary records | via alert only | via alert only | both |
| Messages to parents | no | no | both |

## 9. Suggested order

1. Contacts for parents (small, visible to parents at once).
2. Disciplinary records, with the student page on both platforms.
3. Messages and announcements (shared backend, then DM screens, then admin screen, then parent inbox).
4. Today's attendance view.
5. Navigation redesign and the remaining parity rows (settings and dashboard on mobile, report on web).

Each step is shippable alone and keeps all tests green.

## 10. Left to the builder

Exact copy of labels and templates (within the FR/EN and GSM-7 rules), the icons, the SQL of the day view, the shape of the preview response, how the composer counts SMS units, and whether the student page tabs load lazily.

## 11. Calls I made that you should check

1. A DM can message a whole class of their blocks, not only one student's parents.
2. SMS for messages and announcements is optional per message: on by default for one student, off by default for a class or the school; no later SMS when a push is not opened.
3. Sending is refused when the SMS would go past this month's limit.
4. Parents see staff names, subjects and phone numbers, with call and SMS buttons (decided by the user on 2026-11-09).
5. Disciplinary records are never shown or sent to parents automatically, and are never edited, only closed or cancelled.
6. The DM sees missing roll calls but cannot record one for an absent teacher.
7. The admin sends announcements from the web only.
8. Mobile DM navigation: Today, Students, Messages, More.
