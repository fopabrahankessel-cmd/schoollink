import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-D5, D37: the Discipline tab of the DM's student page. The DM records an incident (category,
/// date at most 30 days back, measure, what happened), closes it with a note or cancels it with a
/// reason. The alert decision "disciplinary" asks for the category and measure too. Nothing is sent
/// without the DM's tap (BR-16 to BR-19). Names are made up.
void main() {
  late FakeServer server;
  late List<Map<String, dynamic>> records;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 2, 10);

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
    'asOf': '2026-10-30',
    'evidence': {
      'dates': ['2026-10-30'],
    },
    'status': 'OPEN',
    'raisedAt': '2026-10-30T15:00:00Z',
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
    'totals': {'absentPeriods': 0, 'unjustifiedAbsentPeriods': 0, 'lateness': 0, 'unjustifiedLateness': 0},
    'days': <Object>[],
  };
  Map<String, dynamic> record({String id = 'd-1', String status = 'OPEN'}) => {
    'id': id,
    'studentId': 'st-1',
    'alertId': null,
    'category': 'FIGHT',
    'measure': 'DETENTION',
    'occurredOn': '2026-10-30',
    'description': 'Bagarre à la récréation',
    'status': status,
    'closeNote': null,
    'recordedByName': 'Marie Surveillante',
    'recordedAt': '2026-10-30T11:00:00Z',
    'closedAt': null,
  };

  setUp(() {
    records = [record()];
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => Reply.ok([alert]))
      ..on('GET /lateness', (_) => const Reply.ok({'date': '2026-11-02', 'startTime': '07:30:00', 'lateness': []}))
      ..on('GET /students/st-1/history', (_) => Reply.ok(history))
      ..on('GET /students/st-1/disciplinary-records', (_) => Reply.ok(records))
      ..on('POST /students/st-1/disciplinary-records', (_) {
        records = [record(id: 'd-2'), ...records];
        return Reply(201, record(id: 'd-2'));
      })
      ..on('POST /disciplinary-records/d-1/close', (_) => Reply.ok(record(status: 'CLOSED')))
      ..on('POST /disciplinary-records/d-1/cancel', (_) => const Reply(204))
      ..on('POST /alerts/al-1/decision', (_) => const Reply.ok({}));
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

  Future<void> openDiscipline(WidgetTester tester, {Map<String, dynamic>? user, String tab = 'Discipline'}) async {
    await open(tester, user: user);
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-history')));
    await tapAndSettle(tester, find.widgetWithText(Tab, tab));
  }

  Finder submit() => find.byKey(const ValueKey('sheet-submit'));
  bool enabled(WidgetTester tester) => tester.widget<FilledButton>(submit()).onPressed != null;

  testWidgets('the student page has History and Discipline tabs (UC-D5)', (tester) async {
    await open(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-history')));
    expect(find.widgetWithText(Tab, 'Historique'), findsOneWidget);

    await tapAndSettle(tester, find.widgetWithText(Tab, 'Discipline'));

    expect(find.text('Bagarre'), findsOneWidget);
    expect(find.text('Bagarre à la récréation'), findsOneWidget);
    expect(find.textContaining('Retenue'), findsOneWidget);
    expect(find.textContaining('30 oct.'), findsOneWidget);
    expect(find.text('En cours'), findsOneWidget);
    expect(find.textContaining('Marie Surveillante'), findsOneWidget);
  });

  testWidgets('an empty Discipline tab says what to do', (tester) async {
    records = [];
    await openDiscipline(tester);

    expect(find.text('Aucun incident enregistré.'), findsOneWidget);
    expect(find.widgetWithText(FloatingActionButton, 'Signaler un incident'), findsOneWidget);
  });

  testWidgets('recording an incident sends category, date, measure and text', (tester) async {
    await openDiscipline(tester);
    await tapAndSettle(tester, find.widgetWithText(FloatingActionButton, 'Signaler un incident'));

    await tapAndSettle(tester, find.widgetWithText(ChoiceChip, 'Bagarre'));
    await tapAndSettle(tester, find.byKey(const ValueKey('incident-measure')));
    await tapAndSettle(tester, find.text('Retenue').last);
    await tester.enterText(find.byKey(const ValueKey('incident-description')), '  Bagarre à la récréation ');
    await tester.pumpAndSettle();
    await tapAndSettle(tester, submit());

    expect(server.sent('POST /students/st-1/disciplinary-records').single.body, {
      'category': 'FIGHT',
      'measure': 'DETENTION',
      'occurredOn': '2026-11-02',
      'description': 'Bagarre à la récréation',
    });
    expect(find.text('Incident enregistré.'), findsOneWidget);
    expect(server.sent('GET /students/st-1/disciplinary-records'), hasLength(2), reason: 'read again');
  });

  testWidgets('the save button stays disabled for a blank description or no category', (tester) async {
    await openDiscipline(tester);
    await tapAndSettle(tester, find.widgetWithText(FloatingActionButton, 'Signaler un incident'));
    expect(enabled(tester), isFalse);

    await tester.enterText(find.byKey(const ValueKey('incident-description')), 'Insolence en classe');
    await tester.pumpAndSettle();
    expect(enabled(tester), isFalse, reason: 'no category yet');

    await tapAndSettle(tester, find.widgetWithText(ChoiceChip, 'Insolence'));
    await tester.enterText(find.byKey(const ValueKey('incident-description')), '    ');
    await tester.pumpAndSettle();
    expect(enabled(tester), isFalse, reason: 'spaces only');
  });

  testWidgets('the date picker allows today back to 30 days only', (tester) async {
    await openDiscipline(tester);
    await tapAndSettle(tester, find.widgetWithText(FloatingActionButton, 'Signaler un incident'));
    await tapAndSettle(tester, find.byKey(const ValueKey('incident-date')));

    final picker = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
    expect(picker.firstDate, DateTime(2026, 10, 3));
    expect(picker.lastDate, DateTime(2026, 11, 2));
  });

  testWidgets('a record can be closed with a note', (tester) async {
    await openDiscipline(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('incident-d-1')));
    await tapAndSettle(tester, find.text('Clôturer'));
    await tester.enterText(find.byKey(const ValueKey('close-note')), 'Parents reçus');
    await tester.pumpAndSettle();
    await tapAndSettle(tester, submit());

    expect(server.sent('POST /disciplinary-records/d-1/close').single.body, {'note': 'Parents reçus'});
    expect(find.text('Incident clôturé.'), findsOneWidget);
  });

  testWidgets('a record entered by mistake is cancelled with a reason', (tester) async {
    await openDiscipline(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('incident-d-1')));
    await tapAndSettle(tester, find.text("Annuler l'incident"));
    expect(enabled(tester), isFalse);
    await tester.enterText(find.byKey(const ValueKey('cancel-reason')), "Erreur d'élève");
    await tester.pumpAndSettle();
    await tapAndSettle(tester, submit());

    expect(server.sent('POST /disciplinary-records/d-1/cancel').single.body, {'reason': "Erreur d'élève"});
    expect(find.text('Incident annulé.'), findsOneWidget);
  });

  testWidgets('a closed record can no longer be closed', (tester) async {
    records = [record(status: 'CLOSED')];
    await openDiscipline(tester);
    expect(find.text('Clôturé'), findsOneWidget);
    await tapAndSettle(tester, find.byKey(const ValueKey('incident-d-1')));

    expect(find.text('Clôturer'), findsNothing);
    expect(find.text("Annuler l'incident"), findsOneWidget);
  });

  testWidgets('the disciplinary decision on an alert asks for category and measure (D37)', (tester) async {
    await open(tester);
    await tapAndSettle(tester, find.byKey(const ValueKey('alert-al-1')));
    await tapAndSettle(tester, find.text('Ouvrir un dossier disciplinaire'));
    await tester.enterText(find.byKey(const ValueKey('decision-note')), 'Absences répétées');
    await tester.pumpAndSettle();
    expect(enabled(tester), isFalse, reason: 'no category yet');

    await tapAndSettle(tester, find.widgetWithText(ChoiceChip, 'Absentéisme'));
    await tapAndSettle(tester, find.byKey(const ValueKey('incident-measure')));
    await tapAndSettle(tester, find.text('Avertissement').last);
    await tapAndSettle(tester, submit());

    expect(server.sent('POST /alerts/al-1/decision').single.body, {
      'decision': 'DISCIPLINARY',
      'note': 'Absences répétées',
      'category': 'ABSENTEEISM',
      'measure': 'WARNING',
    });
  });

  testWidgets('the Discipline tab reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await openDiscipline(tester, user: english);

    expect(find.text('Fight'), findsOneWidget);
    expect(find.textContaining('Detention'), findsOneWidget);
    expect(find.text('Open'), findsOneWidget);
    expect(find.widgetWithText(FloatingActionButton, 'Record an incident'), findsOneWidget);
  });
}
