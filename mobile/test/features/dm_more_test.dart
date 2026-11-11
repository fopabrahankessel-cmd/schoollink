import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/router.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// D41: the DM's More tab holds the decided alerts (UC-D2), the scope dashboard (UC-D8), the
/// settings (UC-D7), signing out, and the tabs that did not fit in the bar. Names are made up.
void main() {
  late FakeServer server;
  late List<Map<String, dynamic>> closed;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  Map<String, dynamic> decided() => {
    'id': 'al-9',
    'studentId': 'st-1',
    'firstName': 'Awa',
    'lastName': 'Fictif',
    'matricule': 'TW-0001',
    'classId': 'c-1',
    'className': '6e A',
    'ruleType': 'FULL_DAY_ABSENCE',
    'threshold': 1,
    'asOf': '2026-11-04',
    'evidence': {
      'dates': ['2026-11-04'],
    },
    'status': 'CLOSED',
    'raisedAt': '2026-11-04T15:00:00Z',
    'decision': 'SUMMON',
    'decisionNote': null,
    'decidedAt': '2026-11-05T09:00:00Z',
  };

  setUp(() {
    closed = [decided()];
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (request) => Reply.ok(request.query['status'] == 'closed' ? closed : <Object>[]))
      ..on(
        'GET /attendance/day',
        (_) => const Reply.ok({
          'date': '2026-11-10',
          'schoolDay': true,
          'totals': {'notRecorded': 0, 'absentStudents': 0, 'lateness': 0},
          'classes': [],
        }),
      )
      ..on('GET /me/sessions', (_) => const Reply.ok({'date': '2026-11-10', 'sessions': []}))
      ..on('GET /me/classes', (_) => const Reply.ok([]))
      ..on('GET /me/children', (_) => const Reply.ok([]))
      ..on('GET /me/notifications', (_) => const Reply.ok([]))
      ..on('POST /auth/logout', (_) => const Reply(204));
  });

  Future<TestApp> open(WidgetTester tester, {Map<String, dynamic>? user}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final app = await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user ?? dm)), clock: () => now);
    await tester.tap(find.text(user?['language'] == 'EN' ? 'More' : 'Plus'));
    await tester.pumpAndSettle();
    return app;
  }

  Future<void> tap(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  String location(TestApp app) => app.container.read(routerProvider).state.uri.toString();

  testWidgets("More lists the DM's other screens", (tester) async {
    await open(tester);

    expect(find.byKey(const ValueKey('more-closed-alerts')), findsOneWidget);
    expect(find.byKey(const ValueKey('more-dashboard')), findsOneWidget);
    expect(find.byKey(const ValueKey('more-settings')), findsOneWidget);
    expect(find.byKey(const ValueKey('more-sign-out')), findsOneWidget);
    expect(find.text('Alertes traitées'), findsOneWidget);
  });

  testWidgets('decided alerts show the decision', (tester) async {
    final app = await open(tester);
    await tap(tester, find.byKey(const ValueKey('more-closed-alerts')));

    expect(location(app), '/home/more/alerts/closed');
    expect(server.sent('GET /alerts').last.query, {'status': 'closed'});
    expect(find.text('Fictif Awa'), findsOneWidget);
    expect(find.text('Parents convoqués'), findsOneWidget);
    expect(find.textContaining('Décidée le 5 nov.'), findsOneWidget);
  });

  testWidgets('a decided alert opens the student page', (tester) async {
    server
      ..on('GET /students/st-1/history', (_) => const Reply(500, {}))
      ..on('GET /students/st-1/disciplinary-records', (_) => const Reply.ok([]));
    final app = await open(tester);
    await tap(tester, find.byKey(const ValueKey('more-closed-alerts')));
    await tap(tester, find.byKey(const ValueKey('closed-al-9')));

    expect(location(app), '/home/more/student/st-1');
  });

  testWidgets('no decided alert says so', (tester) async {
    closed = [];
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('more-closed-alerts')));

    expect(find.text('Aucune alerte traitée pour le moment.'), findsOneWidget);
  });

  testWidgets('sign out from More', (tester) async {
    final app = await open(tester);
    await tap(tester, find.byKey(const ValueKey('more-sign-out')));

    expect(location(app), '/login');
  });

  testWidgets('overflow tabs are listed', (tester) async {
    final everyone = userJson(roles: ['TEACHER', 'DM', 'PARENT']);
    server.on('GET /me', (_) => Reply.ok(everyone));
    final app = await open(tester, user: everyone);

    expect(find.byKey(const ValueKey('more-tab-children')), findsOneWidget);
    expect(find.byKey(const ValueKey('more-tab-inbox')), findsOneWidget);
    await tap(tester, find.byKey(const ValueKey('more-tab-children')));
    expect(location(app), '/home/children');
    expect(tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex, 4, reason: 'Plus stays selected');
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, user: english);

    expect(find.text('Decided alerts'), findsOneWidget);
    expect(find.text('Dashboard'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('Sign out'), findsWidgets);
  });
}
