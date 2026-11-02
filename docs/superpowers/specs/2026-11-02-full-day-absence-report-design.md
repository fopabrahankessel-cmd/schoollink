# Full-day absence report — design

Date: 2026-11-02. Requested by the project owner for the Discipline Masters of the pilot schools.
Proposed spec numbers: **UC-D9** (DM generates and shares the report) and **UC-A7** (admin sets the
school letterhead); decision **D36** in PLAN.md. "My call" marks choices made by the designer, not
by the owner; everything else was decided in conversation.

## Why

Each evening the Discipline Master (DM) tells the school which students missed the whole day. Today
that list is written by hand and photographed into a WhatsApp group. The DM wants SchoolLink to
produce it as an official-looking PDF, with the school's letterhead, that he can preview and share
from his Android phone through the normal share menu (WhatsApp, Bluetooth, e-mail, …).

## Goals

- On the mobile app, a DM picks a school day and gets a PDF of the students of **his own block**
  who were absent the whole day, **one table per class**.
- The PDF carries the school's bilingual letterhead (French left, logo centre, English right, as on
  the school's paper) and shows the day prominently.
- He previews it in the app and shares it as a PDF through Android's share sheet.
- The school administrator sets the letterhead on the web dashboard, with enough freedom to
  reproduce the school's paper.

**A DM with several blocks** (the data model allows it; D5 only says one DM per block): my call,
one report covering all his classes. Tables are grouped by block (block name as a sub-heading,
then classes by name), the summary line names the blocks, and the file name drops the block
(`absences-2026-11-03.pdf`). With a single block, the block sub-heading is left out.

## Non-goals

- Whole-school reports, or the report for the admin or assistant DMs. Only the DM, only his block.
- Generating on the web, scheduled/automatic sending, or SchoolLink posting to WhatsApp itself.
  Sharing is always the DM's manual action from the share sheet.
- Other documents using the letterhead (summons letters etc.). The letterhead is built so it can be
  reused later, but nothing else uses it now.
- Offline generation. DM screens are online (only teacher and assistant DM screens are offline-first).

## Anti-goals (would make it a failure even if it works)

- A student listed who was **not** absent from every lesson of the day, or a report that disagrees
  with the full-day absence alert's definition of "the whole day".
- A DM seeing or exporting students outside his block (NFR-8).
- Letterhead editable by anyone but the admin.
- Hard-coded French or English text in the document or screens (NFR-12).

## The report

### Who is listed (decided)

A student is listed for day *d* when, for **every timetabled period of their class on that weekday**,
there is a recorded session and the student's record in it is ABSENT, **not cancelled** (BR-15) and
**not justified**.

- This is the same "every timetabled period" rule as the full-day absence alert (D2 in PLAN.md).
  The owner confirmed it on 2026-11-02, which closes the open question from task 3.13. Update the D2
  wording in PLAN.md accordingly.
- If a timetabled lesson was **never recorded**, nobody in that class can be listed for that day
  (we cannot say they missed it). The report warns about it instead (below).
- **Justified absences are left off** (the owner: "after justification the absences are erased").
  If any one of the day's absences is justified, the student is not listed. In SchoolLink a
  justification does not delete the record; the report simply ignores justified ones. Note this is
  stricter than the alert, which is not withdrawn by a later justification (task 3.4).
- A class with no timetabled lesson that day does not appear.

### Which day (decided)

- Any past or current **school day** (D3/D10 calendar: Mon–Fri within a term, not a holiday). No
  future days.
- Default when the screen opens: **today from 17:00 school time**, otherwise **the previous school
  day**. Before 17:00, today cannot be chosen (its lessons are not finished). 17:00 is fixed in this
  version (my call; it could become a school setting later).

### Content of the PDF (decided, see mockup in `.superpowers/brainstorm/…/report-page.html`)

1. Letterhead (below).
2. Title "Students absent the whole day" and the **date in large type** (e.g. "Mardi 3 novembre 2026").
3. A summary line: block name, DM's name, "N students in M classes".
4. One table per class that has listed students, classes in name order: columns N°, last name and
   first name (last name first), matricule; students by last name then first name.
5. A class with an unrecorded lesson can never have listed students (rule above). So the warning
   the owner asked to keep goes after the tables, as one section naming every class of the scope
   with unrecorded lessons that day: "Lessons not recorded, so full-day absences there cannot be
   known: 4e B (P5), 3e A (P2, P3)." (The first mockup showed this note under a listed class; that
   combination cannot happen under the rule, and the rule wins.)
7. Footer: "Generated by SchoolLink on <date> at <time>", page "x/y". Tables continue across pages,
   repeating the column headings.

Everything except the letterhead is in the **DM's app language** (FR or EN). The letterhead is
printed as the admin typed it.

### When nobody is listed (decided)

**No PDF is made.** The screen says "No student was absent from all the school periods on <date>."
My call: if some lessons were not recorded that day, the message adds the same "Lessons not
recorded: …" line, so the DM knows the answer may be incomplete.

### Mobile flow

1. DM home → "Full-day absences" (new entry).
2. Date picker with the default above; "Generate".
3. Preview of the PDF (scrollable, pinch-zoom), with a **Share** button that opens Android's share
   sheet with the PDF file (file name like `absences-2026-11-03.pdf`, my call).
4. Errors (offline, server error) show the usual retry message.

## Letterhead (UC-A7)

Set by the **admin only**, on the web dashboard, in a new "Letterhead" section next to the existing
school settings. Read by anyone in the school who can generate a document (here: the DM).

Decided:

- Two columns of lines: **French** (left) and **English** (right), typed one line per row.
- Per line: text, **bold** or not, **text size** (small / normal / large; my call on three steps),
  and the line can be **moved up or down**. The school-name line is simply a bold line.
- A choice of layout: **both languages** (FR left, logo centre, EN right) or **one language**
  (that column centred under or beside the logo — exact arrangement my call).
- A **logo** upload (PNG or JPEG), shown in the centre. My call: at most 1 MB, stored in the
  database, scaled to fit about 3 cm high; the admin can remove it.
- A live preview of the header as the PDF will show it.
- Until the admin sets anything, the PDF header is just the school's name, centred, without a logo.

The logo is the school's emblem, not a person; the "no photos" rule (NFR-10) is about students and
is unaffected.

## Constraints

- **Scope (NFR-8):** school and block come from the JWT principal. A DM asking for a date gets only
  his block's classes. Integration tests per role: DM allowed (own block only), other roles
  forbidden; admin allowed for the letterhead, others forbidden to change it.
- **Audit (NFR-11):** generating the report is an export of personal data: write an `audit_log`
  entry (actor, date reported, number of students), no names. Changing the letterhead is an edit:
  audit it too.
- **Privacy (NFR-9):** no phone numbers in the report, URLs or logs. The report holds names,
  matricules and classes only. Once shared, the file leaves SchoolLink's control; that is the DM's
  decision, as with today's handwritten list.
- **Languages (NFR-12):** every new string in FR and EN (mobile ARB, web i18n, API errors). Dates
  formatted in the DM's language.
- **Modules:** the listing is a read model over attendance, timetable and people. It belongs in
  `reporting` (my call), using other modules' public interfaces. The letterhead belongs in `school`.
- **Generated data only** in seeds and tests.

## Left to the builder

- API shape. Suggested: `GET /api/v1/reports/full-day-absences?date=` (DM) returning the day, block,
  classes with listed students and unrecorded lessons; `GET/PUT /api/v1/school/letterhead` and
  `GET/PUT/DELETE /api/v1/school/letterhead/logo` (read: any signed-in staff of the school; write:
  admin).
- Where the PDF is drawn: suggested on the phone from the JSON (e.g. the `pdf` and `printing`
  Flutter packages, which also give preview and the share sheet), embedding a font with French
  accents.
- Table styling, page margins, A4 portrait.
- Whether the report query reuses the detection module's full-day evaluator or has its own SQL, as
  long as both follow the rule above and a test shows they agree on the same data (except for
  justifications).
