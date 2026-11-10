import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-D4, D38: the DM writes to the guardians of one student or one class of their blocks. Before
/// sending, a preview gives who gets it (by the app, by SMS, not reached), the SMS units, the cost
/// and the SMS left this month; sending twice sends once (the id is made once per composer). Names
/// are made up.
void main() {
  late FakeServer server;
  late List<Map<String, dynamic>> sentList;
  late List<Map<String, dynamic>> previews;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);
  final uuid = RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$');

  final history = {
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
  };
  final alert = {
    'id': 'al-1',
    'studentId': 'st-1',
    'firstName': 'Awa',
    'lastName': 'Fictif',
    'matricule': 'TW-0001',
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
  Map<String, dynamic> sentMessage() => {
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
  Map<String, dynamic> preview({bool sms = false, bool unicode = false, bool overCap = false}) => {
    'recipients': 4,
    'byApp': 2,
    'bySms': sms ? 2 : 0,
    'unreached': sms ? 0 : 2,
    'smsUnits': sms ? 1 : 0,
    'smsUnicode': unicode,
    'smsLeft': overCap ? 1 : 120,
    'estimatedCost': sms ? 362 : 0,
    'overCap': overCap,
    'smsSample': sms ? 'SchoolLink - SG Lycée Démo : Réunion vendredi.' : null,
  };

  setUp(() {
    sentList = [sentMessage()];
    previews = [];
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => Reply.ok([alert]))
      ..on('GET /lateness', (_) => const Reply.ok({'date': '2026-11-10', 'startTime': '07:30:00', 'lateness': []}))
      ..on('GET /students/st-1/history', (_) => Reply.ok(history))
      ..on('GET /students/st-1/disciplinary-records', (_) => const Reply.ok([]))
      ..on('GET /messages', (_) => Reply.ok(sentList))
      ..on(
        'GET /messages/classes',
        (_) => const Reply.ok([
          {'classId': 'c-1', 'className': '6e A', 'students': 30},
          {'classId': 'c-2', 'className': '5e B', 'students': 28},
        ]),
      )
      ..on('POST /messages/preview', (request) {
        final body = request.body! as Map;
        return Reply.ok(preview(sms: body['sms'] == true));
      })
      ..on('POST /messages', (request) {
        final body = request.body! as Map;
        return Reply(201, {'id': body['id']});
      });
  });

  Future<void> tapAndSettle(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  Future<void> open(WidgetTester tester, {Map<String, dynamic>? user}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user ?? dm)), clock: () => now);
  }

  Future<void> openMessages(WidgetTester tester, {Map<String, dynamic>? user}) async {
    await open(tester, user: user);
    await tapAndSettle(tester, find.byKey(const ValueKey('dm-messages-entry')));
  }

  /// Waits the 400 ms after the last change before the preview is asked for.
  Future<void> typeAndWait(WidgetTester tester, String text) async {
    await tester.enterText(find.byKey(const ValueKey('message-body')), text);
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();
  }

  Future<void> openClassComposer(WidgetTester tester) async {
    await openMessages(tester);
    await tapAndSettle(tester, find.widgetWithText(FloatingActionButton, 'Nouveau message'));
    await tapAndSettle(tester, find.byKey(const ValueKey('message-class')));
    await tapAndSettle(tester, find.text('6e A').last);
  }

  Finder send() => find.byKey(const ValueKey('message-send'));
  bool enabled(WidgetTester tester) => tester.widget<FilledButton>(send()).onPressed != null;
  Map<String, dynamic> withoutId(Object? body) => Map.of(body! as Map<String, dynamic>)..remove('id');

  testWidgets('the DM home leads to Messages, which lists sent messages with delivery', (tester) async {
    await openMessages(tester);

    expect(find.text('6e A'), findsOneWidget);
    expect(find.text('Réunion des parents vendredi à 15 h.'), findsOneWidget);
    expect(find.text('4 destinataires'), findsOneWidget);
    expect(find.text('1 ouvert'), findsOneWidget);
    expect(find.text('2 reçus'), findsOneWidget);
  });

  testWidgets('an empty Messages screen says what to do', (tester) async {
    sentList = [];
    await openMessages(tester);

    expect(find.text('Aucun message envoyé.'), findsOneWidget);
    expect(find.widgetWithText(FloatingActionButton, 'Nouveau message'), findsOneWidget);
  });

  testWidgets('a class message: SMS off by default, live counts, sends once', (tester) async {
    await openClassComposer(tester);
    final sms = tester.widget<SwitchListTile>(find.byKey(const ValueKey('message-sms')));
    expect(sms.value, isFalse);

    await typeAndWait(tester, '  Réunion des parents vendredi à 15 h. ');
    expect(find.text("4 parents : 2 par l'app, 2 sans l'app"), findsOneWidget);
    expect(server.sent('POST /messages/preview'), hasLength(1), reason: 'one preview after the pause');

    await tapAndSettle(tester, send());
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(
      find.descendant(of: find.byType(AlertDialog), matching: find.text("4 parents : 2 par l'app, 2 sans l'app")),
      findsOneWidget,
    );
    await tapAndSettle(tester, find.byKey(const ValueKey('message-confirm')));

    final posted = server.sent('POST /messages').single.body! as Map<String, dynamic>;
    expect(posted['id'], matches(uuid));
    expect(withoutId(posted), {
      'classId': 'c-1',
      'body': 'Réunion des parents vendredi à 15 h.',
      'sms': false,
      'plainSms': false,
    });
    expect(find.text('Message envoyé.'), findsOneWidget);
    expect(server.sent('GET /messages'), hasLength(2), reason: 'the list is read again');
  });

  testWidgets('a student message from the student page has SMS on by default', (tester) async {
    await open(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-history')));
    await tapAndSettle(tester, find.byKey(const ValueKey('history-message')));

    expect(find.text('Écrire aux parents'), findsOneWidget);
    expect(find.widgetWithText(Chip, 'Fictif Awa'), findsOneWidget);
    expect(tester.widget<SwitchListTile>(find.byKey(const ValueKey('message-sms'))).value, isTrue);

    await typeAndWait(tester, 'Merci de passer au bureau du surveillant.');
    expect(find.text("4 parents : 2 par l'app, 2 par SMS"), findsOneWidget);
    await tapAndSettle(tester, send());
    await tapAndSettle(tester, find.byKey(const ValueKey('message-confirm')));

    expect(withoutId(server.sent('POST /messages').single.body), {
      'studentId': 'st-1',
      'body': 'Merci de passer au bureau du surveillant.',
      'sms': true,
      'plainSms': false,
    });
    expect(find.text('Message envoyé.'), findsOneWidget);
  });

  testWidgets('the SMS line shows cost and SMS left, and over the cap blocks sending', (tester) async {
    await openClassComposer(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('message-sms')));
    await typeAndWait(tester, 'Réunion vendredi.');

    expect(find.text('≈ 362 FCFA · 120 SMS restants ce mois-ci'), findsOneWidget);
    expect(find.text('1 SMS par parent'), findsOneWidget);
    expect(find.text('SchoolLink - SG Lycée Démo : Réunion vendredi.'), findsOneWidget);
    expect(enabled(tester), isTrue);

    server.on('POST /messages/preview', (_) => Reply.ok(preview(sms: true, overCap: true)));
    await typeAndWait(tester, 'Réunion vendredi à 15 h.');

    expect(find.textContaining('Plus assez de SMS ce mois-ci'), findsOneWidget);
    expect(enabled(tester), isFalse);
  });

  testWidgets('with SMS on but every parent on the app, no SMS is shown or counted', (tester) async {
    server.on(
      'POST /messages/preview',
      (_) => Reply.ok({...preview(sms: true), 'byApp': 4, 'bySms': 0, 'estimatedCost': 0}),
    );
    await openClassComposer(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('message-sms')));
    await typeAndWait(tester, 'Réunion vendredi.');

    expect(find.text('Le SMS envoyé'), findsNothing);
    expect(find.textContaining('FCFA'), findsNothing);
  });

  testWidgets('the SMS left are written with the thousands separator', (tester) async {
    server.on('POST /messages/preview', (_) => Reply.ok({...preview(sms: true), 'smsLeft': 1957}));
    await openClassComposer(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('message-sms')));
    await typeAndWait(tester, 'Réunion vendredi.');

    expect(
      find.text('≈ 362 FCFA · ${NumberFormat.decimalPattern('fr').format(1957)} SMS restants ce mois-ci'),
      findsOneWidget,
    );
  });

  testWidgets('special letters: the notice and the replace switch', (tester) async {
    server.on('POST /messages/preview', (request) {
      final body = request.body! as Map;
      previews.add(Map.of(body.cast<String, dynamic>()));
      return Reply.ok(preview(sms: true, unicode: body['plainSms'] != true));
    });
    await openClassComposer(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('message-sms')));
    await typeAndWait(tester, 'Fête de fin d’année');

    expect(find.textContaining('lettres spéciales'), findsWidgets);
    await tapAndSettle(tester, find.byKey(const ValueKey('message-plain')));
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(previews.last['plainSms'], isTrue);
  });

  testWidgets('send stays disabled for a blank text', (tester) async {
    await openClassComposer(tester);
    expect(enabled(tester), isFalse, reason: 'nothing written');

    await typeAndWait(tester, '    ');
    expect(enabled(tester), isFalse, reason: 'spaces only');
    expect(server.sent('POST /messages/preview'), isEmpty, reason: 'nothing to preview');
  });

  testWidgets('a second tap during sending sends once', (tester) async {
    final answer = Completer<Reply>();
    server.on('POST /messages', (_) => answer.future);
    await openClassComposer(tester);
    await typeAndWait(tester, 'Réunion vendredi.');

    await tapAndSettle(tester, send());
    await tester.tap(find.byKey(const ValueKey('message-confirm')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(enabled(tester), isFalse, reason: 'sending');
    await tester.tap(send(), warnIfMissed: false);
    await tester.pump();

    answer.complete(const Reply(201, {'id': 'x'}));
    await tester.pumpAndSettle();
    expect(server.sent('POST /messages'), hasLength(1));
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await openMessages(tester, user: english);

    expect(find.text('4 recipients'), findsOneWidget);
    expect(find.text('1 opened'), findsOneWidget);
    await tapAndSettle(tester, find.widgetWithText(FloatingActionButton, 'New message'));
    expect(find.text('Also by SMS for parents without the app'), findsOneWidget);
  });
}
