import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-P4, D38: parents read the DM's messages and the school's announcements in the inbox: two
/// lines in the list, the whole text once opened, and the child concerned for a message about one
/// student. Opening is the receipt (BR-24). Names are made up.
void main() {
  late FakeServer server;
  final parent = userJson(roles: ['PARENT']);
  final now = DateTime(2026, 11, 10, 18);
  final longText = List.filled(20, 'Réunion des parents vendredi.').join(' ');

  Map<String, dynamic> entry(String id, String type, String title, String body, {String? student}) => {
    'id': id,
    'eventType': type,
    'studentId': student,
    'title': title,
    'body': body,
    'createdAt': '2026-11-10T09:00:00Z',
    'openedAt': null,
  };

  setUp(() {
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(parent))
      ..on(
        'GET /me/children',
        (_) => const Reply.ok([
          {'id': 'st-1', 'firstName': 'Awa', 'lastName': 'Fictif', 'classId': 'c-1', 'className': '6e A'},
        ]),
      )
      ..on(
        'GET /me/notifications',
        (_) => Reply.ok([
          entry('n-3', 'DM_MESSAGE', 'Message du surveillant général', 'Merci de passer au bureau.', student: 'st-1'),
          entry('n-2', 'ANNOUNCEMENT', 'Annonce : Lycée Démo', 'Pas de cours lundi.'),
          entry('n-1', 'DM_MESSAGE', 'Message du surveillant général', longText),
        ]),
      )
      ..on('POST /notifications/*', (_) => const Reply(204));
  });

  Future<void> openInbox(WidgetTester tester) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(parent)), clock: () => now);
    await tester.tap(find.descendant(of: find.byType(NavigationBar), matching: find.text('Notifications')));
    await tester.pumpAndSettle();
  }

  Finder selectable(String text) => find.byWidgetPredicate((w) => w is SelectableText && w.data == text);

  Future<void> openEntry(WidgetTester tester, String id) async {
    await tester.tap(find.byKey(ValueKey('message-$id')));
    await tester.pumpAndSettle();
  }

  testWidgets('a long DM message shows two lines in the list and the full text when opened', (tester) async {
    await openInbox(tester);
    final inList = tester.widget<Text>(find.text(longText));
    expect(inList.maxLines, 2);

    await openEntry(tester, 'n-1');

    expect(selectable(longText), findsOneWidget, reason: 'whole and selectable');
    expect(server.sent('POST /notifications/n-1/opened'), hasLength(1));
  });

  testWidgets('an announcement shows its title and no child', (tester) async {
    await openInbox(tester);
    await openEntry(tester, 'n-2');

    expect(find.text('Annonce : Lycée Démo'), findsOneWidget);
    expect(selectable('Pas de cours lundi.'), findsOneWidget);
    expect(find.textContaining('Concerne'), findsNothing);
  });

  testWidgets('a student message names the child', (tester) async {
    await openInbox(tester);
    await openEntry(tester, 'n-3');

    expect(find.text('Concerne Awa'), findsOneWidget);
    expect(find.byKey(const ValueKey('open-history')), findsNothing, reason: 'not about attendance');
  });
}
