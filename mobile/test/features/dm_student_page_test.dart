import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/router.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// D41, UC-D4, UC-D5, UC-D8: the student page has three tabs, History, Discipline and Messages,
/// and one "Agir" button: record an incident, message the parents, summon. Names are made up.
void main() {
  late FakeServer server;
  late List<Map<String, dynamic>> messages;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  Map<String, dynamic> message() => {
    'id': 'm-7',
    'kind': 'DM_MESSAGE',
    'audience': 'STUDENT',
    'studentId': 'st-1',
    'studentName': 'Fictif Awa',
    'classId': 'c-1',
    'className': '6e A',
    'body': 'Awa a oublié son cahier.',
    'sms': true,
    'authorName': 'Marie Surveillante',
    'createdAt': '2026-11-09T08:30:00Z',
    'delivery': {'recipients': 2, 'pending': 0, 'sent': 0, 'delivered': 1, 'opened': 1, 'failed': 0},
  };

  setUp(() {
    messages = [message()];
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => const Reply.ok([]))
      ..on(
        'GET /attendance/day',
        (_) => const Reply.ok({
          'date': '2026-11-10',
          'schoolDay': true,
          'totals': {'notRecorded': 0, 'absentStudents': 0, 'lateness': 0},
          'classes': [],
        }),
      )
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
      ..on('GET /students/st-1/disciplinary-records', (_) => const Reply.ok([]))
      ..on('GET /messages', (_) => Reply.ok(messages));
  });

  Future<void> tap(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  Future<TestApp> open(WidgetTester tester) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    final app = await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(dm)), clock: () => now);
    await tap(tester, find.text('Élèves'));
    await tester.enterText(find.byKey(const ValueKey('dm-search')), 'awa');
    await tester.pumpAndSettle();
    await tap(tester, find.byKey(const ValueKey('student-st-1')));
    return app;
  }

  String location(TestApp app) => app.container.read(routerProvider).state.uri.toString();

  testWidgets('three tabs', (tester) async {
    await open(tester);

    expect(find.widgetWithText(Tab, 'Historique'), findsOneWidget);
    expect(find.widgetWithText(Tab, 'Discipline'), findsOneWidget);
    expect(find.widgetWithText(Tab, 'Messages'), findsOneWidget);
  });

  testWidgets("the Messages tab lists this student's messages", (tester) async {
    await open(tester);
    await tap(tester, find.widgetWithText(Tab, 'Messages'));

    expect(server.sent('GET /messages').last.query, {'studentId': 'st-1'});
    expect(find.text('Awa a oublié son cahier.'), findsOneWidget);
  });

  testWidgets('no message yet says what to do', (tester) async {
    messages = [];
    await open(tester);
    await tap(tester, find.widgetWithText(Tab, 'Messages'));

    expect(find.text('Aucun message envoyé à ses parents.'), findsOneWidget);
    expect(find.text('Pour leur écrire, touchez « Agir ».'), findsOneWidget);
  });

  testWidgets('one action button', (tester) async {
    final app = await open(tester);

    expect(find.byKey(const ValueKey('history-message')), findsNothing);
    expect(find.byKey(const ValueKey('history-summon')), findsNothing);
    await tap(tester, find.byKey(const ValueKey('student-actions')));
    expect(find.byKey(const ValueKey('action-incident')), findsOneWidget);
    expect(find.byKey(const ValueKey('action-summon')), findsOneWidget);
    await tap(tester, find.byKey(const ValueKey('action-message')));

    expect(location(app), '/home/students/write?studentId=st-1');
  });

  testWidgets('summon from the action button', (tester) async {
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('student-actions')));
    await tap(tester, find.byKey(const ValueKey('action-summon')));

    expect(find.byKey(const ValueKey('summons-reason')), findsOneWidget);
  });

  testWidgets('a history that fails to load still leaves the incident and the message', (tester) async {
    server.on('GET /students/st-1/history', (_) => const Reply(500, {'code': 'server.error', 'message': 'Erreur'}));
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('student-actions')));

    expect(find.byKey(const ValueKey('action-incident')), findsOneWidget);
    expect(find.byKey(const ValueKey('action-message')), findsOneWidget);
    expect(find.byKey(const ValueKey('action-summon')), findsNothing, reason: "a summons names the student");
  });

  testWidgets('records ticked on History do not hide the button on the other tabs', (tester) async {
    server.on(
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
        'totals': {'absentPeriods': 1, 'unjustifiedAbsentPeriods': 1, 'lateness': 0, 'unjustifiedLateness': 0},
        'days': [
          {
            'date': '2026-11-09',
            'absences': [
              {'recordId': 'abs-1', 'periodNumber': 1, 'startTime': '07:30:00', 'justified': false},
            ],
            'lateness': null,
          },
        ],
      }),
    );
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('record-abs-1')));
    expect(find.byKey(const ValueKey('student-actions')), findsNothing, reason: 'the justify bar acts on History');

    await tap(tester, find.widgetWithText(Tab, 'Discipline'));
    expect(find.byKey(const ValueKey('student-actions')), findsOneWidget);
  });

  testWidgets('record an incident from the action button', (tester) async {
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('student-actions')));
    await tap(tester, find.byKey(const ValueKey('action-incident')));

    final tabs = tester.widget<TabBar>(find.byType(TabBar));
    expect(tabs.controller!.index, 1, reason: 'the Discipline tab');
    expect(find.byKey(const ValueKey('incident-description')), findsOneWidget);
  });
}
