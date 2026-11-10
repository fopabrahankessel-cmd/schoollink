import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-D10, D40: the DM's day of attendance, period by period: recorded with its absentees, not
/// recorded (information only, BR-4) or upcoming, and the day's lateness under each class. From here
/// the DM justifies several records or cancels one; cancelled rows are listed, struck through, never
/// counted (BR-15). Names are made up.
void main() {
  late FakeServer server;
  late Map<String, dynamic> day;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  Map<String, dynamic> absence(String id, String first, String state) => {
    'recordId': id,
    'studentId': 'st-$id',
    'firstName': first,
    'lastName': 'Fictif',
    'matricule': 'TW-$id',
    'state': state,
  };
  Map<String, dynamic> period(int number, String status, {List<Map<String, dynamic>> absences = const []}) => {
    'periodId': 'p-$number',
    'number': number,
    'startTime': ['07:30:00', '08:30:00', '10:15:00'][number - 1],
    'endTime': ['08:25:00', '09:25:00', '11:10:00'][number - 1],
    'subject': 'Maths',
    'teacherName': 'Paul Enseignant',
    'status': status,
    'absentCount': absences.where((a) => a['state'] != 'CANCELLED').length,
    'absences': absences,
  };
  Map<String, dynamic> schoolDay() => {
    'date': '2026-11-10',
    'schoolDay': true,
    'totals': {'notRecorded': 1, 'absentStudents': 2, 'lateness': 1},
    'classes': [
      {
        'classId': 'c-1',
        'className': '6e A',
        'periods': [
          period(
            1,
            'RECORDED',
            absences: [absence('a1', 'Awa', 'UNJUSTIFIED'), absence('a2', 'Bruno', 'UNJUSTIFIED')],
          ),
          period(2, 'NOT_RECORDED'),
          period(3, 'UPCOMING'),
        ],
        'lateness': [
          {
            'recordId': 'l1',
            'studentId': 'st-l1',
            'firstName': 'Chantal',
            'lastName': 'Fictif',
            'matricule': 'TW-l1',
            'arrivalTime': '07:52:00',
            'state': 'UNJUSTIFIED',
          },
        ],
      },
    ],
  };

  setUp(() {
    day = schoolDay();
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => const Reply.ok([]))
      ..on('GET /lateness', (_) => const Reply.ok({'date': '2026-11-10', 'startTime': '07:30:00', 'lateness': []}))
      ..on('GET /attendance/day', (_) => Reply.ok(day))
      ..on('POST /justifications', (_) => const Reply(201, {'id': 'j-1'}))
      ..on('POST /records/a1/cancel', (_) => const Reply.ok());
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
    await tap(tester, find.byKey(const ValueKey('dm-day-entry')));
  }

  Future<void> openFirstPeriod(WidgetTester tester) =>
      tap(tester, find.byKey(const ValueKey('day-period-1-6e A')));

  testWidgets("the DM home leads to today's attendance", (tester) async {
    await open(tester);

    expect(find.text('Présences du jour'), findsWidgets);
    expect(server.sent('GET /attendance/day').single.query, isEmpty, reason: 'today is the server\'s today');
    expect(find.text('6e A'), findsOneWidget);
  });

  testWidgets('periods show their status', (tester) async {
    await open(tester);

    expect(find.text('Saisie · 2 absents'), findsOneWidget);
    expect(find.text('Non saisie'), findsOneWidget);
    expect(find.text('À venir'), findsOneWidget);
    expect(find.textContaining('P1 · 07:30 · Maths · Paul Enseignant'), findsOneWidget);
  });

  testWidgets('a recorded period opens its absentees and justifies several', (tester) async {
    await open(tester);
    await openFirstPeriod(tester);

    await tap(tester, find.byKey(const ValueKey('day-absence-a1')));
    await tap(tester, find.byKey(const ValueKey('day-absence-a2')));
    await tap(tester, find.byKey(const ValueKey('day-justify')));
    expect(find.text('Justifier 2 éléments'), findsWidgets);
    await tap(tester, find.byKey(const ValueKey('reason-illness')));
    await tap(tester, find.byKey(const ValueKey('sheet-submit')));

    final body = server.sent('POST /justifications').single.body! as Map;
    expect(body['absenceIds'], ['a1', 'a2']);
    expect(body['latenessIds'], isEmpty);
    expect(server.sent('GET /attendance/day'), hasLength(2), reason: 'the day is read again');
  });

  testWidgets('an absence is cancelled with a reason', (tester) async {
    await open(tester);
    await openFirstPeriod(tester);

    await tap(tester, find.byKey(const ValueKey('day-record-menu-a1')));
    await tap(tester, find.text('Annuler cet enregistrement'));
    await tester.enterText(find.byKey(const ValueKey('cancel-reason')), 'Erreur de saisie');
    await tester.pumpAndSettle();
    await tap(tester, find.byKey(const ValueKey('sheet-submit')));

    expect(server.sent('POST /records/a1/cancel').single.body, {'reason': 'Erreur de saisie'});
    expect(server.sent('GET /attendance/day'), hasLength(2));
  });

  testWidgets('a ticked absence that is then cancelled leaves the selection', (tester) async {
    await open(tester);
    await openFirstPeriod(tester);

    await tap(tester, find.byKey(const ValueKey('day-absence-a1')));
    await tap(tester, find.byKey(const ValueKey('day-absence-a2')));
    expect(find.text('Justifier 2 éléments'), findsOneWidget);
    await tap(tester, find.byKey(const ValueKey('day-record-menu-a1')));
    await tap(tester, find.text('Annuler cet enregistrement'));
    await tester.enterText(find.byKey(const ValueKey('cancel-reason')), 'Erreur de saisie');
    await tester.pumpAndSettle();
    await tap(tester, find.byKey(const ValueKey('sheet-submit')));

    expect(find.text('Justifier 1 élément'), findsOneWidget);
    await tap(tester, find.byKey(const ValueKey('day-justify')));
    await tap(tester, find.byKey(const ValueKey('reason-illness')));
    await tap(tester, find.byKey(const ValueKey('sheet-submit')));
    expect((server.sent('POST /justifications').single.body! as Map)['absenceIds'], ['a2']);
  });

  testWidgets('a cancelled absence is struck through and cannot be ticked', (tester) async {
    final first = ((day['classes'] as List).first as Map)['periods'] as List;
    first[0] = period(1, 'RECORDED', absences: [absence('a1', 'Awa', 'UNJUSTIFIED'), absence('a2', 'Bruno', 'CANCELLED')]);
    await open(tester);
    expect(find.text('Saisie · 1 absent'), findsOneWidget);
    await openFirstPeriod(tester);

    expect(find.byKey(const ValueKey('day-absence-a2')), findsNothing);
    expect(find.byKey(const ValueKey('day-record-menu-a2')), findsNothing);
    expect(find.text('Annulée'), findsOneWidget);
    final name = tester.widget<Text>(find.text('Bruno Fictif'));
    expect(name.style?.decoration, TextDecoration.lineThrough);
  });

  testWidgets("the day's lateness is justified from the class", (tester) async {
    await open(tester);

    expect(find.text('Chantal Fictif'), findsOneWidget);
    await tap(tester, find.byKey(const ValueKey('day-lateness-l1')));
    await tap(tester, find.byKey(const ValueKey('day-justify')));
    await tap(tester, find.byKey(const ValueKey('reason-illness')));
    await tap(tester, find.byKey(const ValueKey('sheet-submit')));

    final body = server.sent('POST /justifications').single.body! as Map;
    expect(body['latenessIds'], ['l1']);
    expect(body['absenceIds'], isEmpty);
  });

  testWidgets('another date is asked for', (tester) async {
    await open(tester);

    await tap(tester, find.byKey(const ValueKey('day-date')));
    final picker = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
    expect(picker.lastDate, DateTime(2026, 11, 10));
    expect(picker.firstDate, DateTime(2025, 9, 1));
    await tap(tester, find.descendant(of: find.byType(DatePickerDialog), matching: find.text('2')));
    await tap(tester, find.text('OK'));

    expect(server.sent('GET /attendance/day').last.query, {'date': '2026-11-02'});
  });

  testWidgets('a day without lessons says so', (tester) async {
    day = {
      'date': '2026-11-10',
      'schoolDay': false,
      'totals': {'notRecorded': 0, 'absentStudents': 0, 'lateness': 0},
      'classes': <Object>[],
    };
    await open(tester);

    expect(find.text('Pas de cours ce jour-là.'), findsOneWidget);
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, user: english);

    expect(find.text("Today's attendance"), findsWidgets);
    expect(find.text('Not recorded'), findsOneWidget);
    expect(find.text('Recorded · 2 absent'), findsOneWidget);
  });
}
