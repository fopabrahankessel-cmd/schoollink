import 'dart:async';

import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// Task 2.15, BR-6, UC-T2: a roll recorded with no network waits on the phone under a banner, and
/// reaches the server exactly once when the network is back.
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  late AppDatabase db;
  late StreamController<bool> network;
  final now = DateTime(2026, 11, 2, 8, 40); // Monday, period 2 under way

  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
    network = StreamController<bool>.broadcast();
  });
  tearDown(() => network.close());

  final monday = {
    'date': '2026-11-02',
    'schoolDay': true,
    'sessions': [
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
  final classes = [
    {
      'classId': 'class-5B',
      'className': '5e B',
      'students': [
        {'id': 'st-1', 'matricule': 'MAT-1', 'firstName': 'Aline', 'lastName': 'Abanda'},
        {'id': 'st-2', 'matricule': 'MAT-2', 'firstName': 'Brice', 'lastName': 'Bello'},
      ],
    },
  ];

  /// Lets the sync queue's database work run outside the fake clock.
  Future<void> settle(WidgetTester tester) async {
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pumpAndSettle();
  }

  /// Opens the app with the server answering reads; `PUT /sessions/*` goes through [save].
  Future<(TestApp, FakeServer)> open(WidgetTester tester, Handler save) async {
    final teacher = userJson(roles: ['TEACHER']);
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(teacher))
      ..on('GET /me/sessions', (_) => Reply.ok(monday))
      ..on('GET /me/classes', (_) => Reply.ok(classes))
      ..on('PUT /sessions/*', save);
    final app = await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(teacher)),
      database: db,
      online: network.stream,
      clock: () => now,
    );
    return (app, server);
  }

  /// UC-T1: Bello absent, confirmed twice.
  Future<void> recordRoll(WidgetTester tester) async {
    await tester.tap(find.text('5e B'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Bello Brice'));
    await tester.pump();
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer'));
    await tester.pumpAndSettle();
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Oui')));
    await tester.pumpAndSettle();
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Confirmer')));
    await tester.pumpAndSettle();
    await settle(tester);
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
  }

  /// Ends the app and its sync backoff timer before the test does.
  Future<void> close(WidgetTester tester, TestApp app) async {
    await tester.pumpWidget(const SizedBox());
    app.dispose();
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pump(const Duration(milliseconds: 1));
  }

  final banner = find.byKey(const ValueKey('sync-banner'));

  testWidgets('offline, then online: the roll waits under a banner and is synced once', (tester) async {
    var reachable = false;
    var accepted = 0;
    final (app, server) = await open(tester, (_) {
      if (!reachable) {
        return Reply.unreachable;
      }
      accepted++;
      return const Reply(201, {});
    });

    network.add(false);
    await recordRoll(tester);

    expect(banner, findsOneWidget);
    expect(find.text('Pas de réseau : 1 enregistrement sera envoyé à son retour.'), findsOneWidget);
    expect(find.text("Enregistrée sur ce téléphone, en attente d'envoi."), findsOneWidget);
    expect(accepted, 0);

    reachable = true;
    network.add(true);
    await settle(tester);

    expect(accepted, 1);
    expect(banner, findsNothing);
    expect(find.text("Enregistrée sur ce téléphone, en attente d'envoi."), findsNothing);
    final state = await tester.runAsync(() => db.select(db.syncItems).getSingle());
    expect(state!.state, SyncState.synced);

    // the network coming and going again sends nothing more
    network
      ..add(false)
      ..add(true);
    await settle(tester);
    expect(accepted, 1);
    expect(server.requests.where((r) => r.method == 'PUT').map((r) => r.path).toSet(), hasLength(1));

    await close(tester, app);
  });

  testWidgets('"Send now" sends at once, without waiting for the next attempt', (tester) async {
    var reachable = false;
    var accepted = 0;
    final (app, _) = await open(tester, (_) {
      if (!reachable) {
        return Reply.unreachable;
      }
      accepted++;
      return const Reply(201, {});
    });

    await recordRoll(tester);
    expect(find.text("1 enregistrement en attente d'envoi."), findsOneWidget);

    reachable = true;
    await tester.tap(find.widgetWithText(TextButton, 'Envoyer'));
    await settle(tester);

    expect(accepted, 1);
    expect(banner, findsNothing);

    await close(tester, app);
  });
}
