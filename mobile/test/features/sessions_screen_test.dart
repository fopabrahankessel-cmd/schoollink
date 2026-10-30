import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/cache/response_cache.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/teacher/sessions_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-T3: the teacher's sessions today, the period under way first, those to record highlighted,
/// and the list still there with no network (NFR-3).
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  late AppDatabase db;
  late DateTime now;

  setUp(() {
    db = AppDatabase(NativeDatabase.memory());
    now = DateTime(2026, 11, 2, 8, 40); // a Monday, during period 2
  });

  Map<String, dynamic> session(
    String className,
    int period,
    String start,
    String end, {
    String? subject,
    bool recorded = false,
    int? absent,
  }) => {
    'classId': 'class-$className',
    'className': className,
    'periodId': 'period-$period',
    'periodNumber': period,
    'startTime': '$start:00',
    'endTime': '$end:00',
    'subject': subject,
    'current': false, // the phone works it out from its clock
    'recorded': recorded,
    'sessionId': recorded ? 'session-$period' : null,
    'confirmedAt': recorded ? '2026-11-02T06:35:00Z' : null,
    'absentCount': absent,
  };

  final monday = {
    'date': '2026-11-02',
    'schoolDay': true,
    'sessions': [
      session('6e A', 1, '07:30', '08:25', subject: 'Mathématiques', recorded: true, absent: 2),
      session('5e B', 2, '08:25', '09:20', subject: 'Français'),
      session('6e A', 3, '09:20', '10:15', subject: 'SVT'),
    ],
  };

  final teacher = userJson(roles: ['TEACHER']);

  Future<FakeServer> open(WidgetTester tester, {Object? day, String language = 'fr', bool offline = false}) async {
    final server = FakeServer()
      ..on('GET /me', (_) => offline ? Reply.unreachable : Reply.ok(teacher))
      ..on('GET /me/sessions', (_) => offline ? Reply.unreachable : Reply.ok(day ?? monday));
    await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(teacher)),
      database: db,
      clock: () => now,
      language: language,
    );
    return server;
  }

  Finder tile(String className, int period) => find.byKey(ValueKey('session-class-$className-period-$period'));

  List<Finder> inScreenOrder(WidgetTester tester, List<Finder> tiles) =>
      [...tiles]..sort((a, b) => tester.getTopLeft(a).dy.compareTo(tester.getTopLeft(b).dy));

  testWidgets('the period under way comes first, then the others by time', (tester) async {
    await open(tester);

    final p1 = tile('6e A', 1), p2 = tile('5e B', 2), p3 = tile('6e A', 3);
    expect(inScreenOrder(tester, [p1, p2, p3]), [p2, p1, p3]);
    expect(find.descendant(of: p2, matching: find.text('En cours')), findsOneWidget);
    expect(find.text('En cours'), findsOneWidget);
    expect(find.text('lundi 2 novembre 2026'), findsOneWidget);
  });

  testWidgets('sessions still to record are highlighted; recorded ones give their absentees', (tester) async {
    await open(tester);
    final scheme = Theme.of(tester.element(find.byType(SessionsScreen))).colorScheme;
    Color colorOf(Finder t) => tester.widget<Card>(t).color!;

    expect(find.descendant(of: tile('5e B', 2), matching: find.text('À faire')), findsOneWidget);
    expect(find.descendant(of: tile('6e A', 3), matching: find.text('À faire')), findsOneWidget);
    expect(find.descendant(of: tile('6e A', 1), matching: find.text('Enregistré, 2 absents')), findsOneWidget);
    expect(colorOf(tile('5e B', 2)), scheme.primaryContainer);
    expect(colorOf(tile('6e A', 3)), scheme.primaryContainer);
    expect(colorOf(tile('6e A', 1)), isNot(scheme.primaryContainer));
  });

  testWidgets('as the time passes, the next period moves to the top', (tester) async {
    await open(tester);

    now = DateTime(2026, 11, 2, 9, 25);
    await tester.pump(const Duration(minutes: 1));

    final p1 = tile('6e A', 1), p2 = tile('5e B', 2), p3 = tile('6e A', 3);
    expect(inScreenOrder(tester, [p1, p2, p3]), [p3, p1, p2]);
    expect(find.descendant(of: p3, matching: find.text('En cours')), findsOneWidget);
  });

  testWidgets('with no network, the list saved on the phone is shown, and says so (NFR-3)', (tester) async {
    await ResponseCache(db, now: () => DateTime(2026, 11, 2, 7, 5)).put('user-1', 'sessions:2026-11-02', monday);

    await open(tester, offline: true);

    expect(find.text('Pas de connexion. Affiché tel que mis à jour à 07:05.'), findsOneWidget);
    expect(find.descendant(of: tile('5e B', 2), matching: find.text('À faire')), findsOneWidget);
  });

  testWidgets('a list read online is saved for later', (tester) async {
    await open(tester);

    final cached = await ResponseCache(db).get('user-1', 'sessions:2026-11-02');
    expect((cached!.value! as Map)['sessions'], hasLength(3));
    expect(find.textContaining('Pas de connexion'), findsNothing);
  });

  testWidgets("yesterday's list is not shown for today", (tester) async {
    await ResponseCache(db).put('user-1', 'sessions:2026-10-30', monday);

    await open(tester, offline: true);

    expect(find.text('Pas de connexion au serveur. Vérifiez votre réseau et réessayez.'), findsOneWidget);
    expect(find.text('Réessayer'), findsOneWidget);
  });

  testWidgets('retrying after a failure loads the list', (tester) async {
    final server = await open(tester, offline: true);
    server.on('GET /me/sessions', (_) => Reply.ok(monday));

    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();

    expect(tile('5e B', 2), findsOneWidget);
  });

  testWidgets('pulling the list down reads it again', (tester) async {
    final server = await open(tester);
    final recorded = {
      ...monday,
      'sessions': [
        session('6e A', 1, '07:30', '08:25', recorded: true, absent: 2),
        session('5e B', 2, '08:25', '09:20', recorded: true, absent: 0),
        session('6e A', 3, '09:20', '10:15'),
      ],
    };
    server.on('GET /me/sessions', (_) => Reply.ok(recorded));

    await tester.fling(find.byType(ListView), const Offset(0, 400), 1000);
    await tester.pumpAndSettle();

    expect(server.sent('GET /me/sessions'), hasLength(2));
    expect(find.descendant(of: tile('5e B', 2), matching: find.text('Enregistré, aucun absent')), findsOneWidget);
  });

  testWidgets('a day with no school says so', (tester) async {
    await open(tester, day: {'date': '2026-11-02', 'schoolDay': false, 'sessions': <Object>[]});

    expect(find.text("Pas de cours aujourd'hui : ce n'est pas un jour de classe."), findsOneWidget);
  });

  testWidgets('a school day with no classes for this teacher says so', (tester) async {
    await open(tester, day: {'date': '2026-11-02', 'schoolDay': true, 'sessions': <Object>[]});

    expect(find.text("Vous n'avez aucun cours à l'emploi du temps aujourd'hui."), findsOneWidget);
  });

  testWidgets('the list renders in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['TEACHER'], language: 'EN');
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(english))
      ..on('GET /me/sessions', (_) => Reply.ok(monday));
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(english)), database: db, clock: () => now);

    expect(find.text('Now'), findsOneWidget);
    expect(find.text('To record'), findsNWidgets(2));
    expect(find.text('Recorded, 2 absent'), findsOneWidget);
    expect(find.text('Monday, November 2, 2026'), findsOneWidget);
  });
}
