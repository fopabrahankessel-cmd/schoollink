import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-D8, D34, D41: the DM's scope dashboard on Android, as on the web: the last 30 days by
/// default, the absence rate, the unjustified absences, the lateness and the open alerts; the
/// classes, most absences first; the alerts by rule. Absences and lateness are counted apart (BR-8,
/// BR-9). Names are made up.
void main() {
  late FakeServer server;
  late Map<String, dynamic> dashboard;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 10, 10);

  Map<String, dynamic> counts({int studentPeriods = 400, int absent = 20, int unjustified = 12, int late = 7}) => {
    'sessions': 40,
    'studentPeriods': studentPeriods,
    'absentPeriods': absent,
    'unjustifiedAbsentPeriods': unjustified,
    'lateness': late,
    'unjustifiedLateness': 3,
  };

  setUp(() {
    dashboard = {
      'from': '2026-10-12',
      'to': '2026-11-10',
      'totals': counts(),
      'days': <Object>[],
      'classes': [
        {'classId': 'c-1', 'name': '6e A', 'counts': counts(absent: 5)},
        {'classId': 'c-2', 'name': '5e B', 'counts': counts(absent: 15)},
      ],
      'alerts': [
        {'rule': 'FULL_DAY_ABSENCE', 'open': 2, 'raised': 4, 'closed': 2},
        {'rule': 'CONSECUTIVE_LATENESS', 'open': 1, 'raised': 1, 'closed': 0},
        {'rule': 'UNJUSTIFIED_ABSENCES', 'open': 0, 'raised': 0, 'closed': 0},
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
      ..on('GET /dashboard/scope', (_) => Reply.ok(dashboard));
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
    await tap(tester, find.byKey(const ValueKey('more-dashboard')));
  }

  testWidgets('the last 30 days by default', (tester) async {
    await open(tester);

    expect(server.sent('GET /dashboard/scope').single.query, {'from': '2026-10-12', 'to': '2026-11-10'});
  });

  testWidgets('the figures', (tester) async {
    await open(tester);

    expect(find.text(NumberFormat.percentPattern('fr').format(0.05)), findsOneWidget);
    expect(find.text('Taux d\'absence'), findsOneWidget);
    expect(find.byKey(const ValueKey('figure-unjustified')), findsOneWidget);
    expect(
      find.descendant(of: find.byKey(const ValueKey('figure-unjustified')), matching: find.text('12')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: find.byKey(const ValueKey('figure-lateness')), matching: find.text('7')),
      findsOneWidget,
    );
    expect(
      find.descendant(of: find.byKey(const ValueKey('figure-open-alerts')), matching: find.text('3')),
      findsOneWidget,
    );
  });

  testWidgets('classes, most absences first', (tester) async {
    await open(tester);

    final first = tester.getTopLeft(find.byKey(const ValueKey('class-c-2'))).dy;
    final second = tester.getTopLeft(find.byKey(const ValueKey('class-c-1'))).dy;
    expect(first, lessThan(second));
  });

  testWidgets('another range', (tester) async {
    await open(tester);

    await tap(tester, find.byKey(const ValueKey('dashboard-range')));
    final picker = tester.widget<DateRangePickerDialog>(find.byType(DateRangePickerDialog));
    expect(picker.lastDate, DateTime(2026, 11, 10));
    expect(picker.initialDateRange, DateTimeRange(start: DateTime(2026, 10, 12), end: DateTime(2026, 11, 10)));
    await tester.tap(find.text('2').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('6').last);
    await tester.pumpAndSettle();
    await tap(tester, find.text('Enregistrer'));

    expect(server.sent('GET /dashboard/scope').last.query, {'from': '2026-11-02', 'to': '2026-11-06'});
  });

  testWidgets('no lesson in the range', (tester) async {
    dashboard['totals'] = counts(studentPeriods: 0, absent: 0);
    await open(tester);

    expect(find.descendant(of: find.byKey(const ValueKey('figure-rate')), matching: find.text('—')), findsOneWidget);
    expect(find.textContaining('0 %'), findsNothing);
  });

  testWidgets('reads in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['DM'], language: 'EN');
    server.on('GET /me', (_) => Reply.ok(english));
    await open(tester, user: english);

    expect(find.text('Absence rate'), findsOneWidget);
    expect(find.text(NumberFormat.percentPattern('en').format(0.05)), findsOneWidget);
  });
}
