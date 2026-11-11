import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/router.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// D41, UC-D2, UC-D10: the DM's Today tab counts the alerts to decide, the periods not recorded and
/// the day's absences and lateness; each count opens its list. A count that could not be read
/// offers a retry and never shows 0. Names are made up.
void main() {
  late FakeServer server;
  late Reply dayReply;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  Map<String, dynamic> alert(String id, String first) => {
    'id': id,
    'studentId': 'st-$id',
    'firstName': first,
    'lastName': 'Fictif',
    'matricule': 'TW-$id',
    'classId': 'c-1',
    'className': '6e A',
    'ruleType': 'FULL_DAY_ABSENCE',
    'threshold': 1,
    'asOf': '2026-11-09',
    'evidence': {
      'dates': ['2026-11-09'],
    },
    'status': 'OPEN',
    'raisedAt': '2026-11-09T15:00:00Z',
  };
  const day = Reply.ok({
    'date': '2026-11-10',
    'schoolDay': true,
    'totals': {'notRecorded': 2, 'absentStudents': 14, 'lateness': 6},
    'classes': [],
  });

  setUp(() {
    dayReply = day;
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => Reply.ok([alert('a1', 'Awa'), alert('a2', 'Bruno'), alert('a3', 'Chantal')]))
      ..on('GET /attendance/day', (_) => dayReply);
  });

  Future<TestApp> open(WidgetTester tester, {Map<String, dynamic>? user}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    return pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user ?? dm)), clock: () => now);
  }

  Future<void> tap(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  String location(TestApp app) => app.container.read(routerProvider).state.uri.toString();

  testWidgets('today shows the three counts', (tester) async {
    await open(tester);

    expect(find.text('3 alertes à traiter'), findsOneWidget);
    expect(find.text('2 périodes non saisies'), findsOneWidget);
    expect(find.text('14 absents · 6 retards'), findsOneWidget);
    expect(find.byKey(const ValueKey('open-full-day')), findsOneWidget);
  });

  testWidgets('each card opens its list', (tester) async {
    final app = await open(tester);

    await tap(tester, find.byKey(const ValueKey('today-alerts')));
    expect(location(app), '/home/today/alerts');
    expect(find.byKey(const ValueKey('alert-a1')), findsOneWidget);
    expect(find.byKey(const ValueKey('alert-a3')), findsOneWidget);
    await tap(tester, find.byType(BackButton));

    await tap(tester, find.byKey(const ValueKey('today-not-recorded')));
    expect(location(app), '/home/today/day');
    await tap(tester, find.byType(BackButton));

    await tap(tester, find.byKey(const ValueKey('today-absences')));
    expect(location(app), '/home/today/day');
  });

  testWidgets('the search field leads to the Students tab', (tester) async {
    final app = await open(tester);

    await tap(tester, find.byKey(const ValueKey('open-search')));
    expect(location(app), '/home/students');
  });

  testWidgets('a failed count offers a retry, never zero', (tester) async {
    dayReply = const Reply(500, {'message': 'Erreur'});
    await open(tester);

    expect(find.text('3 alertes à traiter'), findsOneWidget);
    expect(find.byKey(const ValueKey('today-retry-day')), findsOneWidget);
    expect(find.textContaining('0 absent'), findsNothing);
    expect(find.textContaining('0 période'), findsNothing);
    expect(find.text('Toutes les périodes sont saisies'), findsNothing);

    dayReply = day;
    await tap(tester, find.byKey(const ValueKey('today-retry-day')));
    expect(server.sent('GET /attendance/day'), hasLength(2));
    expect(find.text('2 périodes non saisies'), findsOneWidget);
  });

  testWidgets('pull to refresh reads both again', (tester) async {
    await open(tester);

    await tester.fling(find.byType(RefreshIndicator).first, const Offset(0, 400), 1000);
    await tester.pumpAndSettle();

    expect(server.sent('GET /alerts'), hasLength(2));
    expect(server.sent('GET /attendance/day'), hasLength(2));
  });

  testWidgets('a refresh that fails shows the retry, not the old counts', (tester) async {
    await open(tester);
    expect(find.text('2 périodes non saisies'), findsOneWidget);

    dayReply = const Reply(500, {'code': 'server.error', 'message': 'Erreur'});
    await tester.fling(find.byType(RefreshIndicator).first, const Offset(0, 400), 1000);
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('today-retry-day')), findsOneWidget);
    expect(find.text('2 périodes non saisies'), findsNothing);
    expect(find.text('14 absents · 6 retards'), findsNothing);
  });

  testWidgets('a day without lessons says so instead of counting', (tester) async {
    dayReply = const Reply.ok({
      'date': '2026-11-14',
      'schoolDay': false,
      'totals': {'notRecorded': 0, 'absentStudents': 0, 'lateness': 0},
      'classes': [],
    });
    await open(tester);

    expect(find.text("Pas de cours aujourd'hui : rien à saisir."), findsOneWidget);
    expect(find.byKey(const ValueKey('today-not-recorded')), findsNothing);
    expect(find.byKey(const ValueKey('today-absences')), findsNothing);
    expect(find.text('3 alertes à traiter'), findsOneWidget, reason: 'alerts wait for a decision whatever the day');
  });

  testWidgets('nothing left to do reads as such', (tester) async {
    server
      ..on('GET /alerts', (_) => const Reply.ok([]))
      ..on(
        'GET /attendance/day',
        (_) => const Reply.ok({
          'date': '2026-11-10',
          'schoolDay': true,
          'totals': {'notRecorded': 0, 'absentStudents': 0, 'lateness': 0},
          'classes': [],
        }),
      );
    await open(tester);

    expect(find.text('Aucune alerte à traiter'), findsOneWidget);
    expect(find.text('Toutes les périodes sont saisies'), findsOneWidget);
    expect(find.text('0 absent · 0 retard'), findsOneWidget);
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, user: english);

    expect(find.text('3 alerts to decide'), findsOneWidget);
    expect(find.text('2 periods not recorded'), findsOneWidget);
    expect(find.text('14 absent · 6 late'), findsOneWidget);
  });
}
