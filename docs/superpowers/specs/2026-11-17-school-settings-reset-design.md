# School settings in one place, and sequences (D44)

Status: **scope agreed with the user on 2026-11-17, built the same day (D44 in PLAN.md)**. The first draft of this note also designed a reset of the school's data; that part was dropped, see the end.

## Why

The admin defines a school once before the pilot: the year, its terms and sequences, the day's
periods, the blocks and classes, the timetable, the letterhead. Today those screens are five
separate menu entries and nothing shows, in the place where the admin works, what is defined and
what is still missing. Sequences, the two halves of a term in Cameroonian schools, do not exist.

The user's words: "all school settings (where everything is defined or has to be defined) in one
place"; a term "is a combination of two sequences".

## 1. School settings

A new page, **School settings** (`/setup`), admin only. The menu group "School" (Calendar,
Periods, Classes, Timetable, Letterhead) becomes this single entry.

- One line per thing to define, in the order they depend on each other: school year, terms and
  sequences, holidays; the day's periods; blocks and classes; timetable; letterhead.
- Each line says what is defined ("3 terms, 6 sequences") and whether it is ready or what it
  lacks, and opens the existing screen. The screens keep their content and gain a back link to
  School settings; the menu entry stays highlighted while the admin is in any of them.
- The lines for staff and students are shown too, because the timetable depends on teachers and
  the roll calls on students, and they link to the Staff and Students screens. Those screens
  **stay in the menu** ("People"): they are used all year, not defined once.
- The readiness is the one the admin's home already computes (`SetupOverview`); both read the
  same code, so they cannot disagree. No new endpoint.

## 2. Sequences

- A sequence belongs to a term: a number (1 or 2), a label, a start and an end date.
- At most two per term, inside the term's dates, not overlapping, sequence 1 before sequence 2.
  They need not cover the whole term.
- Edited on the Calendar screen, under their term, like the holidays. The label is proposed as
  "Séquence 1" … "Séquence 6", counted across the year, and can be changed.
- A term with sequences is not deleted (409 `in_use`), like a term with holidays. A term's dates
  cannot change so that a sequence falls outside them (409 `term.sequences_outside`).
- API, like holidays: `GET /sequences?termId=` (every staff member), `POST /sequences`,
  `PUT /sequences/{id}`, `DELETE /sequences/{id}` (admin). School from the JWT (NFR-8), audited
  (NFR-11), messages in French and English (NFR-12).
- First use: the admin's home names the current sequence next to the current term and week, and
  the calendar step is ready only when each term of the current year has its two sequences.
  Nothing else reads sequences yet.

## Dropped: resetting a school's data

The user asked for a way to empty a school of trial data before the pilot. Not built, because:

- Trial data lives in a separate demo school on the developer's machine. The pilot schools are
  created empty on the server (bootstrap), so there is nothing to erase.
- Attendance, lateness, alerts, discipline records and the audit log refuse deletion in the
  database on purpose (BR-4, BR-15, NFR-11). A reset would be a permanent way past that, used
  once per school, able to erase a term of attendance by mistake or with a stolen password.

If trial data ever has to leave a server, the route is to archive the school (hidden from
sign-in, left out of the research export, nothing deleted), not to delete rows.
