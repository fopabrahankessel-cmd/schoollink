import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/cache/response_cache.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/teacher/attendance_screen.dart';
import 'package:schoollink/features/teacher/sessions_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-T1: all present by default (BR-2), tap the absentees, confirm twice (BR-3), then locked
/// (BR-4); a session with no absentee is saved too (BR-5); offline it waits on the phone (UC-T2).
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  late AppDatabase db;
  final now = DateTime(2026, 11, 2, 8, 40); // Monday, period 2 under way

  setUp(() => db = AppDatabase(NativeDatabase.memory()));

  final monday = {
    'date': '2026-11-02',
    'schoolDay': true,
    'sessions': [
      {
        'classId': 'class-6A',
        'className': '6e A',
        'periodId': 'period-1',
        'periodNumber': 1,
        'startTime': '07:30:00',
        'endTime': '08:25:00',
        'subject': 'Mathématiques',
        'current': false,
        'recorded': true,
        'sessionId': 'server-session-1',
        'confirmedAt': '2026-11-02T06:35:00Z',
        'absentCount': 2,
      },
      {
        'classId': 'class-5B',
        'className': '5e B',
        'periodId': 'period-2',
        'periodNumber': 2,
        'startTime': '08:25:00',
        'endTime': '09:20:00',
        'subject': 'Français',
        'current': true,
        'recorded': false,
        'sessionId': null,
        'confirmedAt': null,
        'absentCount': null,
      },
    ],
  };

  Map<String, dynamic> student(String id, String first, String last) => {
    'id': id,
    'matricule': 'MAT-$id',
    'firstName': first,
    'lastName': last,
  };

  final classes = [
    {
      'classId': 'class-5B',
      'className': '5e B',
      'students': [
        student('st-1', 'Aline', 'Abanda'),
        student('st-2', 'Brice', 'Bello'),
        student('st-3', 'Chantal', 'Fictif'),
      ],
    },
    {'classId': 'class-6A', 'className': '6e A', 'students': <Object>[]},
  ];

  final teacher = userJson(roles: ['TEACHER']);

  Future<FakeServer> openRoll(WidgetTester tester, {String language = 'fr', Reply? saveReply}) async {
    final user = userJson(roles: ['TEACHER'], language: language.toUpperCase());
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('GET /me/sessions', (_) => Reply.ok(monday))
      ..on('GET /me/classes', (_) => Reply.ok(classes));
    server.handlers['PUT /sessions/*'] = (_) => saveReply ?? const Reply(201, {});
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user)), database: db, clock: () => now);
    await tester.tap(find.text('5e B'));
    await tester.pumpAndSettle();
    expect(find.byType(AttendanceScreen), findsOneWidget);
    return server;
  }

  Iterable<Sent> saved(FakeServer server) => server.requests.where((r) => r.method == 'PUT');

  Future<List<SyncItem>> queued() => db.select(db.syncItems).get();

  Future<void> tapStudent(WidgetTester tester, String name) async {
    await tester.tap(find.text(name));
    await tester.pump();
  }

  Future<void> save(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer'));
    await tester.pumpAndSettle();
  }

  Future<void> answer(WidgetTester tester, String button) async {
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text(button)));
    await tester.pumpAndSettle();
  }

  /// The app bar's back arrow (pageBack looks for the English tooltip only).
  Future<void> goBack(WidgetTester tester) => tester.tap(find.byType(BackButton));

  /// Lets the sync queue's database work run outside the fake clock.
  Future<void> settle(WidgetTester tester) async {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pumpAndSettle();
  }

  testWidgets('every student starts present; a tap marks absent, another tap undoes it (BR-2)', (tester) async {
    await openRoll(tester);

    expect(find.textContaining('Présents : 3'), findsOneWidget);
    expect(find.textContaining('Absents : 0'), findsOneWidget);

    await tapStudent(tester, 'Bello Brice');
    expect(find.textContaining('Présents : 2'), findsOneWidget);
    expect(find.textContaining('Absents : 1'), findsOneWidget);
    expect(
      find.descendant(of: find.byKey(const ValueKey('student-st-2')), matching: find.text('Absent')),
      findsOneWidget,
    );

    await tapStudent(tester, 'Bello Brice');
    expect(find.textContaining('Absents : 0'), findsOneWidget);
    expect(find.text('Absent'), findsNothing);
  });

  testWidgets('"Back" at the first confirmation returns to the roll with the marks kept (UC-T1 step 7)', (
    tester,
  ) async {
    await openRoll(tester);
    await tapStudent(tester, 'Bello Brice');
    await tapStudent(tester, 'Fictif Chantal');

    await save(tester);
    expect(find.text('Ces 2 élèves sont marqués absents. Est-ce correct ?'), findsOneWidget);
    expect(find.descendant(of: find.byType(AlertDialog), matching: find.text('Bello Brice')), findsOneWidget);
    expect(find.descendant(of: find.byType(AlertDialog), matching: find.text('Fictif Chantal')), findsOneWidget);
    await answer(tester, 'Retour');

    expect(find.byType(AlertDialog), findsNothing);
    expect(find.textContaining('Absents : 2'), findsOneWidget);
    expect(await tester.runAsync(queued), isEmpty);
  });

  testWidgets('"Back" at the second confirmation returns to the roll with the marks kept (UC-T1 step 7)', (
    tester,
  ) async {
    await openRoll(tester);
    await tapStudent(tester, 'Bello Brice');

    await save(tester);
    await answer(tester, 'Oui');
    expect(find.text('Confirmer et prévenir les parents ?'), findsOneWidget);
    await answer(tester, 'Retour');

    expect(find.byType(AlertDialog), findsNothing);
    expect(find.textContaining('Absents : 1'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Enregistrer'), findsOneWidget);
    expect(await tester.runAsync(queued), isEmpty);
  });

  testWidgets('confirming twice locks the session, keeps it on the phone and sends it (BR-3, BR-4, BR-6)', (
    tester,
  ) async {
    final server = await openRoll(tester);
    await tapStudent(tester, 'Bello Brice');

    await save(tester);
    await answer(tester, 'Oui');
    await answer(tester, 'Confirmer');
    await settle(tester);

    final put = saved(server).single;
    final id = put.path.split('/').last;
    expect(id, matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
    expect(put.body, {
      'classId': 'class-5B',
      'date': '2026-11-02',
      'periodId': 'period-2',
      'confirmedAt': now.toUtc().toIso8601String(),
      'absentStudentIds': ['st-2'],
    });
    expect(find.text('Verrouillée : enregistrée à 08:40.'), findsOneWidget);
    expect(find.text('Envoyée à l\'école.'), findsOneWidget);
    expect(find.text('Bello Brice'), findsOneWidget);
    expect(find.text('Abanda Aline'), findsNothing, reason: 'only the absentees are listed');
    expect(find.widgetWithText(FilledButton, 'Enregistrer'), findsNothing, reason: 'nothing can change (BR-4)');

    await goBack(tester);
    await tester.pumpAndSettle();
    expect(find.byType(SessionsScreen), findsOneWidget);
    expect(find.text('Enregistré, 1 absent'), findsOneWidget);
    expect(find.text('À faire'), findsNothing);
  });

  testWidgets('a session with no absentee is saved too (BR-5)', (tester) async {
    final server = await openRoll(tester);

    await save(tester);
    expect(find.text("Aucun élève n'est marqué absent. Est-ce correct ?"), findsOneWidget);
    await answer(tester, 'Oui');
    await answer(tester, 'Confirmer');
    await settle(tester);

    expect((saved(server).single.body! as Map)['absentStudentIds'], isEmpty);
    expect(find.text('Aucun élève absent.'), findsOneWidget);
  });

  testWidgets('a session recorded earlier opens locked, with nothing to change (BR-4)', (tester) async {
    await openRoll(tester);
    await goBack(tester);
    await tester.pumpAndSettle();

    await tester.tap(find.text('6e A'));
    await tester.pumpAndSettle();

    expect(find.textContaining('Verrouillée : enregistrée à'), findsOneWidget);
    expect(find.text('2 élèves absents.'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Enregistrer'), findsNothing);
  });

  testWidgets('with no network the session is saved and waits on the phone (UC-T2, NFR-3)', (tester) async {
    final cache = ResponseCache(db, now: () => DateTime(2026, 11, 2, 7, 0));
    await cache.put('user-1', 'sessions:2026-11-02', monday);
    await cache.put('user-1', 'classes', classes);
    final server = FakeServer()..offline = true;
    final app = await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(teacher)),
      database: db,
      clock: () => now,
    );
    await tester.tap(find.text('5e B'));
    await tester.pumpAndSettle();

    await tapStudent(tester, 'Abanda Aline');
    await save(tester);
    await answer(tester, 'Oui');
    await answer(tester, 'Confirmer');
    await settle(tester);

    expect(find.text("Enregistrée sur ce téléphone, en attente d'envoi."), findsOneWidget);
    final item = (await tester.runAsync(queued))!.single;
    expect(item.state, SyncState.pending);
    await goBack(tester);
    await tester.pumpAndSettle();
    expect(find.text('Enregistré, 1 absent'), findsOneWidget);
    expect(find.text("Enregistrée sur ce téléphone, en attente d'envoi."), findsOneWidget);
    // the sync backoff timer is still waiting for the network: end the app, then its providers
    await tester.pumpWidget(const SizedBox());
    app.dispose();
    // drift ends its query streams on zero-length timers, set once the cancellations have run
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pump(const Duration(milliseconds: 1));
  });

  testWidgets('leaving with marks asks first; "Back" stays on the roll', (tester) async {
    await openRoll(tester);
    await tapStudent(tester, 'Bello Brice');

    await goBack(tester);
    await tester.pumpAndSettle();
    expect(find.text('Quitter sans enregistrer ?'), findsOneWidget);
    await answer(tester, 'Retour');
    expect(find.byType(AttendanceScreen), findsOneWidget);
    expect(find.textContaining('Absents : 1'), findsOneWidget);

    await goBack(tester);
    await tester.pumpAndSettle();
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Quitter')));
    // the pop waits on the dialog's answer: a fixed run of frames rather than pumpAndSettle
    for (var i = 0; i < 20; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.byType(SessionsScreen), findsOneWidget);
    expect(find.byType(AttendanceScreen), findsNothing);
    expect(await tester.runAsync(queued), isEmpty);
  });

  testWidgets('a session the school already has under another id is shown as not accepted (409)', (tester) async {
    await openRoll(
      tester,
      saveReply: Reply.error(409, 'attendance.session_exists', 'Cette séance a déjà été enregistrée.'),
    );

    await save(tester);
    await answer(tester, 'Oui');
    await answer(tester, 'Confirmer');
    await settle(tester);

    expect(find.text("Refusée par l'école. Cette séance a déjà été enregistrée."), findsOneWidget);
  });

  testWidgets('the confirmations read in English (NFR-12)', (tester) async {
    await openRoll(tester, language: 'en');
    await tapStudent(tester, 'Bello Brice');
    await tapStudent(tester, 'Abanda Aline');

    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pumpAndSettle();
    expect(find.text('These 2 students are marked absent. Correct?'), findsOneWidget);
    await answer(tester, 'Yes');
    expect(find.text('Confirm and notify parents?'), findsOneWidget);
    expect(find.text('Once confirmed, the session is locked and can no longer be changed.'), findsOneWidget);
  });
}
