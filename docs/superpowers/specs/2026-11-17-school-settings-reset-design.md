# School settings in one place, sequences, and a reset before the pilot (D44)

Status: **draft, waiting for the user's approval**. Nothing is built yet.

## Why

A school tries SchoolLink with demo or trial data first. Before the real pilot the admin must
empty the school and enter the real one: terms and sequences, classes, teachers with their
subjects, Discipline Masters and assistant Discipline Masters. Today there is no way to empty a
school, the screens that define it are spread over two menu groups, and sequences do not exist.

What the user asked for, in their words: "give the possibility to the admin to reset school data
and insert new ones", through the existing screens, "but those screens should be grouped … all
school settings (where everything is defined or has to be defined) in one place". A term "is a
combination of two sequences".

## Goals

1. One **School settings** place where the admin sees everything that defines the school, what is
   done and what is missing, and opens each screen from there.
2. **Sequences:** each term has two, with their own dates.
3. **Reset:** the admin empties their school once, before the pilot, and starts again.

## Not in scope

- Importing teachers or classes from a file. The existing screens are used (the user's choice).
- Any use of sequences beyond the calendar (no report or count per sequence yet). *My call.*
- Resetting part of a school (only the students, only the staff). *My call: all or nothing.*
- A reset of another school, or by anyone other than an admin of the school.

## Must never happen

- A reset after the real pilot has started. It would destroy the attendance history and the
  research data, which BR-4, BR-15 and NFR-11 exist to protect.
- A reset that touches another school, or that erases the audit log.
- A reset by accident: one click, or a request replayed.

## 1. School settings in one place

A new page, **School settings** (`/school`), replaces the two menu groups "School" and "People".
The admin menu becomes: Home, School dashboard, Announcements, **School settings**.

The page is a list of sections in the order they depend on each other. Each one says in a line
what is defined ("3 terms, 6 sequences", "24 classes in 2 blocks", "no timetable yet") and
whether it is ready, and opens the existing screen:

| Section | Opens | Ready when |
| --- | --- | --- |
| School year, terms and sequences, holidays | Calendar | a current year with terms, each with 2 sequences |
| The day | Periods | at least one period |
| Blocks and classes | Classes | at least one class in the current year |
| Teachers and their subjects | Staff, filtered on teachers | every class has a teacher for a subject |
| Discipline Masters and assistants | Staff, filtered on those roles | every block has a DM |
| Timetable | Timetable | every class has slots |
| Students and guardians | Students, with Import | at least one student |
| Letterhead | Letterhead | optional, never blocks |
| Reset the school | the reset (section 3) | shown only before the pilot starts |

- The existing screens keep their content. Each gets a "School settings" back link, and the menu
  entry stays highlighted while the admin is in any of them.
- The Guardians screen (who has signed in) moves under Students and guardians.
- The admin's home keeps its question "can teachers take attendance this year?" and its ring, but
  its steps become these same sections and link to this page. *My call: one list of steps, shown
  in two places, instead of two lists that drift apart.*
- The readiness comes from one new endpoint, `GET /school/setup`, so the web does not load seven
  lists to draw the page.

## 2. Sequences

- A sequence belongs to a term: a number (1 or 2), a label, a start and an end date.
- A term has at most two. They lie inside the term's dates and do not overlap. They need not
  cover the whole term (a week of exams between them is fine). *My call.*
- The label defaults to "Séquence 1" … "Séquence 6" counted across the year, as schools in
  Cameroon number them, and can be changed.
- They are edited on the Calendar screen, under their term, next to the holidays.
- Deleting a term deletes its sequences. Changing a term's dates is refused while a sequence would
  fall outside them.
- API: `GET/POST /terms/{id}/sequences`, `PUT/DELETE /sequences/{id}`, admin only, school from the
  JWT (NFR-8), audited (NFR-11), messages in French and English (NFR-12).
- Nothing else reads sequences yet.

## 3. Reset the school

### When it is allowed

A school is **in preparation** until the admin declares it **live**. Both are new.

- A new school starts in preparation. The two demo schools and any school that exists today also
  start in preparation.
- **Going live** is a button on School settings, "Start the pilot", with a confirmation. It is
  audited and cannot be undone from the web.
- The reset exists only in preparation. Once live, the section disappears and the endpoint
  answers 409.
- While in preparation, the school dashboard and the admin's home show a band: "This school is in
  preparation: its data can still be reset."
- The research export leaves out everything recorded before the school went live.

### What it removes

Everything that was entered or recorded in the school:

- students, guardians, parent accounts, imports
- staff accounts with their roles and subjects, except admins
- academic years, terms, sequences, holidays, periods, blocks, classes, timetable
- attendance sessions and records, lateness, justifications
- alerts, summons, discipline records, counselling cases
- messages, announcements, notifications waiting or sent, SMS digest runs
- sign-in codes, sessions and push tokens of every account removed

### What it keeps

- the school itself (name, language, time zone) and its letterhead and logo
- the alert thresholds
- every account with the admin role, with that role only; other roles they held are removed.
  They stay signed in.
- the audit log, whole. It gains one entry: who reset the school, when, and how many rows of each
  kind were removed. No names and no phones (NFR-9).

### How the admin does it

On School settings, in a section set apart at the bottom:

1. "Reset the school" opens a dialog that lists what will be removed with the real counts
   ("412 students, 38 staff accounts, 2 190 roll calls …") and what is kept.
2. The admin types the school's name exactly and their own password.
3. The button stays disabled until both are filled. A wrong name or password is refused and
   nothing is removed.
4. After the reset the admin lands on School settings, every section "to do".

### How it works

- `GET /school/reset` gives the counts. `POST /school/reset` with `{schoolName, password}` does
  it, in one transaction: all or nothing.
- The tables that refuse deletion (attendance, lateness, alerts, summons, discipline, messages)
  keep their protection. Their triggers let a deletion through only inside a reset transaction
  that names the school, and only for that school's rows. Nothing else in the application can
  set this.
- The audit log's link to user accounts is loosened so that a removed account's id can stay in
  the log. The log itself still refuses every update and delete.
- Admin only, school from the JWT (NFR-8). A staff member who is signed in when the reset happens
  is signed out at their next request.

### Known limits

- A phone that still holds roll calls or lateness waiting to be sent will have them refused after
  the reset; they are lost, as trial data should be.
- The reset does not remove files outside the database, because there are none except the logo,
  which is kept.
- SMS already sent are not unsent, and their cost stays with the provider.

## Decisions for the user

1. **Lock after going live.** Proposed: the reset is possible only while the school is in
   preparation, and "Start the pilot" closes it for good. Without this, one mistake during the
   pilot erases a term of attendance.
2. **What survives.** Proposed: the school, letterhead, alert thresholds and admin accounts.
   Everything else goes, including all staff who are not admins.
3. **Sequences.** Proposed: dates and a label only, shown on the calendar, used by nothing else
   for now.
4. **The menu.** Proposed: the two groups "School" and "People" become the single entry "School
   settings"; the screens are reached from that page.
