import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/auth/user_info.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/shell/destinations.dart';
import 'package:schoollink/router.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// D41: the DM's tabs are Today, Students, Messages and More. A DM who also teaches keeps the
/// teacher's tab first; past five tabs, the bar shows four and More, which lists the rest. A screen
/// opened from a tab stays under that tab. Names are made up.
void main() {
  late FakeServer server;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  final sent = {
    'id': 'm-1',
    'kind': 'DM_MESSAGE',
    'audience': 'CLASS',
    'studentId': null,
    'studentName': null,
    'classId': 'c-1',
    'className': '6e A',
    'body': 'Réunion des parents vendredi à 15 h.',
    'sms': false,
    'authorName': 'Marie Surveillante',
    'createdAt': '2026-11-09T08:30:00Z',
    'delivery': {'recipients': 4, 'pending': 0, 'sent': 1, 'delivered': 2, 'opened': 1, 'failed': 0},
  };

  setUp(() {
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => const Reply.ok([]))
      ..on('GET /lateness', (_) => const Reply.ok({'date': '2026-11-10', 'startTime': '07:30:00', 'lateness': []}))
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
      ..on('GET /messages', (_) => Reply.ok([sent]))
      ..on(
        'GET /lateness/students',
        (_) => const Reply.ok([
          {
            'id': 'st-1',
            'matricule': 'TW-0001',
            'firstName': 'Awa',
            'lastName': 'Fictif',
            'classId': 'c-1',
            'className': '6e A',
          },
        ]),
      )
      ..on(
        'GET /students/st-1/history',
        (_) => const Reply.ok({
          'studentId': 'st-1',
          'matricule': 'TW-0001',
          'firstName': 'Awa',
          'lastName': 'Fictif',
          'classId': 'c-1',
          'className': '6e A',
          'from': '2026-08-12',
          'to': '2026-11-10',
          'totals': {'absentPeriods': 0, 'unjustifiedAbsentPeriods': 0, 'lateness': 0, 'unjustifiedLateness': 0},
          'days': <Object>[],
        }),
      )
      ..on('GET /students/st-1/disciplinary-records', (_) => const Reply.ok([]));
  });

  Future<TestApp> open(WidgetTester tester, {Map<String, dynamic>? user}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    return pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user ?? dm)), clock: () => now);
  }

  List<String> barLabels(WidgetTester tester) => [
    for (final d in tester.widget<NavigationBar>(find.byType(NavigationBar)).destinations)
      ((d as NavigationDestination).label),
  ];

  String location(TestApp app) => app.container.read(routerProvider).state.uri.toString();

  testWidgets('a DM has four tabs in order', (tester) async {
    final app = await open(tester);

    expect(barLabels(tester), ["Aujourd'hui", 'Élèves', 'Messages', 'Plus']);
    expect(location(app), '/home/today');
  });

  testWidgets('a DM who teaches keeps the teacher tab first', (tester) async {
    final user = userJson(roles: ['TEACHER', 'DM']);
    server.on('GET /me', (_) => Reply.ok(user));
    await open(tester, user: user);

    expect(barLabels(tester), ['Appel', "Aujourd'hui", 'Élèves', 'Messages', 'Plus']);
  });

  test('overflow goes to More', () {
    final all = destinationsFor({Role.teacher, Role.dm, Role.parent});
    expect(barFor(all), [
      Destination.sessions,
      Destination.today,
      Destination.students,
      Destination.messages,
      Destination.more,
    ]);
    expect(overflowOf(all), [Destination.children, Destination.inbox]);
    expect(barFor(destinationsFor({Role.dm})), destinationsFor({Role.dm}));
    expect(overflowOf(destinationsFor({Role.dm})), isEmpty);
  });

  testWidgets('the Students tab searches without its own app bar', (tester) async {
    await open(tester);
    await tester.tap(find.text('Élèves'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('dm-search')), findsOneWidget);
    expect(find.byType(AppBar), findsOneWidget);
  });

  testWidgets('a pushed screen stays under its tab', (tester) async {
    final app = await open(tester);
    await tester.tap(find.text('Élèves'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('dm-search')), 'awa');
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('student-st-1')));
    await tester.pumpAndSettle();

    expect(location(app), '/home/students/student/st-1');
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(location(app), '/home/students');
    expect(tester.widget<NavigationBar>(find.byType(NavigationBar)).selectedIndex, 1);
    expect(find.byKey(const ValueKey('dm-search')), findsOneWidget);
  });

  testWidgets('the Messages tab lists sent messages and writes a new one', (tester) async {
    final app = await open(tester);
    await tester.tap(find.text('Messages'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('sent-m-1')), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('dm-new-message')));
    await tester.pumpAndSettle();
    expect(location(app), '/home/messages/write');
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, user: english);

    expect(barLabels(tester), ['Today', 'Students', 'Messages', 'More']);
  });
}
