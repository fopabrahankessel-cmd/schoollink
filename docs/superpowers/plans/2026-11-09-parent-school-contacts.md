# Parent school contacts (UC-P5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A parent sees, for each child, the class, the teachers with their subjects, and the block's DMs and assistant DMs, each with a phone number and call and SMS buttons.

**Architecture:** One read-only endpoint in the `people` module (it owns `app_user`, `role_assignment` and `teaching_assignment`), using `SchoolClasses.blockOf` from the `school` module for the class's block. The mobile child page gets two tabs, History and Contacts; the call and SMS buttons hand a `tel:` or `sms:` link to the phone through `url_launcher`, so SchoolLink sends nothing.

**Tech Stack:** Spring Boot 4, JDBC (`NamedParameterJdbcTemplate`), JUnit 5 + Testcontainers + `PermissionMatrix`; Flutter, Riverpod, dio, `url_launcher`, ARB (FR/EN).

**Spec:** `docs/superpowers/specs/2026-11-09-dm-workspace-messages-design.md`, §4.5 and §5.3. This is plan 1 of 5 (design §9 order: contacts, disciplinary records, messages and announcements, today's attendance, navigation and parity).

## Global Constraints

- NFR-8: the school and the parent's children come from the JWT principal (`CurrentUser.schoolId()`, `CurrentUser.children()`), never from the request. Every endpoint gets a permission-matrix test, allowed and forbidden.
- NFR-9: phone numbers travel in response bodies only, never in a URL or a log line.
- NFR-12: every new user-facing string in `app_fr.arb` and `app_en.arb` in the same change; run `flutter gen-l10n`.
- Design §4.5: disabled staff accounts (`app_user.status = 'DISABLED'`) are left out; a staff member with no phone shows "no number"; names, subjects, roles and phones only, no emails.
- Fake data only in tests (CLAUDE.md).
- Cite the rule numbers (UC-P5, D39, NFR-8, NFR-9) in commit messages and in comments where the rule is implemented.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never commit `backend/.run/SchoolLink (dev).run.xml`.

## Review Focus

1. A teacher who teaches the child's class two subjects appears once, with both subjects, not twice.
2. A child whose class's block has no DM yet: the discipline list is empty and the screen says so, rather than an error.
3. A staff member with the DM role in two blocks, or both teacher and DM of the class, appears in each list where they belong, once per list.
4. The parent is offline: the contacts saved on the phone show, as the history does (NFR-2).
5. The phone cannot open the dialler (no SIM app, tablet): the tap shows a message instead of doing nothing.

Each line has its test in the task that owns the code (Task 1: lines 1 to 3; Task 2: lines 4 and 5).

---

### Task 1: `GET /me/children/{id}/contacts`

**Files:**
- Create: `backend/src/main/java/com/schoollink/people/internal/ChildContacts.java`
- Modify: `backend/src/main/java/com/schoollink/people/internal/ChildrenController.java`
- Test: `backend/src/test/java/com/schoollink/people/internal/ChildContactsTests.java`
- Modify: `docs/SPEC.md` (§5 use cases, §9 API outline), `docs/PLAN.md` (decision D39)

**Interfaces:**
- Consumes: `SchoolClasses.blockOf(UUID schoolId, Set<UUID> classIds): Map<UUID, UUID>`, `SchoolClasses.names(...)`, `Students.classOf(UUID schoolId, UUID studentId): Optional<UUID>`, `CurrentUser.children(): Set<UUID>`.
- Produces: `GET /api/v1/me/children/{id}/contacts` returning JSON
  ```json
  {
    "className": "6e A",
    "teachers":   [{ "name": "Paul Enseignant", "subjects": ["Maths", "Physique"], "phone": "+237690000010" }],
    "discipline": [{ "name": "Marie Surveillante", "role": "DM", "phone": null }]
  }
  ```
  `role` is `DM` or `ASSISTANT_DM`. Task 2 parses exactly these names.

- [ ] **Step 1: Write the failing tests** in `ChildContactsTests` (same setup as `ParentApiTests`: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`, `TestWorld.create`, `TestcontainersConfiguration`). The test sets phones with `UPDATE app_user SET phone = ? WHERE id = ?` (fake `+2376900000xx` numbers) and names with `first_name`/`last_name`.

  ```java
  @TestFactory Stream<DynamicTest> onlyAParentReadsTheirChildsContacts()
  // PermissionMatrix.row("GET /me/children/{own}/contacts", w -> get(".../{id}/contacts", w.studentA))
  //   .expect(200, PARENT_A).expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, RESEARCHER, ADMIN_Y).expect(401, ANONYMOUS)

  @TestFactory Stream<DynamicTest> aParentCannotReadAnotherFamilysChildContacts()
  // a student of class A with no link to PARENT_A: 404 for PARENT_A, the rest as above

  @Test void listsTheClassTeachersWithTheirSubjectsAndTheBlockDisciplineStaff()
  // TEACHER_A also teaches 'Physique' in class A (second teaching_assignment row)
  // expect className "6e A"; teachers.length() 1; teachers[0].subjects ["Maths", "Physique"];
  // teachers[0].phone "+237690000010"; discipline = DM_1 (role "DM") then ASSISTANT_DM_1 (role "ASSISTANT_DM")

  @Test void leavesOutOtherClassesOtherBlocksAndDisabledStaff()
  // a teacher of class B, DM_2 (block 2) and a DISABLED teacher of class A are not in the answer

  @Test void aStaffMemberWithNoPhoneHasANullPhone()     // TestWorld users have email only: phone is null
  @Test void aClassWhoseBlockHasNoDmHasAnEmptyDisciplineList() // remove DM_1 and ASSISTANT_DM_1's role rows
  @Test void aTeacherWhoIsAlsoTheDmIsInBothLists()       // give TEACHER_A a DM role on block 1
  ```

- [ ] **Step 2: Run them to verify they fail**

  Run: `cd backend && ./mvnw -q test -Dtest=ChildContactsTests`
  Expected: FAIL with 404 (no handler) on every allowed case.

- [ ] **Step 3: Implement `ChildContacts`**, a `@Component` with `Contacts of(UUID schoolId, UUID classId)` and the records `Contacts(String className, List<Teacher> teachers, List<DisciplineStaff> discipline)`, `Teacher(String name, List<String> subjects, String phone)`, `DisciplineStaff(String name, String role, String phone)`.
  - Teachers: `teaching_assignment` joined to `app_user` on `(school_id, teacher_id)`, `class_id = :class`, `status <> 'DISABLED'`; grouped by teacher (`array_agg(subject ORDER BY subject)`); ordered by last name, first name.
  - Discipline: `role_assignment` with `role IN ('DM', 'ASSISTANT_DM')` and `block_id = :block`, joined to `app_user`, `status <> 'DISABLED'`; ordered DM first, then last name.
  - Name: `first_name || ' ' || last_name`.

  In `ChildrenController` add `@GetMapping("/api/v1/me/children/{id}/contacts")`, `@Transactional(readOnly = true)`: 403 unless the user is a parent with a school (as `myChildren`); 404 unless `user.children().contains(id)` (NFR-8: a child of another family looks like no child); class from `Students.classOf`, block from `SchoolClasses.blockOf`. Comment: `UC-P5, D39`.

- [ ] **Step 4: Run the tests to verify they pass**

  Run: `cd backend && ./mvnw -q test -Dtest=ChildContactsTests`
  Expected: PASS, every test.

- [ ] **Step 5: Update the docs**
  - `docs/SPEC.md` §5, after UC-P4: `| UC-P5 | Parent | See each child's class, teachers with their subjects, and the block's DMs and assistant DMs, with their phone numbers | Yes |`
  - `docs/SPEC.md` §9, after `GET /me/children`: `| People | GET /me/children/{id}/contacts | Parent | The child's class, teachers and the block's DMs, with names, subjects, roles and phones (UC-P5, D39) |`
  - `docs/SPEC.md` §3 permission matrix: a row `| Staff names, subjects and phones | W | — | — | — | — | R (own children's teachers and DMs) | — |`.
  - `docs/PLAN.md`, decision D39 after D36: "School contacts for parents: a parent sees, for each child, the teachers of the class with their subjects and the DMs and assistant DMs of its block, with names, roles and phone numbers (decided by the user, 2026-11-09). Call and SMS open the phone's own apps; SchoolLink sends nothing. Disabled accounts are left out."

- [ ] **Step 6: Run the whole backend suite**

  Run: `cd backend && ./mvnw verify`
  Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 7: Commit**

  ```bash
  git add backend/src/main/java/com/schoollink/people/internal/ChildContacts.java backend/src/main/java/com/schoollink/people/internal/ChildrenController.java backend/src/test/java/com/schoollink/people/internal/ChildContactsTests.java docs/SPEC.md docs/PLAN.md
  git commit -m "UC-P5, D39: a parent reads the teachers and DMs of their child, with phones"
  ```

### Task 2: Contacts tab on the parent's child page

**Files:**
- Create: `mobile/lib/features/parent/contacts.dart` (model, provider, launcher seam)
- Create: `mobile/lib/features/parent/contacts_view.dart` (the tab's widget)
- Modify: `mobile/lib/features/parent/parent.dart` (repository method)
- Modify: `mobile/lib/features/parent/history_screen.dart` (two tabs)
- Modify: `mobile/pubspec.yaml` (`url_launcher: ^6.3.1`), `mobile/android/app/src/main/AndroidManifest.xml` (`<queries>`)
- Modify: `mobile/lib/l10n/app_fr.arb`, `mobile/lib/l10n/app_en.arb`
- Test: `mobile/test/features/parent_contacts_test.dart`

**Interfaces:**
- Consumes: Task 1's JSON.
- Produces:
  - `class ChildContacts { String? className; List<TeacherContact> teachers; List<DisciplineContact> discipline; DateTime? fetchedAt; factory ChildContacts.fromJson(Map<String, dynamic> json, {DateTime? fetchedAt}); }`
  - `class TeacherContact { String name; List<String> subjects; String? phone; }`, `class DisciplineContact { String name; bool assistant; String? phone; }` (`assistant` = `role == 'ASSISTANT_DM'`)
  - `ParentRepository.contacts(String userId, String childId): Future<ChildContacts>`, using the existing `_get` with cache key `'contacts:$childId'`, so offline it returns the copy on the phone.
  - `final childContactsProvider = FutureProvider.autoDispose.family<ChildContacts, String>`
  - `final phoneLauncherProvider = Provider<Future<bool> Function(Uri)>((ref) => (uri) => launchUrl(uri, mode: LaunchMode.externalApplication));`, the seam tests override.

- [ ] **Step 1: Write the failing widget tests** in `parent_contacts_test.dart`, with the same `FakeServer` setup as `parent_test.dart` plus `server.on('GET /me/children/st-1/contacts', ...)` returning two teachers (one with two subjects), one DM with a phone and one assistant DM without. Override `phoneLauncherProvider` with a fake that records the `Uri` and returns `true` (or `false` for the last test).

  ```dart
  testWidgets('the child page has a Contacts tab with teachers, subjects and the DMs (UC-P5)')
  // open Enfants > the child > tab 'Contacts'
  // expect 'Paul Enseignant' and 'Maths, Physique'; 'Surveillant général' under the DM's name;
  // 'Surveillant général adjoint' under the assistant; 'Pas de numéro' for the assistant
  testWidgets('call and SMS open the phone with the number, never sent to the server (NFR-9)')
  // tap the call button of the DM: launcher got Uri.parse('tel:+237690000020');
  // tap the SMS button: Uri.parse('sms:+237690000020'); server.requests has no path containing '690000020'
  testWidgets('a block with no DM yet says so')  // discipline: [] -> 'Aucun surveillant pour cette classe pour le moment.'
  testWidgets('offline, the contacts saved on the phone show')  // load once, go offline, reopen: names still shown
  testWidgets('a phone that cannot call shows a message')  // launcher returns false -> SnackBar "Impossible d'ouvrir l'application Téléphone."
  testWidgets('the contacts read in English (NFR-12)')  // 'Contacts', 'Discipline Master', 'Assistant Discipline Master', 'No number'
  ```

- [ ] **Step 2: Run them to verify they fail**

  Run: `cd mobile && flutter test test/features/parent_contacts_test.dart`
  Expected: FAIL, the 'Contacts' tab is not found.

- [ ] **Step 3: Implement**
  - `pubspec.yaml`: add `url_launcher: ^6.3.1`; run `flutter pub get`.
  - `AndroidManifest.xml`, inside the existing `<queries>`: two `<intent>` entries, action `android.intent.action.VIEW` with `<data android:scheme="tel"/>`, and the same with `sms` (Android 11+ package visibility).
  - `ChildHistoryScreen` becomes a `DefaultTabController(length: 2)` with a `TabBar` in the `AppBar.bottom`: "Historique" (the current body, unchanged) and "Contacts" (`ContactsView(childId)`). The AppBar title stays the child's first name.
  - `ContactsView`: pull to refresh; skeleton while loading; `FailureView` on error (as the history); the class name as a header; a `SectionHeader` "Enseignants" with one `ListTile` per teacher (name, subjects joined with ", ", two `IconButton`s, `Icons.call_outlined` and `Icons.sms_outlined`, each with a tooltip, 48 dp); a `SectionHeader` "Surveillance" with the DMs the same way, role as the subtitle. A contact with no phone shows "Pas de numéro" and no buttons. On `false` from the launcher, a `SnackBar`.
  - ARB keys, FR / EN: `tabHistory` Historique / History; `tabContacts` Contacts / Contacts; `contactsTeachers` Enseignants / Teachers; `contactsDiscipline` Surveillance / Discipline; `roleDm` Surveillant général / Discipline Master; `roleAssistantDm` Surveillant général adjoint / Assistant Discipline Master; `contactsNoPhone` Pas de numéro / No number; `contactsNoDiscipline` Aucun surveillant pour cette classe pour le moment. / No discipline staff for this class yet.; `contactsCall` Appeler {name} / Call {name}; `contactsSms` Envoyer un SMS à {name} / Text {name}; `contactsCannotOpen` Impossible d'ouvrir l'application Téléphone. / Could not open the Phone app.; `contactsNoTeachers` Aucun enseignant pour cette classe pour le moment. / No teachers for this class yet. Run `flutter gen-l10n`.

- [ ] **Step 4: Run the tests to verify they pass**

  Run: `cd mobile && flutter test test/features/parent_contacts_test.dart`
  Expected: PASS, 6 tests.

- [ ] **Step 5: Run the whole mobile check**

  Run: `cd mobile && flutter analyze && flutter test`
  Expected: "No issues found!" and "All tests passed!" (the existing `parent_test.dart` still passes: the history stays the first tab).

- [ ] **Step 6: Check on the emulator**

  Start the backend with the dev profile (or on 8081 if the user's backend runs on 8080), run `flutter run -d emulator-5554`, sign in as a dev-seed parent, open a child, then the Contacts tab: teachers and DMs listed; the call button opens the dialler with the number. Take one screenshot for the report.

- [ ] **Step 7: Commit**

  ```bash
  git add mobile/lib/features/parent mobile/lib/l10n mobile/pubspec.yaml mobile/pubspec.lock mobile/android/app/src/main/AndroidManifest.xml mobile/test/features/parent_contacts_test.dart
  git commit -m "UC-P5, D39: the child page shows who to contact, with call and SMS"
  ```
