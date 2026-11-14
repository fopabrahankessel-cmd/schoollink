# Student and guardian maintenance (D43): design

Status: **approved by the user on 2026-11-14, being built**. Answers: parents keep the history of a child who left; a phone change signs the parent out; the last guardian cannot be removed; end-of-year promotion is in scope.

## Why

After the import (UC-A3), nothing can change a student or a guardian. A school year does not stay still:

- a student arrives in October;
- a student changes class;
- a student leaves the school, and teachers keep seeing the name in the roll;
- a parent changes phone number, and every SMS goes to the old number, with nobody told.

Today each of these needs a developer and SQL on the production database. That is the largest production risk left after D42.

## Goals

The administrator can, from the web dashboard, without help:

1. add one student, with their guardians;
2. correct a student's details (names, sex, birth year, matricule);
3. move a student to another class;
4. mark a student as having left the school, and bring them back;
5. change a guardian's phone, name and language;
6. add a guardian to a student, and remove a guardian from a student;
7. move a whole class to its class of the next year in one step (promotion), leaving out those who repeat or leave.

## Not in this design

- Updating existing students by importing a file again.
- Deleting a student or a guardian. Records are never deleted (BR-15, NFR-11).
- Merging two guardians that are the same person.
- Any change to the Android app. Teachers and assistant DMs get the new lists the next time their phone is online.

## Must never happen

- A student's past absences, lateness, alerts or discipline records change or disappear when the student moves or leaves.
- A parent who changed number keeps receiving nothing, or the old number keeps access to the child.
- An admin of one school sees or changes a student of another (NFR-8).
- A phone number appears in a URL or in a log (NFR-9).

## Behaviour

### Adding a student

The admin gives: matricule, last name, first name, sex, birth year, class, and at least one guardian. For each guardian: phone, name, relation (mother, father, guardian, other), language, and which one is the primary.

- The matricule must be free in the school. Otherwise the form says so.
- A guardian phone already known in the school is **reused**: the existing guardian is linked to the new student, and the form shows the name on file so the admin can check it is the same person. This is what the import does today for brothers and sisters.
- The same rules as the import apply to the phone (Cameroon numbers only, D15) and to the birth year.

### Moving a student to another class

- The student is in the new class from that moment: the next roll call of the new class includes them, the old class's does not.
- Sessions already recorded stay as they are (BR-4). The student's history shows them with the class they were recorded in.
- Open alerts stay open. The DM of the new class's block sees them, since scope follows the student's class.
- A phone that still holds the old list is covered by D42: an absentee who is not in the class any more is left out and audited.

### Moving a whole class (promotion)

- The admin first creates the next academic year and its classes (UC-A1, already built).
- On a class, "Move the students" lists its students, all ticked, and asks for the destination class, of any academic year.
- The admin unticks the students who repeat the year or leave. Those stay where they are; the admin then moves them one by one or marks them as left.
- The ticked students move in one step: all of them or none. Each move is audited like a single move.
- Nothing else changes: records, alerts and guardians follow the student.

### A student who left

- The admin marks the student as left, with the date (today by default, not in the future).
- From then on the student is in no roll call, not in the gate list or search, and not counted in a class's student count. No new absence, lateness or alert can be raised for them.
- Everything already recorded stays, and still counts in the dashboards for the days it happened.
- Their open alerts stay open for the DM to close. *(My call: closing them automatically would be a decision taken by the system, which BR-16 to BR-19 forbid.)*
- Guardians keep the child in their app, with the history, marked as having left. They get no new notification about that child. *(My call.)*
- The matricule stays taken.
- The admin can bring the student back ("returned"), in the same class or another one. The time away has no records; nothing is filled in.

### Changing a guardian

- **Name and language:** changed at once. The language applies to the next notification (BR-20).
- **Phone:** the new number must be valid (D15) and not be another guardian's in the school. If it is, the form says who has it, so the admin links that guardian to the child instead.
- If the guardian has an app account, the account's sign-in phone changes too, in the same step. Every phone signed in to that account is signed out and stops receiving push, and any SMS code waiting for the old number stops working. The parent signs in again with the new number (D24). *(My call: the old number may now belong to somebody else.)*
- A number that belongs to a staff account of the school is refused (D21: staff sign in with a password).
- SMS already sent are not sent again. An SMS still waiting goes to the new number.

### Linking and unlinking

- Adding a guardian to a student works as in "adding a student": an existing phone reuses the guardian.
- Removing a guardian from a student removes the link only. The guardian stays, with their other children. A guardian left with no child keeps their account but sees no child.
- A student must keep at least one guardian. The last one cannot be removed. *(My call: a student nobody can be told about defeats the purpose of the product.)*
- Exactly one guardian is the primary. Removing the primary asks which one becomes it.

## Screens (web, admin)

A new "Élèves / Students" entry in the admin navigation.

- **List:** search by name or matricule, filter by class, a switch to show the students who left. Columns: matricule, name, class, guardians (count), status.
- **Student page:** details with an "Edit" action; the class with "Change class"; "Mark as left" or "Bring back"; the guardians, each with relation, name, phone, language, whether they have signed in to the app, and "Edit" and "Remove"; "Add a guardian".
- **Add a student:** one form, with the guardians inline.
- Every destructive step (left, remove a guardian, change a phone that has an app account) asks for confirmation and says what will happen.

All text in French and English (NFR-12).

## API (admin only; school from the token, NFR-8)

| Method and path | Purpose |
| --- | --- |
| `GET /students?classId=&q=&left=` | List. `q` is a name or a matricule, never a phone (NFR-9) |
| `GET /students/{id}` | One student with their guardians |
| `POST /students` | Add a student with guardians |
| `PUT /students/{id}` | Correct the details |
| `PUT /students/{id}/class` | Move to another class |
| `POST /students/move` | Move several students to one class (promotion): `{studentIds, classId}` |
| `POST /students/{id}/leave`, `POST /students/{id}/return` | Left, came back |
| `POST /students/{id}/guardians` | Add a guardian (new, or existing by phone) |
| `PUT /students/{id}/guardians/{guardianId}` | Relation, primary |
| `DELETE /students/{id}/guardians/{guardianId}` | Remove the link |
| `PUT /guardians/{id}` | Name, language, phone |

Each write is one transaction and writes to `audit_log` with ids and field names, the old and new class, and for a phone only the fact that it changed (NFR-9, NFR-11). Each endpoint gets the permission test per role.

## Data

- `student.left_on date NULL`. Every place that lists "the students of a class" for new work (roll, gate list, counts, announcements, message recipients) leaves out students with `left_on` set. Places that show history do not.
- No other table changes. A class move is an update of `student.class_id`; past sessions keep their own `class_id`.

## Left to the builder

Layout details of the pages, the exact wording of the messages, and how the confirmation dialogs look, following the existing admin pages (staff, guardians).
