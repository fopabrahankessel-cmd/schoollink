import 'dart:async';

import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/cache/response_cache.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/core/ui/skeleton.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// Task 2.16, UC-AD1, UC-AD2: the assistant DM searches a student at the gate, records the arrival
/// time (BR-7), and sees today's list; all of it with no network too (NFR-3).
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  late AppDatabase db;
  late DateTime now;
  late TestApp app;

  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
    now = DateTime(2026, 11, 2, 7, 52); // a Monday, classes start at 07:30
  });

  Map<String, dynamic> student(String id, String first, String last, {String className = '5e B'}) => {
    'id': id,
    'matricule': 'MAT-$id',
    'firstName': first,
    'lastName': last,
    'classId': 'class-$className',
    'className': className,
  };

  final students = [
    student('st-1', 'Aline', 'Abanda'),
    student('st-2', 'Élodie', 'Etoa', className: '6e A'),
    student('st-3', 'Chantal', 'Fictif'),
  ];

  Map<String, dynamic> today([List<Map<String, dynamic>> lateness = const []]) => {
    'date': '2026-11-02',
    'startTime': '07:30:00',
    'lateness': lateness,
  };

  final chantalLate = {
    'id': 'late-1',
    'studentId': 'st-3',
    'firstName': 'Chantal',
    'lastName': 'Fictif',
    'classId': 'class-5e B',
    'className': '5e B',
    'arrivalTime': '07:41:00',
    'recordedAt': '2026-11-02T06:41:30Z',
    'recordedBy': 'user-1',
    'justified': false,
  };

  Future<FakeServer> open(WidgetTester tester, {Reply? saveReply, bool offline = false, String language = 'fr'}) async {
    final user = userJson(roles: ['ASSISTANT_DM'], language: language.toUpperCase());
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('GET /lateness/students', (_) => Reply.ok(students))
      ..on('GET /lateness', (_) => Reply.ok(today([chantalLate])))
      ..on('PUT /lateness/*', (_) => saveReply ?? const Reply(201, {}))
      ..offline = offline;
    app = await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(user)),
      database: db,
      clock: () => now,
    );
    return server;
  }

  /// Lets the sync queue's database work run outside the fake clock.
  Future<void> settle(WidgetTester tester) async {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pumpAndSettle();
  }

  Future<void> search(WidgetTester tester, String text) async {
    await tester.enterText(find.byKey(const ValueKey('gate-search')), text);
    await tester.pumpAndSettle();
  }

  Future<void> recordFirstResult(WidgetTester tester, String name) async {
    await tester.tap(find.text(name));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer le retard'));
    await tester.pumpAndSettle();
    await settle(tester);
  }

  final etoa = find.byKey(const ValueKey('gate-student-st-2'));

  testWidgets('while the day loads, skeletons stand in and nothing claims there is no lateness', (tester) async {
    final user = userJson(roles: ['ASSISTANT_DM']);
    final day = Completer<Reply>();
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('GET /lateness/students', (_) => Reply.ok(students))
      ..on('GET /lateness', (_) => day.future);
    app = await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(user)),
      database: db,
      clock: () => now,
      settle: false,
    );

    expect(find.byType(Skeleton), findsNWidgets(2)); // the count and the list
    expect(find.text("Aucun retard aujourd'hui"), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    day.complete(Reply.ok(today([chantalLate])));
    await tester.pumpAndSettle();
    expect(find.byType(Skeleton), findsNothing);
    expect(find.text("1 retard aujourd'hui"), findsOneWidget);
  });

  testWidgets("opens on today's lateness, latest first, with the school's start time (UC-AD2)", (tester) async {
    await open(tester);

    expect(find.text('1 retard aujourd\'hui'), findsOneWidget);
    expect(find.text('Début des cours : 07:30'), findsOneWidget);
    expect(find.text('Fictif Chantal'), findsOneWidget);
    expect(find.text('07:41'), findsOneWidget);
  });

  testWidgets('finds a student by part of the name, accents and case ignored, or by matricule', (tester) async {
    await open(tester);

    await search(tester, 'elod');
    expect(etoa, findsOneWidget);
    expect(find.text('Abanda Aline'), findsNothing);

    await search(tester, 'mat-st-1');
    expect(find.text('Abanda Aline'), findsOneWidget);
    expect(etoa, findsNothing);

    await search(tester, 'zzz');
    expect(find.text('Aucun élève ne correspond à cette recherche.'), findsOneWidget);
  });

  testWidgets('a student already late today says so and cannot be recorded twice (BR-7)', (tester) async {
    await open(tester);

    await search(tester, 'fictif');
    expect(find.text('En retard à 07:41'), findsOneWidget);
    await tester.tap(find.text('Fictif Chantal'));
    await tester.pumpAndSettle();

    expect(find.text('Enregistrer le retard'), findsNothing);
  });

  testWidgets('records the arrival time, now by default, with an id made on the phone (UC-AD1)', (tester) async {
    final server = await open(tester);

    await search(tester, 'etoa');
    await tester.tap(etoa);
    await tester.pumpAndSettle();
    expect(find.text('07:52'), findsOneWidget);
    expect(find.text('Noté à part de l\'appel en classe : il ne le modifie pas.'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer le retard'));
    await tester.pumpAndSettle();
    await settle(tester);

    final put = server.requests.singleWhere((r) => r.method == 'PUT');
    final id = put.path.split('/').last;
    expect(id, matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
    expect(put.body, {
      'studentId': 'st-2',
      'date': '2026-11-02',
      'arrivalTime': '07:52',
      'recordedAt': now.toUtc().toIso8601String(),
    });
    expect(find.text('Retard enregistré : Etoa Élodie à 07:52.'), findsOneWidget);
    expect(find.text('2 retards aujourd\'hui'), findsOneWidget);
    expect(find.byKey(ValueKey('lateness-$id')), findsOneWidget);
  });

  testWidgets('before the start time, an arrival is not late and cannot be recorded (BR-7)', (tester) async {
    now = DateTime(2026, 11, 2, 7, 25);
    final server = await open(tester);

    await search(tester, 'abanda');
    await tester.tap(find.text('Abanda Aline'));
    await tester.pumpAndSettle();

    expect(
      find.text("Les cours commencent à 07:30 : arriver à cette heure ou avant n'est pas un retard."),
      findsOneWidget,
    );
    final button = tester.widget<FilledButton>(find.widgetWithText(FilledButton, 'Enregistrer le retard'));
    expect(button.onPressed, isNull);
    expect(server.requests.where((r) => r.method == 'PUT'), isEmpty);
  });

  testWidgets('with no network, searches the copy on the phone and keeps the record to send (NFR-3)', (tester) async {
    await ResponseCache(db, now: () => DateTime(2026, 11, 2, 7, 5)).put('user-1', 'gate-students', students);
    final server = await open(tester, offline: true);

    await search(tester, 'etoa');
    await recordFirstResult(tester, 'Etoa Élodie');

    expect(find.text('Sur ce téléphone, en attente d\'envoi'), findsOneWidget);
    expect(find.text('Etoa Élodie'), findsOneWidget);
    final item = await tester.runAsync(() => db.select(db.syncItems).getSingle());
    expect(item!.state, SyncState.pending);
    expect(item.kind, 'lateness');
    expect(find.byKey(const ValueKey('sync-banner')), findsOneWidget);
    expect(server.requests.where((r) => r.method == 'PUT'), isNotEmpty, reason: 'tried, and will again');

    // ends the sync backoff timer before the test does
    await tester.pumpWidget(const SizedBox());
    app.dispose();
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pump(const Duration(milliseconds: 1));
  });

  testWidgets('a lateness the school refuses is shown with its reason, and not counted', (tester) async {
    await open(
      tester,
      saveReply: Reply.error(409, 'lateness.already_recorded', 'Cet élève a déjà un retard aujourd\'hui.'),
    );

    await search(tester, 'etoa');
    await recordFirstResult(tester, 'Etoa Élodie');

    expect(
      find.text("Refusé par l'école : Etoa Élodie à 07:52. Cet élève a déjà un retard aujourd'hui."),
      findsOneWidget,
    );
    expect(find.text('1 retard aujourd\'hui'), findsOneWidget);
  });

  testWidgets('the gate reads in English (NFR-12)', (tester) async {
    await open(tester, language: 'en');

    expect(find.text('1 late arrival today'), findsOneWidget);
    expect(find.text('Classes start at 07:30'), findsOneWidget);
    await search(tester, 'etoa');
    await tester.tap(etoa);
    await tester.pumpAndSettle();
    expect(find.text('Record lateness'), findsOneWidget);
  });

  testWidgets("the DM sees today's lateness in their classes under their alerts (UC-AD2)", (tester) async {
    final dm = userJson(roles: ['DM']);
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => const Reply.ok([]))
      ..on(
        'GET /lateness',
        (_) => Reply.ok(
          today([
            {...chantalLate, 'justified': true},
          ]),
        ),
      );
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(dm)), database: db, clock: () => now);

    expect(find.text('Alertes à traiter'), findsOneWidget);
    expect(find.text('Aucune alerte à traiter'), findsWidgets);
    expect(find.text('Retards du jour'), findsOneWidget);
    expect(find.text('Fictif Chantal'), findsOneWidget);
    expect(find.text('Justifié'), findsOneWidget);
    expect(find.byKey(const ValueKey('gate-search')), findsNothing, reason: 'the DM does not record lateness');
  });
}
