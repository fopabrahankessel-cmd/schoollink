import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/dm/settings/settings_models.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-D7, D28, D41: the alert thresholds, the start of school and the SMS digest times, on
/// Android as on the web, with the same bounds: start 05:00 to 12:00, digests 06:00 to 20:00, the
/// second after the first, each count within its rule's bounds.
void main() {
  late FakeServer server;
  late Map<String, dynamic> settings;
  late Reply saveReply;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  Map<String, dynamic> rule(String type, int count, {bool active = true, int min = 1, int max = 5}) => {
    'type': type,
    'count': count,
    'active': active,
    'min': min,
    'max': max,
  };

  setUp(() {
    saveReply = const Reply.ok({});
    settings = {
      'startTime': '07:30:00',
      'digestTimes': ['12:00:00', '18:00:00'],
      'rules': [
        rule('FULL_DAY_ABSENCE', 1),
        rule('CONSECUTIVE_LATENESS', 3),
        rule('UNJUSTIFIED_ABSENCES', 10, active: false, max: 10),
      ],
    };
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
      ..on('GET /settings/thresholds', (_) => Reply.ok(settings))
      ..on('PUT /settings/thresholds', (_) => saveReply);
  });

  Future<void> tap(WidgetTester tester, Finder finder) async {
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
    await tap(tester, find.text(user?['language'] == 'EN' ? 'More' : 'Plus'));
    await tap(tester, find.byKey(const ValueKey('more-settings')));
  }

  bool saveEnabled(WidgetTester tester) =>
      tester.widget<FilledButton>(find.byKey(const ValueKey('settings-save'))).onPressed != null;

  testWidgets('the current settings are shown', (tester) async {
    await open(tester);

    expect(
      find.descendant(of: find.byKey(const ValueKey('settings-start')), matching: find.text('07:30')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: find.byKey(const ValueKey('settings-digest-0')), matching: find.text('12:00')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: find.byKey(const ValueKey('settings-digest-1')), matching: find.text('18:00')),
      findsOneWidget,
    );
    expect(find.text('Une alerte quand un élève manque tous les cours d\'une journée.'), findsOneWidget);
    expect(tester.widget<Switch>(find.byKey(const ValueKey('rule-active-UNJUSTIFIED_ABSENCES'))).value, isFalse);
  });

  testWidgets('saving sends the whole form', (tester) async {
    await open(tester);

    await tap(tester, find.byKey(const ValueKey('rule-plus-FULL_DAY_ABSENCE')));
    expect(find.text('Une alerte quand un élève manque tous les cours 2 jours de suite.'), findsOneWidget);
    await tap(tester, find.byKey(const ValueKey('settings-save')));

    expect(server.sent('PUT /settings/thresholds').single.body, {
      'startTime': '07:30',
      'digestTimes': ['12:00', '18:00'],
      'rules': [
        {'type': 'FULL_DAY_ABSENCE', 'count': 2, 'active': true},
        {'type': 'CONSECUTIVE_LATENESS', 'count': 3, 'active': true},
        {'type': 'UNJUSTIFIED_ABSENCES', 'count': 10, 'active': false},
      ],
    });
    expect(find.text('Réglages enregistrés.'), findsOneWidget);
  });

  testWidgets('an out-of-order digest blocks saving', (tester) async {
    settings['digestTimes'] = ['12:00:00', '06:30:00'];
    await open(tester);

    expect(find.text('Après le premier récapitulatif'), findsOneWidget);
    expect(saveEnabled(tester), isFalse);
  });

  testWidgets('the second digest is added and removed', (tester) async {
    settings['digestTimes'] = ['12:00:00'];
    await open(tester);

    expect(find.byKey(const ValueKey('settings-digest-1')), findsNothing);
    await tap(tester, find.byKey(const ValueKey('settings-add-digest')));
    expect(
      find.descendant(of: find.byKey(const ValueKey('settings-digest-1')), matching: find.text('18:00')),
      findsOneWidget,
    );
    await tap(tester, find.byKey(const ValueKey('settings-remove-digest')));
    expect(find.byKey(const ValueKey('settings-digest-1')), findsNothing);
  });

  testWidgets('a count stays within its bounds', (tester) async {
    await open(tester);

    expect(tester.widget<IconButton>(find.byKey(const ValueKey('rule-minus-FULL_DAY_ABSENCE'))).onPressed, isNull);
    for (var i = 0; i < 4; i++) {
      await tap(tester, find.byKey(const ValueKey('rule-plus-FULL_DAY_ABSENCE')));
    }
    expect(tester.widget<IconButton>(find.byKey(const ValueKey('rule-plus-FULL_DAY_ABSENCE'))).onPressed, isNull);
    expect(
      tester.widget<IconButton>(find.byKey(const ValueKey('rule-plus-UNJUSTIFIED_ABSENCES'))).onPressed,
      isNull,
      reason: 'a rule switched off is not counted',
    );
  });

  testWidgets("a server refusal is shown in the server's words", (tester) async {
    saveReply = const Reply(400, {'code': 'validation.range', 'message': "L'heure de début doit être entre 05:00 et 12:00."});
    await open(tester);

    await tap(tester, find.byKey(const ValueKey('settings-save')));
    expect(find.text("L'heure de début doit être entre 05:00 et 12:00."), findsOneWidget);
  });

  test('the bounds of the form', () {
    SettingsDraft draft(String start, List<String> digests) =>
        SettingsDraft(startTime: start, digestTimes: digests, rules: const []);
    expect(draft('07:30', ['12:00', '18:00']).valid, isTrue);
    expect(draft('04:59', ['12:00']).valid, isFalse);
    expect(draft('12:01', ['12:00']).valid, isFalse);
    expect(draft('07:30', ['05:59']).valid, isFalse);
    expect(draft('07:30', ['20:01']).valid, isFalse);
    expect(draft('07:30', ['12:00', '12:00']).valid, isFalse, reason: 'the second after the first');
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, user: english);

    expect(find.text('Alert rules'), findsOneWidget);
    expect(find.text('An alert when a student arrives late 3 school days running.'), findsOneWidget);
  });
}
