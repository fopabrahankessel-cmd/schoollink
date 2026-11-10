# Parent Messages and Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A DM writes to the guardians of one student or one class of their blocks, and an administrator writes to the guardians of the whole school or one class. Both see the count and the SMS cost before sending, and later see the delivery of each message. Parents read them in the app inbox (D38: UC-D4, UC-A5, UC-P4).

**Architecture:**
- A `parent_message` table in the `notification` module. It is append-only and owns the outbox rows its sending writes.
- One service, `ParentMessages`, serves both authors. It resolves the audience inside the author's scope from the JWT, splits the guardians into push / SMS / unreached, checks the monthly SMS budget, then writes the message, its outbox rows and the audit entry in one transaction.
- The existing dispatcher sends the rows. The existing inbox shows them, once `Templates` knows the two event types.
- The clients never compute counts or SMS units themselves. They call a preview endpoint as the text changes, so Android and the web always agree.

**Tech Stack:** Spring Boot 4 (JDBC, Flyway, Testcontainers), Flutter (Riverpod, dio, ARB), React + MUI + i18next (Vitest).

**Spec:** `docs/superpowers/specs/2026-11-09-dm-workspace-messages-design.md` (§4.2, §4.3, §4.4, §5.1 composer patterns, §5.2 web composer, §5.3 parent inbox, §6, §11 calls 1–3 and 7).

## Global Constraints

- Modular monolith: `parent_message` lives in `notification` (it owns the outbox and the templates). Other modules are used only through their public interfaces: `people.Students`, `people.Guardians`, `school.SchoolClasses`, `school.Schools`, `audit.AuditLog`.
- NFR-8: the scope comes from the JWT.
  - DM: classes of their blocks (`user.classesAs(Role.DM)`).
  - Admin: their own school.
  - Parent: their own guardian rows.
  - Out of scope gives 404; the wrong role gives 403.
  - Every endpoint gets one integration test per role, covering both the allowed and the forbidden case.
- NFR-9: no phone number in URLs, logs, previews or delivery reports.
- NFR-11: every message and announcement sent writes one `audit_log` row (`AuditAction.CREATE`, entity `parent_message`).
- NFR-12: every new string exists in FR and EN: `messages_fr/en.properties` (errors and push/SMS templates), ARB, and i18next.
- BR-20, NFR-6: the message row, its outbox rows and the audit row are written in one transaction. Nothing is sent inline.
- BR-25: message and announcement SMS count against the monthly cap. Only summons pass the cap; the dispatcher already rejects other SMS at the cap.
- Messages are one-way, never edited and never deleted. They have no attachments and are never scheduled.
- Body: 1 to 480 characters after `strip()`.
- SMS template wording:
  - FR announcement: `SchoolLink - {school} : {body}`
  - FR DM message: `SchoolLink - SG {school} : {body}`
  - EN announcement: `SchoolLink - {school}: {body}`
  - EN DM message: `SchoolLink - DM {school}: {body}`
- SMS defaults: the SMS box is ticked by default for one student and unticked for a class or the school. A push that is never opened gets no later SMS.
- Sending is refused (409) when SMS is ticked and the SMS count is more than the SMS left this month.
- Price per SMS: the new column `school.sms_unit_price`, an integer in FCFA, default 181. Editing it is out of this plan.
- No real student data. Fixtures use made-up names.
- Commit messages cite D38 and the UC numbers. Each ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Double tap or network retry on Send:** the same client `id` sent twice gives one message, one set of outbox rows and one audit row. The second call returns the first message (200).
2. **A guardian with two children in the targeted class or school:** they get one push or one SMS, not two.
3. **Two class messages sent close together near the cap:** "SMS left" also subtracts SMS that are queued but not yet sent. So the second message is refused, instead of both passing and the second being rejected by the dispatcher.
4. **A body with ç, é, ê or œ:**
   - The preview says the SMS uses 70-character units.
   - With `plainSms`, only the SMS text is transliterated (ç→c, é→e, œ→oe). The push and the inbox keep the original text.
5. **Out of scope:** a DM targeting a student or class outside their blocks, or an admin targeting a class of another school, gets 404 with nothing written. The same applies to preview.

Each line has its test in the owning task: 1, 2, 3 and 5 in Task 1; 4 in Task 1 (preview units) and Task 2 (SMS text).

---

### Task 1: Backend — compose, preview and send

**Files:**
- Create: `backend/src/main/resources/db/migration/V29__parent_message.sql`
- Create: `backend/src/main/java/com/schoollink/notification/internal/ParentMessages.java` (service)
- Create: `backend/src/main/java/com/schoollink/notification/internal/ParentMessageController.java`
- Create: `backend/src/main/java/com/schoollink/notification/internal/SmsText.java` (GSM-7 units and transliteration, pure functions)
- Modify: `notification/internal/SmsUsage.java` (add `left`)
- Modify: `people/Students.java` and its JDBC implementation (add `idsInSchool`)
- Modify: `messages_fr.properties` and `messages_en.properties` (errors)
- Test: `backend/src/test/java/com/schoollink/notification/internal/ParentMessageTests.java`
- Test: `backend/src/test/java/com/schoollink/notification/internal/SmsTextTests.java`

**Interfaces:**
- Produces (SQL), the table `parent_message`:
  - `id uuid PK` (the client's id)
  - `school_id`, `author_id` (the user)
  - `kind` in `DM_MESSAGE` or `ANNOUNCEMENT`
  - `audience` in `STUDENT`, `CLASS` or `SCHOOL`
  - `student_id` and `class_id`, nullable, with FKs on `(school_id, …)`
  - `body varchar(480)`, `sms boolean`, `plain_sms boolean`, `created_at`
  - CHECKs:
    - STUDENT ⇔ `student_id` set and `class_id` null;
    - CLASS ⇔ `class_id` set and `student_id` null;
    - SCHOOL ⇔ both null;
    - `kind = 'DM_MESSAGE'` ⇒ `audience <> 'SCHOOL'`;
    - `kind = 'ANNOUNCEMENT'` ⇒ `audience <> 'STUDENT'`.
  - A trigger refuses UPDATE and DELETE, with the message `parent_message % cannot change`.
  - Index on `(school_id, kind, created_at DESC)`.
  - `ALTER TABLE school ADD COLUMN sms_unit_price integer NOT NULL DEFAULT 181 CHECK (sms_unit_price >= 0)`.
- Produces (Java):
  - `SmsText.units(String text) -> int`:
    - GSM-7: 160 per unit, or 153 when several are needed. The extension characters `^{}\[~]|€` count as 2.
    - Otherwise 70 per unit, or 67 when several are needed.
  - `SmsText.isGsm7(String) -> boolean`
  - `SmsText.plain(String) -> String`: NFD, then remove combining marks, plus `œ→oe`, `Œ→OE`, `æ→ae`, `’→'`, `«`/`»`→`"`.
  - `SmsUsage.left(UUID schoolId) -> int`: `max(0, cap − sentThisMonth − pending)`, where pending = SMS rows with status `QUEUED` or `SENDING`.
  - `Students.idsInSchool(UUID schoolId) -> Set<UUID>`: the same enrolment rule as `idsInClass`, for all classes of the school's current year.
  - In `ParentMessages`:
    - `record Target(UUID studentId, UUID classId)`
    - `record Draft(UUID id, UUID studentId, UUID classId, String body, boolean sms, boolean plainSms)`. For an announcement `studentId` must be null, and a null `classId` means the whole school.
    - `record Preview(int recipients, int byApp, int bySms, int unreached, int smsUnits, boolean smsUnicode, int smsLeft, int estimatedCost, boolean overCap, String smsSample)`. `smsSample` is the SMS in the author's language, with `plainSms` applied, or null when `sms` is false.
    - `record ClassOption(UUID classId, String className, int students)`
    - `Preview preview(CurrentUser user, Kind kind, Draft draft)`
    - `Sent send(CurrentUser user, Kind kind, Draft draft)` with `record Sent(UUID id, boolean created)`. `created` is false for a repeated id; the controller answers 201 or 200 with `{id}`.
    - `List<ClassOption> classes(CurrentUser user)`
    - `enum Kind { DM_MESSAGE, ANNOUNCEMENT }`
- Produces (HTTP):
  - `POST /api/v1/messages/preview` and `POST /api/v1/messages`: DM only. The body is `{id, studentId | classId, body, sms, plainSms}`. Send returns 201, or 200 for a repeated `id`.
  - `POST /api/v1/announcements/preview` and `POST /api/v1/announcements`: ADMIN only. The body is `{id, classId?, body, sms, plainSms}`.
  - `GET /api/v1/messages/classes`: DM gets the classes of their blocks; ADMIN gets the classes of the current year. Both are sorted by name.
- Errors (400 unless marked):
  - `messages.body_required` (field `body`)
  - `messages.body_too_long` (field `body`, 480)
  - `messages.target_required` (field `target`, for a DM sending neither or both of `studentId` and `classId`)
  - `messages.id_required` (field `id`)
  - 409 `messages.sms_over_cap` (with `bySms` and `smsLeft` in the message)
  - 409 `messages.no_recipient` (no guardian can be reached)
  - 409 `messages.id_reused` (the same id with different content)
  - 404 `not_found` (out of scope)
  - 403 (wrong role)

- [ ] **Step 1: Write the failing tests**

`SmsTextTests`:
```java
@Test void plainAsciiIsOneUnitUpTo160() // "a".repeat(160) → 1; 161 → 2; 306 → 2; 307 → 3
@Test void extensionCharactersCountTwice() // "€".repeat(80) → 1; 81 → 2
@Test void accentsSwitchTo70CharacterUnits() // "é".repeat(70) → 1; 71 → 2; isGsm7("élève") false
@Test void plainRemovesAccentsAndLigatures() // plain("Ça, élève, cœur, l’été") → "Ca, eleve, coeur, l'ete"; isGsm7 of it true
```

`ParentMessageTests` (the same Testcontainers base and fixture style as `DisciplinaryRecordTests`, with made-up names; the DM's block holds class A, and class B is in another block). The setup:
- class A has three students; one guardian is linked to two of them;
- two guardians have an app account, two do not;
- the school's cap is 10 and 0 SMS are sent.

```java
@Test void dmPreviewOfAClassCountsEachGuardianOnce()        // recipients 4, byApp 2, bySms 2 (sms=true), unreached 0
@Test void withoutSmsTheGuardiansWithoutAppAreUnreached()   // sms=false → bySms 0, unreached 2, estimatedCost 0, smsSample null
@Test void previewGivesUnitsCostAndSample()                 // body "Apportez la tenue de sport demain." → smsUnits 1, smsUnicode false, estimatedCost 2*1*181, smsSample starts "SchoolLink - SG "
@Test void accentsMakeTheSmsUnicodeUnlessPlain()            // body "Réunion à l'école" → smsUnicode true; plainSms=true → smsUnicode false, sample contains "Reunion a l'ecole"
@Test void sendWritesMessageOutboxRowsAndAuditInOneGo()     // 201; 1 parent_message; outbox: 2 PUSH QUEUED + 2 SMS QUEUED, event_type DM_MESSAGE, event_id = message id, student_id null; 1 audit row CREATE parent_message
@Test void aStudentMessageReachesThatStudentsGuardiansOnly() // studentId → outbox student_id = that student; guardians of the others get nothing
@Test void sameIdTwiceSendsOnce()                           // second POST same id/body → 200, same rows count, 1 audit row (Review Focus 1)
@Test void sameIdWithOtherTextIsRefused()                   // 409 messages.id_reused
@Test void smsLeftCountsQueuedSms()                         // cap 5; first send queues 2 SMS → preview smsLeft 3; a send needing 4 SMS → 409 sms_over_cap; with sms=false it passes (Review Focus 3)
@Test void blankOrLongBodyIsRefused()                       // "   " → 400 body_required; 481 chars → 400 body_too_long; nothing written
@Test void dmNeedsExactlyOneTarget()                        // neither / both → 400 target_required
@Test void noReachableGuardianIsRefused()                   // class whose guardians have no app, sms=false → 409 no_recipient, nothing written
@Test void adminAnnouncesToTheWholeSchool()                 // POST /announcements without classId → every guardian of current-year students once, event_type ANNOUNCEMENT
@Test void classesListsTheAuthorsScope()                    // DM → [class A] with students 3; ADMIN → all classes of the year
// permission matrices, one per endpoint (preview, send, classes) × role (ADMIN, DM, ASSISTANT_DM, TEACHER, COUNSELLOR, PARENT, RESEARCHER):
@Test void messageEndpointsAreForTheDmOnly()                // DM 2xx; others 403
@Test void announcementEndpointsAreForTheAdminOnly()        // ADMIN 2xx; others 403
@Test void outOfScopeTargetsAreNotFound()                   // DM → class B or a student of class B: 404 on preview and send, nothing written; ADMIN → a class of the other school: 404 (Review Focus 5)
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd backend && ./mvnw -q test "-Dtest=SmsTextTests,ParentMessageTests"`
Expected: compilation fails, because `SmsText` and `ParentMessages` do not exist yet.

- [ ] **Step 3: Implement**

- The migration as in the Interfaces block.
- `SmsText`.
- `SmsUsage.left`.
- `Students.idsInSchool`.
- `ParentMessages`:
  - Resolve the students: the student, or `idsInClass`, or `idsInSchool`.
  - Call `guardians.ofStudents` and group by `guardianId`, so each guardian gets one row per channel.
  - The channel follows `hasAccount`. For a STUDENT audience the outbox `student_id` is the student; otherwise it is null.
  - The outbox payload is `{"body", "plainSms", "school", "firstName" (STUDENT only), "className" (CLASS only)}`.
  - Status is `QUEUED`.
  - The audit row carries kind, audience, target, sms and the recipient count.
- The idempotency check comes first: look up the existing row by `id` and compare kind, target, body and sms.
- The cap check uses `left` after the scope checks.
- The FR/EN errors.

- [ ] **Step 4: Run them to see them pass, then the whole backend suite**

Run: `cd backend && ./mvnw -q test "-Dtest=SmsTextTests,ParentMessageTests"`, then `./mvnw verify`
Expected: all pass, and no other test fails.

- [ ] **Step 5: Commit**

```bash
git add backend/src
git commit -m "UC-D4, UC-A5, D38: a DM or an administrator previews and sends a message to parents through the outbox"
```

### Task 2: Backend — texts, delivery and the sent lists

**Files:**
- Modify: `notification/internal/Templates.java` (push and SMS for `DM_MESSAGE` and `ANNOUNCEMENT`)
- Modify: `ParentMessages.java` and `ParentMessageController.java` (the sent lists)
- Modify: `messages_fr.properties` and `messages_en.properties` (the templates)
- Modify: `docs/SPEC.md` (UC-D4, UC-A5 and UC-P4 move to the pilot; data model row `ParentMessage`; API rows) and `docs/PLAN.md` (D38 row, three columns)
- Test: `TemplatesTests.java`, `OutboxDispatcherTests.java`, the inbox tests (whichever class tests `GET /me/notifications`), `ParentMessageTests.java`

**Interfaces:**
- Consumes: Task 1's payload `{body, plainSms, school, firstName?, className?}`, and `SmsText.plain`.
- Produces:
  - Push:
    - DM message title: FR `Message du surveillant général`, EN `Message from the discipline master`.
    - Announcement title: FR `Annonce : {school}`, EN `Announcement: {school}`.
    - The body is the full `body`, never transliterated.
  - SMS: the Global Constraints templates. With `plainSms`, `SmsText.plain` applies to the whole SMS.
  - `record Delivery(int recipients, int pending, int sent, int delivered, int opened, int failed)`, counted over the message's outbox rows:
    - `recipients` = distinct guardians;
    - `pending` = QUEUED or SENDING;
    - `sent` = SENT;
    - `delivered` = DELIVERED;
    - `opened` = OPENED;
    - `failed` = FAILED.
  - `record SentMessage(UUID id, String kind, String audience, UUID studentId, String studentName, UUID classId, String className, String body, boolean sms, String authorName, Instant createdAt, Delivery delivery)`. There is no phone number and no guardian name (NFR-9).
  - `GET /api/v1/messages`: a DM gets their own messages, newest first, `limit` 50 (max 100).
  - `GET /api/v1/announcements`: an ADMIN gets the school's announcements.
  - Both are 403 for other roles.

- [ ] **Step 1: Write the failing tests**

```java
// TemplatesTests
@Test void dmMessagePushKeepsTheFullText()        // FR title "Message du surveillant général", body == payload body (accents kept)
@Test void announcementSmsUsesTheSchoolTemplate() // FR "SchoolLink - Lycée Démo : Réunion vendredi" ; EN "SchoolLink - Demo School: …"
@Test void plainSmsRemovesAccentsInTheSmsOnly()   // plainSms true → "SchoolLink - SG Lycee Demo : Reunion a l'ecole"; push body unchanged (Review Focus 4)
// OutboxDispatcherTests
@Test void sendsAQueuedAnnouncementSmsAndPush()   // both rows go SENT through the fake providers
@Test void atTheCapAMessageSmsIsRejected()        // BR-25: DM_MESSAGE SMS FAILED "monthly SMS cap reached"
// inbox tests
@Test void theInboxShowsADmMessageWithItsFullText() // GET /me/notifications → eventType DM_MESSAGE, title "Message du surveillant général", body = full text
// ParentMessageTests
@Test void theDmSeesTheirSentMessagesWithDelivery() // after send: GET /messages → 1 item, delivery {recipients 4, pending 4, …}; mark one push OPENED in SQL → opened 1
@Test void aDmDoesNotSeeAnotherDmsMessages()
@Test void sentListsAreForTheirAuthorRoleOnly()     // matrices for GET /messages (DM) and GET /announcements (ADMIN)
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd backend && ./mvnw -q test "-Dtest=TemplatesTests,OutboxDispatcherTests,ParentMessageTests"` (and the inbox test class)
Expected: they fail with "No push template for DM_MESSAGE" and the missing endpoints.

- [ ] **Step 3: Implement**

Add the `Templates` cases. In the push for a STUDENT audience, the title stays the role and the body is the text. The child's name is given by the inbox's `studentId`.

Then the two GET endpoints, the FR/EN templates, and the SPEC and PLAN rows.

- [ ] **Step 4: Run `./mvnw verify`**

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src docs/SPEC.md docs/PLAN.md
git commit -m "UC-D4, UC-A5, UC-P4, D38: messages reach parents by push and SMS and their authors see the delivery"
```

### Task 3: Android — the DM writes to parents

**Files:**
- Create: `mobile/lib/features/dm/messages.dart` (models and providers)
- Create: `mobile/lib/features/dm/messages_screen.dart` (the sent list)
- Create: `mobile/lib/features/dm/message_composer_screen.dart`
- Modify: `dm.dart` (repository methods), `dm_home_screen.dart` (the entry card), `student_history_screen.dart` (the AppBar action), the ARB files FR/EN followed by `flutter gen-l10n`
- Test: `mobile/test/features/dm_messages_test.dart`

**Interfaces:**
- Consumes: Task 1's and Task 2's endpoints and JSON fields as named above.
- Produces (Dart):
  - Records:
    - `class MessagePreview { recipients, byApp, bySms, unreached, smsUnits, smsUnicode, smsLeft, estimatedCost, overCap, smsSample }`
    - `class SentMessage { id, audience, studentName?, className?, body, sms, createdAt, delivery }`
    - `class Delivery { recipients, pending, sent, delivered, opened, failed }`
    - `class ClassOption { classId, className, students }`
  - Repository methods: `previewMessage(MessageDraft)`, `sendMessage(MessageDraft)`, `sentMessages()`, `messageClasses()`. `MessageDraft` holds `id` (a UUID made once per composer), `studentId?`, `classId?`, `body`, `sms` and `plainSms`.
  - `MessageComposerScreen({String? studentId, String? studentName})`

- [ ] **Step 1: Write the failing tests** (FakeServer, `pumpApp` and `userJson(roles: ['DM'])` as in `dm_discipline_test.dart`)

```dart
testWidgets('the DM home leads to Messages, which lists sent messages with delivery') // card key 'dm-messages-entry'; item shows "6e A", text, "4 destinataires", "1 ouvert"
testWidgets('an empty Messages screen says what to do')                               // "Aucun message envoyé." + FAB "Nouveau message"
testWidgets('a class message: SMS off by default, live counts, sends once')           // pick class, type text, pump 400 ms → "4 parents : 2 par l'app, 2 sans l'app" ; SMS switch off; send → confirm dialog repeats counts → POST /messages body {id, classId, body, sms:false, plainSms:false}; snackbar "Message envoyé."
testWidgets('a student message from the student page has SMS on by default')          // AppBar key 'history-message' → composer titled with the student; POST body has studentId, sms:true
testWidgets('the SMS line shows cost and SMS left, and over the cap blocks sending')  // preview overCap:true → "Plus assez de SMS ce mois-ci" shown, send disabled
testWidgets('special letters: the notice and the replace switch')                    // preview smsUnicode:true → notice shown; switch → next preview request has plainSms:true
testWidgets('send stays disabled for a blank text')
testWidgets('a second tap during sending sends once')                                 // Review Focus 1 on the client: one POST
testWidgets('reads in English (NFR-12)')
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd mobile && flutter test test/features/dm_messages_test.dart`
Expected: fails to compile.

- [ ] **Step 3: Implement**

- The full-screen composer (`Scaffold` with a close button):
  - The audience comes first: the student shown as a chip, or a class dropdown from `messageClasses()`.
  - The text field has `maxLength` 480.
  - The SMS switch reads "Aussi par SMS pour les parents sans l'application".
  - A preview card is refreshed 400 ms after the last change:
    - the counts line;
    - for SMS, "≈ X FCFA · Y SMS restants ce mois-ci";
    - the units line;
    - the unicode notice with its "Remplacer les lettres spéciales" switch;
    - the sample SMS.
  - "Envoyer" is a FilledButton in the bottom bar. It opens a confirm dialog that repeats the counts and the cost.
- The sent list:
  - `RefreshIndicator`, skeletons and `EmptyState`, as in `discipline_tab.dart`.
  - Each card shows the audience, the date, the text on 3 lines, and delivery chips.
- The home card goes under the full-day report entry.
- All strings come from the ARB files.

- [ ] **Step 4: Run `flutter test` and `flutter analyze`**

Expected: all pass, and no issues.

- [ ] **Step 5: Check on the emulator** with the dev seed (FR DM `sg1@lycee-demo-a.test`): send one class message without SMS and one student message, and see both in the list. Then commit:

```bash
git add mobile/lib mobile/test
git commit -m "UC-D4, D38: the DM writes to the parents of a student or a class on Android"
```

### Task 4: Android — parents read messages and announcements

**Files:**
- Modify: `mobile/lib/features/parent/inbox_screen.dart` (tapping a `dmMessage` or `announcement` entry opens a sheet with the full text, the date and the child's first name when `studentId` is set; it marks the entry opened, as tapping does today), the ARB files
- Test: `mobile/test/features/parent_inbox_messages_test.dart`

**Interfaces:**
- Consumes: the inbox entries (`eventType` `DM_MESSAGE` or `ANNOUNCEMENT`, `title`, `body`, `studentId`) from Task 2.

- [ ] **Step 1: Write the failing tests**

```dart
testWidgets('a long DM message shows two lines in the list and the full text when opened') // 400-char body; sheet shows whole body; POST /me/notifications/{id}/opened sent once
testWidgets('an announcement shows its title and no child')
testWidgets('a student message names the child')                                         // studentId of a child → "Concerne Awa"
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd mobile && flutter test test/features/parent_inbox_messages_test.dart`
Expected: FAIL (no sheet).

- [ ] **Step 3: Implement the sheet** (reuse `showActionSheet`-style framing; the text is selectable).

- [ ] **Step 4: Run `flutter test` and `flutter analyze`**

Expected: all pass, and no issues.

- [ ] **Step 5: Commit**

```bash
git add mobile/lib mobile/test
git commit -m "UC-P4, D38: parents read the full text of messages and announcements"
```

### Task 5: Web — the DM's Messages

**Files:**
- Create: `web/src/api/messages.ts` (types matching Task 1/2 and `MESSAGE_PATHS`)
- Create: `web/src/pages/messages/MessageComposer.tsx`: the shared two-column composer. The left column is the form; the right column is the live preview, with a push card and an SMS bubble showing `smsSample` and its units. The count and cost line sits above the send button, and a `ConfirmDialog` repeats them.
- Create: `web/src/pages/messages/SentMessagesTable.tsx`: shared, with a sticky header and an empty state. The columns are date, to, text, SMS, and delivery pills.
- Create: `web/src/pages/messages/MessagesPage.tsx` (`/messages`) and `ComposeMessagePage.tsx` (`/messages/new`, which reads `?studentId=` for a student message)
- Modify:
  - `routes.tsx`;
  - `components/AppLayout.tsx` (DM nav "Messages", after Students);
  - `icons.ts` (`ChatTextIcon`, `MegaphoneIcon`);
  - `pages/dm/StudentHistoryPage.tsx` (header action "Écrire aux parents" linking to `/messages/new?studentId=…`);
  - `i18n/fr.json` and `en.json` (a `messages` section).
- Test: `web/src/pages/messages/messages.test.tsx`

**Interfaces:**
- Produces (TS):
  - `interface MessageDraft { id: string; studentId?: string; classId?: string; body: string; sms: boolean; plainSms: boolean }`
  - `interface MessagePreview`, `interface SentMessage`, `interface Delivery`, `interface ClassOption` (the fields of Tasks 1 and 2)
  - `MessageComposer({ kind: 'DM_MESSAGE' | 'ANNOUNCEMENT', student?: { id: string; name: string }, onSent: () => void })`
  - `SentMessagesTable({ items })`
- The preview is fetched 400 ms after the last change. The id is made with `crypto.randomUUID()` once per composer.

- [ ] **Step 1: Write the failing tests** (`fakeFetch`, `renderApp` and `tokens` as in `dm.test.tsx`; use fake timers or `waitFor` for the 400 ms)

```ts
it('lists sent messages with delivery and an empty state')
it('sends a class message: SMS off by default, counts and cost, confirm, then back to the list')   // POST body {id, classId, body, sms:false, plainSms:false}; success alert "Message envoyé."
it('opens from the student page with that student and SMS on')                                     // /students/s1 → link "Écrire aux parents" → /messages/new?studentId=s1; POST body studentId
it('blocks sending over the SMS left and says why')                                                // preview overCap → alert text, button disabled
it('offers to replace special letters when the SMS is unicode')                                   // switch → next preview body plainSms true
it('keeps Send disabled for a blank text and sends once on a double click')
it('reads in English (NFR-12)')
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd web && npm test -- messages`
Expected: they fail (the modules are missing).

- [ ] **Step 3: Implement** as in Files.

- [ ] **Step 4: Run `npm test`, `npm run lint`, `npm run build`**

Expected: all pass.

- [ ] **Step 5: Check in a browser** with `API_PROXY=<test backend> npx vite --port 5174 --strictPort`: send a class message as `sg1`. Then commit:

```bash
git add web/src
git commit -m "UC-D4, D38: the DM writes to parents on the web"
```

### Task 6: Web — the administrator's announcements

**Files:**
- Create: `web/src/pages/messages/AnnouncementsPage.tsx` (`/announcements`) and `ComposeAnnouncementPage.tsx` (`/announcements/new`), reusing Task 5's `MessageComposer` and `SentMessagesTable` with `kind: 'ANNOUNCEMENT'`. The audience is a radio "Toute l'école" / "Une classe" plus a class select.
- Modify: `routes.tsx` (behind `admin(...)`), `AppLayout.tsx` (admin nav "Annonces"), `i18n/fr.json` and `en.json`
- Test: `web/src/pages/messages/announcements.test.tsx`

**Interfaces:**
- Consumes: `MessageComposer` and `SentMessagesTable` from Task 5; `POST /announcements/preview`, `POST /announcements`, `GET /announcements`, `GET /messages/classes`.

- [ ] **Step 1: Write the failing tests**

```ts
it('an announcement to the whole school shows the parent count and SMS cost before sending') // default audience school, SMS off; tick SMS → "≈ … FCFA"; confirm dialog repeats counts; POST body has no classId
it('an announcement to one class sends its classId')
it('lists sent announcements with delivery')
it('is reachable from the admin menu only')                                                  // DM user: no "Annonces" link, /announcements redirects away
it('reads in English (NFR-12)')
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd web && npm test -- announcements`
Expected: FAIL.

- [ ] **Step 3: Implement** as in Files.

- [ ] **Step 4: Run `npm test`, `npm run lint`, `npm run build`**

Expected: all pass.

- [ ] **Step 5: Check in a browser** as the dev-seed admin (one announcement to a class, SMS off). Then commit:

```bash
git add web/src
git commit -m "UC-A5, D38: the administrator sends announcements to the school or a class on the web"
```
