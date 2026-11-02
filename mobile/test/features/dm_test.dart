import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/dm/alert_screen.dart';
import 'package:schoollink/features/dm/student_history_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// Task 3.10: the DM's screens. Alerts and decisions (UC-D2, UC-D3, BR-18, D30, D31), the
/// student's history with justification of several records (UC-D1, BR-12, D26), cancellation
/// (UC-D6, BR-13, D27), summons (UC-D3, D31) and the student search (D35). The app never decides
/// (BR-16 to BR-19): every write follows the DM's tap. Names are made up.
void main() {
  late FakeServer server;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 2, 10);

  final fullDay = {
    'id': 'al-1',
    'studentId': 'st-1',
    'firstName': 'Awa',
    'lastName': 'Fictif',
    'matricule': 'TW-0001',
    'classId': 'c-1',
    'className': '6e A',
    'ruleType': 'FULL_DAY_ABSENCE',
    'threshold': 1,
    'asOf': '2026-10-30',
    'evidence': {
      'dates': ['2026-10-30'],
    },
    'status': 'OPEN',
    'raisedAt': '2026-10-30T15:00:00Z',
  };
  final unjustified = {
    ...fullDay,
    'id': 'al-2',
    'studentId': 'st-2',
    'firstName': 'Paul',
    'matricule': 'TW-0002',
    'ruleType': 'UNJUSTIFIED_ABSENCES',
    'threshold': 15,
    'evidence': {'periods': 16, 'from': '2026-09-07', 'to': '2026-10-30'},
  };

  final history = {
    'studentId': 'st-1',
    'matricule': 'TW-0001',
    'firstName': 'Awa',
    'lastName': 'Fictif',
    'classId': 'c-1',
    'className': '6e A',
    'from': '2026-08-04',
    'to': '2026-11-02',
    'totals': {'absentPeriods': 2, 'unjustifiedAbsentPeriods': 2, 'lateness': 1, 'unjustifiedLateness': 1},
    'days': [
      {
        'date': '2026-10-30',
        'absences': [
          {'recordId': 'abs-1', 'periodNumber': 1, 'startTime': '07:30:00', 'justified': false},
          {'recordId': 'abs-2', 'periodNumber': 2, 'startTime': '08:25:00', 'justified': false},
        ],
        'lateness': {'recordId': 'late-1', 'arrivalTime': '07:48:00', 'justified': false},
      },
    ],
  };

  setUp(() {
    var alerts = [fullDay, unjustified];
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => Reply.ok(alerts))
      ..on('GET /lateness', (_) => const Reply.ok({'date': '2026-11-02', 'startTime': '07:30:00', 'lateness': []}))
      ..on('GET /students/st-1/history', (_) => Reply.ok(history))
      ..on('POST /alerts/al-1/decision', (_) {
        alerts = [unjustified];
        return const Reply.ok({});
      });
  });

  Future<void> open(WidgetTester tester, {Map<String, dynamic>? user}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user ?? dm)), clock: () => now);
  }

  Future<void> tapAndSettle(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  Finder submit() => find.byKey(const ValueKey('sheet-submit'));
  bool enabled(WidgetTester tester, Finder button) => tester.widget<FilledButton>(button).onPressed != null;

  group('alerts (UC-D2)', () {
    testWidgets('the home lists the open alerts with what each rule found', (tester) async {
      await open(tester);

      expect(find.text('2 alertes à traiter'), findsOneWidget);
      expect(find.text('Fictif Awa'), findsOneWidget);
      expect(find.text('Absent toute la journée · 6e A'), findsOneWidget);
      expect(find.text('Absent à tous les cours le 30 oct.'), findsOneWidget);
      expect(find.text('16 cours manqués sans justification du 7 sept. au 30 oct.'), findsOneWidget);
      // the oldest first, as the server sends them (D30)
      expect(
        tester.getTopLeft(find.byKey(const ValueKey('alert-al-1'))).dy,
        lessThan(tester.getTopLeft(find.byKey(const ValueKey('alert-al-2'))).dy),
      );
      expect(server.sent('GET /alerts').single.query['status'], 'open');
      expect(server.requests.where((r) => r.method == 'POST'), isEmpty, reason: 'nothing is decided without the DM');
    });

    testWidgets('the home says so when there is nothing to decide, in English too (NFR-12)', (tester) async {
      final english = userJson(roles: ['DM'], language: 'EN');
      server
        ..on('GET /me', (_) => Reply.ok(english))
        ..on('GET /alerts', (_) => const Reply.ok([]));
      await open(tester, user: english);

      expect(find.text('No alert to decide'), findsWidgets);
    });

    testWidgets('dismissing needs the reason, then closes the alert (BR-18, D30)', (tester) async {
      await open(tester);
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
      expect(find.byType(AlertScreen), findsOneWidget);
      expect(find.text('Absent toute la journée'), findsOneWidget);

      await tapAndSettle(tester, find.byKey(const ValueKey('decide-dismiss')));
      expect(enabled(tester, submit()), isFalse, reason: 'D30: the reason is required');
      await tester.enterText(find.byKey(const ValueKey('decision-note')), 'Absence connue, voyage scolaire');
      await tester.pump();
      await tapAndSettle(tester, submit());

      final sent = server.sent('POST /alerts/al-1/decision').single;
      expect(sent.body, {'decision': 'DISMISS', 'note': 'Absence connue, voyage scolaire'});
      expect(find.byType(AlertScreen), findsNothing, reason: 'back to the list');
      expect(find.text('Décision enregistrée'), findsOneWidget);
      expect(find.text('1 alerte à traiter'), findsOneWidget);
      expect(find.byKey(const ValueKey('alert-al-1')), findsNothing);
    });

    testWidgets('summoning from an alert is one tap: the next school day, no reason needed (UC-D3, D31)', (
      tester,
    ) async {
      await open(tester);
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
      await tapAndSettle(tester, find.byKey(const ValueKey('decide-summon')));

      expect(find.text('Convoquer les parents de Awa'), findsOneWidget);
      expect(find.text('Prochain jour de classe, à l\'heure de la rentrée des cours'), findsOneWidget);
      expect(find.textContaining('pas le motif'), findsOneWidget, reason: 'D31: the reason stays with the DM');
      await tapAndSettle(tester, submit());

      expect(server.sent('POST /alerts/al-1/decision').single.body, {'decision': 'SUMMON'});
      expect(find.text('Parents convoqués'), findsOneWidget);
    });

    testWidgets('referring may go without a note; a disciplinary issue needs one', (tester) async {
      await open(tester);
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
      await tapAndSettle(tester, find.byKey(const ValueKey('decide-disciplinary')));
      expect(enabled(tester, submit()), isFalse);
      Navigator.of(tester.element(submit())).pop();
      await tester.pumpAndSettle();

      await tapAndSettle(tester, find.byKey(const ValueKey('decide-refer')));
      expect(enabled(tester, submit()), isTrue);
      await tapAndSettle(tester, submit());

      expect(server.sent('POST /alerts/al-1/decision').single.body, {'decision': 'REFER'});
    });

    testWidgets("a refusal stays on the sheet in the server's words, and nothing is closed", (tester) async {
      server.on(
        'POST /alerts/al-1/decision',
        (_) => Reply.error(409, 'alert.already_decided', 'Cette alerte a déjà été traitée.'),
      );
      await open(tester);
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
      await tapAndSettle(tester, find.byKey(const ValueKey('decide-refer')));
      await tapAndSettle(tester, submit());

      expect(find.byKey(const ValueKey('sheet-error')), findsOneWidget);
      expect(find.text('Cette alerte a déjà été traitée.'), findsOneWidget);
      expect(find.byType(AlertScreen), findsOneWidget);
    });

    testWidgets("the alert leads to the student's history (UC-D8)", (tester) async {
      await open(tester);
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-history')));

      expect(find.byType(StudentHistoryScreen), findsOneWidget);
      expect(find.widgetWithText(AppBar, 'Fictif Awa'), findsOneWidget);
      expect(find.text('6e A, matricule TW-0001'), findsOneWidget);
    });
  });

  group('history (UC-D8)', () {
    Future<void> openHistory(WidgetTester tester) async {
      await open(tester);
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
      await tapAndSettle(tester, find.byKey(const ValueKey('alert-history')));
    }

    testWidgets('several records are justified at once; an illness takes no note (BR-12, D26, NFR-10)', (tester) async {
      var justified = false;
      server
        ..on('POST /justifications', (_) {
          justified = true;
          return const Reply(201, {});
        })
        ..on('GET /students/st-1/history', (_) => Reply.ok(history));
      await openHistory(tester);
      expect(find.byKey(const ValueKey('justify-selected')), findsNothing);

      await tapAndSettle(tester, find.byKey(const ValueKey('record-abs-1')));
      await tapAndSettle(tester, find.byKey(const ValueKey('record-late-1')));
      expect(find.text('Justifier 2 éléments'), findsOneWidget);

      await tapAndSettle(tester, find.byKey(const ValueKey('justify-selected')));
      expect(enabled(tester, submit()), isFalse, reason: 'a reason first');
      await tapAndSettle(tester, find.byKey(const ValueKey('reason-family')));
      expect(find.byKey(const ValueKey('justification-note')), findsOneWidget);
      await tapAndSettle(tester, find.byKey(const ValueKey('reason-illness')));
      expect(find.byKey(const ValueKey('justification-note')), findsNothing);
      expect(find.byKey(const ValueKey('illness-no-note')), findsOneWidget);
      await tapAndSettle(tester, submit());

      expect(justified, isTrue);
      expect(server.sent('POST /justifications').single.body, {
        'reason': 'ILLNESS',
        'absenceIds': ['abs-1'],
        'latenessIds': ['late-1'],
      });
      expect(find.text('2 éléments justifiés'), findsOneWidget);
      expect(find.byKey(const ValueKey('justify-selected')), findsNothing, reason: 'the selection is cleared');
      expect(server.sent('GET /students/st-1/history'), hasLength(2), reason: 'read again');
    });

    testWidgets('a lateness entered by mistake is cancelled with the reason (UC-D6, BR-13, D27)', (tester) async {
      server.on('POST /lateness/late-1/cancel', (_) => const Reply.ok({}));
      await openHistory(tester);

      await tapAndSettle(tester, find.byKey(const ValueKey('record-menu-late-1')));
      await tapAndSettle(tester, find.text('Annuler cet enregistrement'));
      expect(find.text('Annuler cet enregistrement ?'), findsOneWidget);
      expect(find.text('Retard, Arrivée à 07:48'), findsOneWidget);
      expect(enabled(tester, submit()), isFalse, reason: 'BR-13: the reason is mandatory');

      await tester.enterText(find.byKey(const ValueKey('cancel-reason')), 'Mauvais élève saisi');
      await tester.pump();
      await tapAndSettle(tester, submit());

      expect(server.sent('POST /lateness/late-1/cancel').single.body, {'reason': 'Mauvais élève saisi'});
      expect(server.sent('POST /records/abs-1/cancel'), isEmpty);
      expect(find.text('Enregistrement annulé'), findsOneWidget);
    });

    testWidgets('an absence is cancelled on its own route', (tester) async {
      server.on('POST /records/abs-2/cancel', (_) => const Reply.ok({}));
      await openHistory(tester);

      await tapAndSettle(tester, find.byKey(const ValueKey('record-menu-abs-2')));
      await tapAndSettle(tester, find.text('Annuler cet enregistrement'));
      await tester.enterText(find.byKey(const ValueKey('cancel-reason')), 'Présent en classe');
      await tester.pump();
      await tapAndSettle(tester, submit());

      expect(server.sent('POST /records/abs-2/cancel').single.body, {'reason': 'Présent en classe'});
    });

    testWidgets('a summons from the history needs its reason and confirms the meeting (UC-D3, D31)', (tester) async {
      server.on(
        'POST /summons',
        (_) => const Reply(201, {'id': 'sm-1', 'meetingDate': '2026-11-03', 'meetingTime': '07:30:00'}),
      );
      await openHistory(tester);

      await tapAndSettle(tester, find.byKey(const ValueKey('history-summon')));
      expect(find.text('Motif'), findsOneWidget);
      expect(enabled(tester, submit()), isFalse);
      await tester.enterText(find.byKey(const ValueKey('summons-reason')), 'Absences répétées');
      await tester.pump();
      await tapAndSettle(tester, submit());

      expect(server.sent('POST /summons').single.body, {'studentId': 'st-1', 'reason': 'Absences répétées'});
      expect(find.text('Parents convoqués le mardi 3 novembre à 07:30'), findsOneWidget);
    });
  });

  group('finding a student (D35)', () {
    testWidgets("the search finds a student of the DM's classes and opens the history", (tester) async {
      server.on(
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
          {
            'id': 'st-9',
            'matricule': 'TW-0009',
            'firstName': 'Bruno',
            'lastName': 'Exemple',
            'classId': 'c-1',
            'className': '6e A',
          },
        ]),
      );
      await open(tester);

      await tapAndSettle(tester, find.byKey(const ValueKey('open-search')));
      await tester.enterText(find.byKey(const ValueKey('dm-search')), 'awa');
      await tester.pumpAndSettle();
      expect(find.text('1 élève'), findsOneWidget);
      expect(find.byKey(const ValueKey('student-st-9')), findsNothing);

      await tapAndSettle(tester, find.byKey(const ValueKey('student-st-1')));
      expect(find.byType(StudentHistoryScreen), findsOneWidget);
      expect(server.sent('GET /students/st-1/history'), hasLength(1));
    });

    testWidgets("today's lateness leads to the student's history", (tester) async {
      server.on(
        'GET /lateness',
        (_) => const Reply.ok({
          'date': '2026-11-02',
          'startTime': '07:30:00',
          'lateness': [
            {
              'id': 'late-9',
              'studentId': 'st-1',
              'firstName': 'Awa',
              'lastName': 'Fictif',
              'classId': 'c-1',
              'className': '6e A',
              'arrivalTime': '07:48:00',
              'recordedAt': '2026-11-02T06:48:00Z',
              'recordedBy': 'user-2',
              'justified': false,
            },
          ],
        }),
      );
      await open(tester);

      await tapAndSettle(tester, find.byKey(const ValueKey('lateness-late-9')));
      expect(find.byType(StudentHistoryScreen), findsOneWidget);
    });
  });
}
