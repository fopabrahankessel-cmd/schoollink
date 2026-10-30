# Phase 2 exit check (task 2.20)

This is a hand-run script for the Phase 2 exit criterion in SPEC §12: **a teacher's confirmation reaches a parent's phone**. It also checks NFR-5 (95% of pushes sent within 1 minute of sync) from the outbox timestamps.

It takes about an hour the first time, most of it the Firebase setup, and about 20 minutes after that.

## Pass criteria

| # | Check | How it is measured |
| --- | --- | --- |
| P1 | Online: the parent's phone shows the absence push after the teacher confirms | Seen on the phone |
| P2 | Online: the outbox row is `SENT` (or `OPENED`) within 60 s of the session's `synced_at` | Query Q3 |
| P3 | Teacher offline: the roll is saved on the device, sent when the network returns, then the push arrives | Seen on both devices; Q3 |
| P4 | In the offline run, the 60 s are counted from `synced_at` (when the server got the roll), not `confirmed_at` (when the teacher confirmed) | Q3: `confirmed_at` is earlier than `synced_at`, and `sent_at - synced_at` is under 60 s |
| P5 | Tapping the push opens the message, and the server records `opened_at` (BR-24) | Q3 |
| P6 | The push text is in the guardian's language (NFR-12) and contains no phone number (NFR-9) | Seen on the phone |

The run passes when P1 to P6 all hold. NFR-5 allows 5% of pushes to be late; this run sends only a few, so any push over 60 s is noted and investigated.

## What you need

- A **weekday** (Monday to Friday, D3) that is not a holiday. Sessions can only be recorded for school days.
- **Two Android devices**:
  - **Parent device**: a real phone with Google Play services, connected by USB. Push needs Play services.
  - **Teacher device**: the Android emulator (it reaches the PC at `10.0.2.2`), or a second phone on USB.
- Docker Desktop running, the repo's `.env` in place, and Node for the web admin.
- A Google account, to create the Firebase project.

Each run records a **different session**: a confirmed session is locked (BR-4) and cannot be recorded again. The teacher you pick below has several periods each day, so use one period per run.

## A. One-time setup: the Firebase project

1. Go to <https://console.firebase.google.com> and choose **Add project**. Name it, for example, `schoollink-pilot`. Google Analytics is not needed.
2. In the project, choose **Add app → Android**:
   - Package name: **`cm.schoollink.app`** (it must match `applicationId`).
   - Download `google-services.json`. **Do not put it in the repository.** Keep it outside the repo, for example in `C:\Users\fopab\secrets\`.
3. Open `google-services.json` and copy four values into a new file, `C:\Users\fopab\secrets\schoollink-phone.json`:

   ```json
   {
     "API_BASE_URL": "http://127.0.0.1:8080/api/v1",
     "FIREBASE_API_KEY": "<client[0].api_key[0].current_key>",
     "FIREBASE_APP_ID": "<client[0].client_info.mobilesdk_app_id>",
     "FIREBASE_SENDER_ID": "<project_info.project_number>",
     "FIREBASE_PROJECT_ID": "<project_info.project_id>"
   }
   ```

4. Go to **Project settings → Service accounts → Generate new private key**. Save the file as `C:\Users\fopab\secrets\schoollink-firebase-admin.json`. This key lets the server send pushes: **never commit it or share it**.
5. Go to **Project settings → Cloud Messaging** and check that **Firebase Cloud Messaging API (V1)** is enabled. It is by default on new projects.

## B. Start the server with real push

In PowerShell, from the repo root:

```powershell
docker compose up -d db
cd backend
$env:FIREBASE_CREDENTIALS = "C:\Users\fopab\secrets\schoollink-firebase-admin.json"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Check the startup log:

- The line `No push provider configured: push notifications are logged, not delivered` must **not** appear. If it does, `FIREBASE_CREDENTIALS` was not set in this window.
- `Dev seed: password for every account below is 'schoollink-dev'` lists the demo accounts.

Leave this window open: the SMS codes in step D appear in it.

To run SQL, open a second PowerShell window at the repo root:

```powershell
docker compose exec db psql -U schoollink -d schoollink
```

## C. Choose the teacher, the class and the parent

The teacher is `prof1@lycee-demo-a.test` (password `schoollink-dev`), in Lycée Démo A (fictif). Query Q1 lists today's lessons for that teacher, with each student's guardians:

```sql
-- Q1: prof1's lessons today, the students and their guardians
SELECT p.number AS period, p.start_time, c.name AS class,
       s.first_name || ' ' || s.last_name AS student,
       g.id AS guardian_id, g.name AS guardian, g.phone, g.language,
       g.user_id IS NOT NULL AS has_account
FROM timetable_slot t
JOIN app_user u ON u.id = t.teacher_id AND u.school_id = t.school_id
JOIN school_class c ON c.id = t.class_id
JOIN period p ON p.id = t.period_id
JOIN student s ON s.class_id = c.id
JOIN student_guardian sg ON sg.student_id = s.id
JOIN guardian g ON g.id = sg.guardian_id
WHERE u.email = 'prof1@lycee-demo-a.test'
  AND t.weekday = extract(isodow FROM current_date)
ORDER BY p.number, c.name, s.last_name
LIMIT 20;
```

From the result, pick:

- **Two periods**: one for run 1 and one for run 2. They can be in the same class.
- **One student** in that class, and **one guardian** with `has_account = false`. The guardian has a made-up `+23760…` number from the dev seed. Note the student's name, the guardian's phone and the guardian's language.

If Q1 returns no rows, prof1 has no lesson today. Replace `prof1` with `prof2` … `prof8` until one does.

## D. Prepare the parent device (activation, UC-P1)

1. Start the web admin, sign in, and invite the guardian:

   ```powershell
   cd web
   npm run dev
   ```

   Open <http://localhost:5173> and sign in as `admin@lycee-demo-a.test` / `schoollink-dev`. Go to **Tuteurs et invitations**, open the class, and choose **Inviter** on the guardian's row.

2. In the server window, find the line `SMS (not delivered, dev): notification …` and note the **invitation code** in it.

3. Connect the parent phone by USB, then run the app on it with the Firebase values:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <phone-serial> reverse tcp:8080 tcp:8080
   cd mobile
   flutter run -d <phone-serial> --dart-define-from-file=C:\Users\fopab\secrets\schoollink-phone.json
   ```

4. On the phone:
   1. Tap **Activer avec mon code d'invitation**.
   2. Enter the code and the guardian's phone number from Q1.
   3. Tap **Recevoir un code par SMS**.
   4. Copy the 6-digit code from the new `SMS (not delivered, dev)` line in the server window.
   5. Tap **Activer mon compte**. The app opens on **Enfants** with the student.
   6. Allow notifications when Android asks (Android 13 and later).

5. Check that the server has the phone's push token (Q2). Don't show the token: it identifies the device.

   ```sql
   -- Q2: the parent's registered devices
   SELECT u.first_name, u.last_name, d.platform, d.created_at, d.last_seen
   FROM device_token d JOIN app_user u ON u.id = d.user_id
   JOIN guardian g ON g.user_id = u.id
   WHERE g.id = '<guardian_id from Q1>';
   ```

   One `ANDROID` row is expected. If there is none, see Troubleshooting.

6. Press the phone's **Home** button so the app goes to the background. Leave the phone unlocked and connected.

## E. Prepare the teacher device

1. Start the emulator, then run the app on it without Firebase (the teacher does not need push):

   ```powershell
   cd mobile
   flutter run -d emulator-5554
   ```

   With a second real phone instead, run `adb -s <serial> reverse tcp:8080 tcp:8080` for that phone and add `--dart-define=API_BASE_URL=http://127.0.0.1:8080/api/v1`.

2. Sign in as `prof1@lycee-demo-a.test` / `schoollink-dev`. The list shows today's sessions.

## Run 1: online (P1, P2, P5, P6)

1. Note the time: **T0 = ______**.
2. On the teacher device, open the run 1 session and tap **Faire l'appel**.
3. Tap the chosen student to mark them absent, then tap **Enregistrer**.
4. Confirm twice: **Confirmer**, then **Confirmer** again at "Confirmer et prévenir les parents ?". The session shows as locked and then **Envoyée à l'école**.
5. On the parent phone, a notification should appear within a few seconds:
   - The title and text are about the student's absence at that period's time.
   - The text is in the guardian's language (Q1 `language`).
   - The text contains no phone number.
6. Note when it appeared: **T1 = ______**.
7. Tap the notification. The app opens the message itself (title "Absence") with **Voir les absences et retards**.
8. Run Q3 (in "Measure") and fill in run 1 on the record sheet.

## Run 2: teacher offline, airplane mode (P3, P4)

1. On the teacher device, turn on **airplane mode**. On the emulator: swipe down the quick settings, or open **Settings → Network & internet → Airplane mode**.
2. Open the run 2 session, mark the same student absent, **Enregistrer**, and confirm twice.
3. Check the teacher device: the session is locked, marked **Enregistrée sur ce téléphone, en attente d'envoi.**, and the bottom banner says that 1 record will be sent when the network returns.
4. Wait **at least 2 minutes**, so the offline gap is visible in the timestamps. On the parent phone, nothing arrives.
5. Turn airplane mode **off**. Within a few seconds the banner disappears and the session shows **Envoyée à l'école**. Tap **Envoyer** on the banner if you don't want to wait for the next retry.
6. On the parent phone, the push for run 2 arrives. Note the time: **T2 = ______**.
7. Leave this push unopened for now, so `opened_at` stays empty for run 2.
8. Run Q3 and fill in run 2.

## Run 3 (optional): parent phone offline

This checks that a push the server sent while the parent had no network still arrives later. FCM keeps it.

1. Put the **parent phone** in airplane mode. The USB link stays up, but the app cannot reach the server or FCM.
2. Record a third session with the student absent on the teacher device, which is online.
3. Q3 shows run 3 `SENT` within 60 s of `synced_at`: the server sent it, and FCM holds it.
4. Turn the parent phone's airplane mode off. The push arrives within about a minute.

## Measure

```sql
-- Q3: each absence push of today, measured from the sync (NFR-5) and the open (BR-24)
SELECT s.confirmed_at, s.synced_at, o.channel, o.status, o.attempts,
       o.created_at, o.sent_at,
       round(extract(epoch FROM o.sent_at - s.synced_at)::numeric, 1) AS seconds_after_sync,
       round(extract(epoch FROM s.synced_at - s.confirmed_at)::numeric, 0) AS seconds_offline,
       o.opened_at, o.last_error
FROM notification_outbox o
JOIN attendance_record r ON r.id = o.event_id AND o.event_type = 'ABSENCE'
JOIN attendance_session s ON s.id = r.session_id
WHERE o.guardian_id = '<guardian_id from Q1>' AND s.date = current_date
ORDER BY s.synced_at;
```

Expected:

- `channel = PUSH`, `status = SENT`, or `OPENED` once tapped.
- `seconds_after_sync` is under 60. It is usually under 10 (the dispatcher polls every 5 s).
- `seconds_offline` is a few seconds in run 1, and 120 or more in run 2.
- `opened_at` is set for run 1 only.

The NFR-5 figure over all pushes of the day:

```sql
-- Q4: share of today's pushes sent within 1 minute of sync (NFR-5: 95% or more)
SELECT count(*) AS pushes,
       count(*) FILTER (WHERE o.sent_at - s.synced_at <= interval '1 minute') AS within_1_min,
       round(100.0 * count(*) FILTER (WHERE o.sent_at - s.synced_at <= interval '1 minute') / count(*), 1) AS pct
FROM notification_outbox o
JOIN attendance_record r ON r.id = o.event_id AND o.event_type = 'ABSENCE'
JOIN attendance_session s ON s.id = r.session_id
WHERE o.channel = 'PUSH' AND s.date = current_date;
```

## Record sheet

Date: ________  Tester: ________  App build: `git rev-parse --short HEAD` = ________

| Run | Session (class, period) | confirmed_at | synced_at | sent_at | s after sync | Push seen at | opened_at | Pass? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 online | | | | | | | | |
| 2 teacher offline | | | | | | | — | |
| 3 parent offline (optional) | | | | | | | — | |

Q4 result: ____ of ____ within 1 min (____ %)

| Criterion | P1 | P2 | P3 | P4 | P5 | P6 |
| --- | --- | --- | --- | --- | --- | --- |
| Result | | | | | | |

Notes (language seen, anything unexpected):

## Troubleshooting

| What you see | Likely cause and fix |
| --- | --- |
| Server log says `push notifications are logged, not delivered` | `FIREBASE_CREDENTIALS` is not set in the server's window. Set it and restart. |
| Q2 has no row | The app ran without the Firebase values: check `--dart-define-from-file`. Also check that the phone has Google Play services and that notifications are allowed. Sign out and in again to register. |
| Q3 has no row for the run | The student was not marked absent, or the guardian is not one of that student's guardians. D2: no absence, no notification. |
| Q3: `channel = SMS`, `status = HELD` | The guardian had no app account when the session synced. Finish step D first, then record a new session. |
| Q3: `status = FAILED`, `last_error = no device registered` | The parent's account has no token: see Q2. |
| Q3: `status = FAILED` or `QUEUED` with `last_error` `FCM …` | Wrong or revoked service-account key, or the FCM V1 API is off. Fix it and record a new session. |
| `SENT` in Q3, but nothing on the phone | Notifications are blocked for the app, or the phone is in battery saver or Do Not Disturb. Check **Settings → Apps → SchoolLink → Notifications**. |
| Teacher device says "no connection" | Emulator: use the default `10.0.2.2`. Phone: redo `adb reverse` after each reconnect. |
| Session "Refusée par l'école" | That period of that class was already recorded today (BR-4). Use another period. |

## After the run

- To rerun from scratch, reset the dev database with `docker compose down -v`, then `docker compose up -d db`. The dev seed runs again at the next server start.
- Keep the two files in `C:\Users\fopab\secrets\` out of the repository. If the service-account key may have leaked, delete it in **Project settings → Service accounts** and generate a new one.
- Record the result in `docs/PLAN.md` (task 2.20), or report it, then move on to Phase 3.
