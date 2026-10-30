import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:schoollink/core/push/push_client.dart';
import 'package:schoollink/core/push/push_registration.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/parent/history_screen.dart';
import 'package:schoollink/features/parent/inbox_screen.dart';

import '../core/push_registration_test.dart' show FakePushClient;
import '../support/fake_server.dart';
import '../support/harness.dart';

/// Task 2.19, UC-P2, UC-P3: the parent's notifications, with the open receipt (BR-24), and each
/// child's attendance history, in plain words (NFR-2). Names are made up.
void main() {
  late FakeServer server;
  late MemoryDeviceStore store;
  final parent = userJson(roles: ['PARENT']);
  final now = DateTime(2026, 11, 2, 18);

  Map<String, dynamic> message(String id, String type, String at, {bool opened = false, String? student}) => {
    'id': id,
    'eventType': type,
    'studentId': student,
    'title': 'Titre $id',
    'body': 'Texte du message $id.',
    'createdAt': at,
    'openedAt': opened ? at : null,
  };

  final inbox = [
    message('n-3', 'ABSENCE', '2026-11-02T11:00:00Z', student: 'st-1'),
    message('n-2', 'LATENESS', '2026-11-02T10:00:00Z', opened: true, student: 'st-1'),
    message('n-1', 'SUMMONS', '2026-10-30T12:00:00Z'),
  ];

  final history = {
    'studentId': 'st-1',
    'firstName': 'Awa',
    'from': '2026-08-04',
    'to': '2026-11-02',
    'totals': {'absentPeriods': 3, 'unjustifiedAbsentPeriods': 2, 'lateness': 1, 'unjustifiedLateness': 0},
    'days': [
      {
        'date': '2026-11-02',
        'absences': [
          {'periodNumber': 3, 'startTime': '09:20:00', 'justified': false},
          {'periodNumber': 2, 'startTime': '08:25:00', 'justified': false},
        ],
        'lateness': {'arrivalTime': '07:41:00', 'justified': true},
      },
      {
        'date': '2026-10-20',
        'absences': [
          {'periodNumber': 1, 'startTime': '07:30:00', 'justified': true},
        ],
        'lateness': null,
      },
    ],
  };

  setUp(() {
    store = MemoryDeviceStore(signedIn(parent));
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(parent))
      ..on(
        'GET /me/children',
        (_) => const Reply.ok([
          {'id': 'st-1', 'firstName': 'Awa', 'lastName': 'Fictif', 'classId': 'c-1', 'className': '6e A'},
          {'id': 'st-2', 'firstName': 'Paul', 'lastName': 'Fictif', 'classId': 'c-2', 'className': '3e B'},
        ]),
      )
      ..on('GET /me/notifications', (_) => Reply.ok(inbox))
      ..on('POST /notifications/*', (_) => const Reply(204))
      ..on('GET /children/st-1/history', (_) => Reply.ok(history));
  });

  Future<TestApp> open(WidgetTester tester, {String language = 'fr', FakePushClient? push}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    return pumpApp(
      tester,
      server: server,
      store: store,
      clock: () => now,
      language: language,
      overrides: [if (push != null) pushClientProvider.overrideWithValue(push)],
    );
  }

  Finder tab(String label) => find.descendant(of: find.byType(NavigationBar), matching: find.text(label));
  Finder badge(String count) =>
      find.descendant(of: find.byKey(const ValueKey('unread-badge')), matching: find.text(count));

  Future<void> openInbox(WidgetTester tester) async {
    await tester.tap(tab('Notifications'));
    await tester.pumpAndSettle();
  }

  testWidgets('the inbox lists messages by day, unread ones counted and marked (UC-P2)', (tester) async {
    await open(tester);
    expect(badge('2'), findsWidgets, reason: 'seen from the children tab');

    await openInbox(tester);

    expect(find.text('2 messages non lus'), findsOneWidget);
    expect(find.text("Aujourd'hui"), findsOneWidget);
    expect(find.text('Vendredi 30 octobre'), findsOneWidget);
    final newest = tester.getTopLeft(find.text('Titre n-3')).dy;
    expect(newest, lessThan(tester.getTopLeft(find.text('Titre n-2')).dy));
    expect(tester.getTopLeft(find.text('Titre n-2')).dy, lessThan(tester.getTopLeft(find.text('Titre n-1')).dy));
    expect(find.byKey(const ValueKey('unread-dot')), findsNWidgets(2));
    final time = DateFormat.Hm('fr').format(DateTime.parse('2026-11-02T11:00:00Z').toLocal());
    expect(find.text(time), findsOneWidget);
    expect(server.sent('GET /me/notifications').first.query['limit'], '30');
  });

  testWidgets('opening a message shows it in full and sends the receipt once (BR-24)', (tester) async {
    await open(tester);
    await openInbox(tester);

    await tester.tap(find.text('Titre n-3'));
    await tester.pumpAndSettle();

    expect(find.byType(MessageScreen), findsOneWidget);
    expect(find.widgetWithText(AppBar, 'Absence'), findsOneWidget);
    expect(find.text('Texte du message n-3.'), findsOneWidget);
    expect(find.textContaining('Reçu le lundi 2 novembre 2026 à'), findsOneWidget);
    expect(server.sent('POST /notifications/n-3/opened'), hasLength(1));

    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('1 message non lu'), findsOneWidget);
    expect(badge('1'), findsWidgets);

    await tester.tap(find.text('Titre n-3'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(server.sent('POST /notifications/n-3/opened'), hasLength(1), reason: 'read already');

    await tester.tap(find.text('Titre n-2'));
    await tester.pumpAndSettle();
    expect(server.sent('POST /notifications/n-2/opened'), isEmpty, reason: 'the server has it opened');
  });

  testWidgets('a receipt sent with no network waits on the phone and goes with the next load', (tester) async {
    server.on('POST /notifications/*', (_) => Reply.unreachable);
    await open(tester);
    await openInbox(tester);
    await tester.tap(find.text('Titre n-3'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('1 message non lu'), findsOneWidget, reason: 'read on the phone at once');

    server.on('POST /notifications/*', (_) => const Reply(204));
    await tester.fling(find.text('1 message non lu'), const Offset(0, 500), 1000);
    await tester.pumpAndSettle();

    expect(server.sent('POST /notifications/n-3/opened'), hasLength(2));
    expect(find.text('1 message non lu'), findsOneWidget);
  });

  testWidgets('a message about an absence leads to the history, absences and lateness apart (UC-P3)', (tester) async {
    await open(tester);
    await openInbox(tester);
    await tester.tap(find.text('Titre n-3'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Voir les absences et retards'));
    await tester.pumpAndSettle();

    expect(find.byType(ChildHistoryScreen), findsOneWidget);
    expect(find.widgetWithText(AppBar, 'Historique de Awa'), findsOneWidget);
    expect(find.text('Du 4 août au 2 novembre'), findsOneWidget);
    final absences = find.byKey(const ValueKey('total-absences'));
    expect(find.descendant(of: absences, matching: find.text('3')), findsOneWidget);
    expect(find.descendant(of: absences, matching: find.text("heures d'absence")), findsOneWidget);
    expect(find.descendant(of: absences, matching: find.text('dont 2 non justifiées')), findsOneWidget);
    final lateness = find.byKey(const ValueKey('total-lateness'));
    expect(find.descendant(of: lateness, matching: find.text('retard')), findsOneWidget);
    expect(find.descendant(of: lateness, matching: find.text('aucun non justifié')), findsOneWidget);

    // BR-8, BR-9: the lateness and the absences of one day are separate lines
    final today = find.byKey(const ValueKey('day-2026-11-02'));
    expect(find.descendant(of: today, matching: find.text("Aujourd'hui")), findsOneWidget);
    expect(find.descendant(of: today, matching: find.text('Arrivée à 07:41')), findsOneWidget);
    expect(find.descendant(of: today, matching: find.text('Justifié')), findsOneWidget);
    expect(find.descendant(of: today, matching: find.text('Non justifiée')), findsNWidgets(2));
    final first = tester.getTopLeft(find.text('Cours de 08:25')).dy;
    expect(first, lessThan(tester.getTopLeft(find.text('Cours de 09:20')).dy));
    final older = find.byKey(const ValueKey('day-2026-10-20'));
    expect(find.descendant(of: older, matching: find.text('Mardi 20 octobre')), findsOneWidget);
    expect(find.descendant(of: older, matching: find.text('Justifiée')), findsOneWidget);
  });

  testWidgets('the children tab opens each child; a clean record says so', (tester) async {
    server.on(
      'GET /children/st-2/history',
      (_) => const Reply.ok({
        'studentId': 'st-2',
        'firstName': 'Paul',
        'from': '2026-08-04',
        'to': '2026-11-02',
        'totals': {'absentPeriods': 0, 'unjustifiedAbsentPeriods': 0, 'lateness': 0, 'unjustifiedLateness': 0},
        'days': <Object>[],
      }),
    );
    await open(tester);
    expect(find.text('Vos 2 enfants'), findsOneWidget);
    expect(find.text('Classe : 3e B'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('child-st-2')));
    await tester.pumpAndSettle();

    expect(find.widgetWithText(AppBar, 'Historique de Paul'), findsOneWidget);
    expect(find.text('Aucune absence ni retard pour Paul'), findsOneWidget);
    expect(find.text('dont 0 non justifiée'), findsNothing);
  });

  testWidgets('older messages load at the end of the list, from the last one shown', (tester) async {
    final page = [
      for (var i = 0; i < 30; i++)
        message(
          'p-$i',
          'LATENESS',
          DateTime.utc(2026, 11, 2, 12).subtract(Duration(hours: i)).toIso8601String(),
          opened: true,
        ),
    ];
    server.on('GET /me/notifications', (r) {
      return r.query['before'] == null
          ? Reply.ok(page)
          : Reply.ok([message('old-1', 'ABSENCE', '2026-09-01T12:00:00Z', opened: true)]);
    });
    await open(tester);
    await openInbox(tester);

    final more = find.text('Afficher les messages plus anciens');
    await tester.ensureVisible(more);
    await tester.pumpAndSettle();
    await tester.tap(more);
    await tester.pumpAndSettle();

    expect(server.sent('GET /me/notifications').last.query, {'limit': '30', 'before': 'p-29'});
    await tester.ensureVisible(find.text('Titre old-1'));
    expect(find.text('Titre old-1'), findsOneWidget);
    expect(more, findsNothing, reason: 'a short page is the last');
  });

  testWidgets('a tapped notification opens that message and sends its receipt', (tester) async {
    final push = FakePushClient()..startedBy = const PushMessage(title: 'A', body: 'B', notificationId: 'n-1');
    server.on('PUT /me/device-token', (_) => const Reply(204));
    await open(tester, push: push);

    expect(find.byType(MessageScreen), findsOneWidget);
    expect(find.widgetWithText(AppBar, 'Convocation'), findsOneWidget);
    expect(server.sent('POST /notifications/n-1/opened'), hasLength(1));
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('1 message non lu'), findsOneWidget);
  });

  testWidgets('with no network the inbox shows the copy on the phone', (tester) async {
    await open(tester);
    await openInbox(tester);

    server.offline = true;
    await tester.fling(find.text('2 messages non lus'), const Offset(0, 500), 1000);
    await tester.pumpAndSettle();

    expect(find.textContaining('Pas de connexion.'), findsOneWidget);
    expect(find.text('Titre n-3'), findsOneWidget);
  });

  testWidgets('the parent screens read in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['PARENT'], language: 'EN');
    store = MemoryDeviceStore(signedIn(english));
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, language: 'en');
    expect(find.text('Your 2 children'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('child-st-1')));
    await tester.pumpAndSettle();

    expect(find.text('From August 4 to November 2'), findsOneWidget);
    expect(find.text('lessons missed'), findsOneWidget);
    expect(find.text('Arrived at 07:41'), findsOneWidget);
    expect(find.text('Lesson at 08:25'), findsOneWidget);
  });
}
