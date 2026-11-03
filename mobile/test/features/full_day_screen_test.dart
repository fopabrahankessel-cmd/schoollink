import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:printing/printing.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/dm/full_day/full_day_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-D9: the DM picks a day, generates the full-day absence report, previews it and shares the
/// PDF. Nobody absent all day: a message and no document. Names are made up.
void main() {
  late FakeServer server;
  late _RecordingSharer sharer;
  final dm = userJson(roles: ['DM']);
  final now = DateTime(2026, 11, 2, 18, 5);

  Map<String, dynamic> report({List<Map<String, dynamic>> students = const [], List<Object> unrecorded = const []}) => {
    'date': '2026-11-02',
    'generatedBy': 'Jean Fictif',
    'studentCount': students.length,
    'classCount': students.isEmpty ? 0 : 1,
    'blocks': [
      if (students.isNotEmpty)
        {
          'blockId': 'b-1',
          'blockName': 'Bloc 1',
          'classes': [
            {'classId': 'c-1', 'className': '6e A', 'students': students},
          ],
        },
    ],
    'unrecorded': unrecorded,
  };

  setUp(() {
    sharer = _RecordingSharer();
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(dm))
      ..on('GET /alerts', (_) => const Reply.ok([]))
      ..on('GET /lateness', (_) => const Reply.ok({'date': '2026-11-02', 'startTime': '07:30:00', 'lateness': []}))
      ..on('GET /reports/full-day-absences/default-date', (_) => const Reply.ok({'date': '2026-11-02'}))
      ..on(
        'GET /school/letterhead',
        (_) => const Reply.ok({
          'schoolName': 'Lycée Fictif X',
          'configured': true,
          'layout': 'BILINGUAL',
          'french': [
            {'text': 'RÉPUBLIQUE FICTIVE', 'bold': true, 'size': 'LARGE'},
          ],
          'english': [
            {'text': 'FICTIONAL REPUBLIC', 'bold': true, 'size': 'LARGE'},
          ],
          'hasLogo': false,
          'logoVersion': null,
        }),
      );
  });

  Future<void> open(WidgetTester tester, {Map<String, dynamic>? user}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(user ?? dm)),
      clock: () => now,
      overrides: [reportSharerProvider.overrideWithValue(sharer)],
    );
  }

  Future<void> tap(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  /// The request runs on the test's clock, the font files load in real time: let both run.
  Future<void> generate(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('full-day-generate')));
    await tester.pump();
    for (var i = 0; i < 40 && find.byKey(const ValueKey('full-day-busy')).evaluate().isNotEmpty; i++) {
      await tester.pump(const Duration(milliseconds: 50)); // the request, on the test's clock
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20))); // the font files
    }
    await tester.pump(const Duration(milliseconds: 300));
  }

  testWidgets('the DM home leads to the report, which opens on the default day', (tester) async {
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('open-full-day')));

    expect(find.byType(FullDayScreen), findsOneWidget);
    expect(find.text('Absents toute la journée'), findsWidgets);
    expect(find.text('lundi 2 novembre 2026'), findsOneWidget);
  });

  testWidgets('nobody absent all day: the message and the unrecorded lessons, no document', (tester) async {
    server.on(
      'GET /reports/full-day-absences',
      (_) => Reply.ok(
        report(
          unrecorded: [
            {
              'classId': 'c-2',
              'className': '4e B',
              'periods': [5],
            },
          ],
        ),
      ),
    );
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('open-full-day')));
    await generate(tester);

    expect(server.sent('GET /reports/full-day-absences').single.query, {'date': '2026-11-02'});
    expect(
      find.text("Aucun élève n'a été absent à toutes les heures de cours le lundi 2 novembre 2026."),
      findsOneWidget,
    );
    expect(find.textContaining('4e B (H5)'), findsOneWidget);
    expect(find.byType(PdfPreview), findsNothing);
    expect(find.byKey(const ValueKey('full-day-share')), findsNothing);
    expect(sharer.shared, isEmpty);
  });

  testWidgets('a report with students is previewed, then shared as a PDF', (tester) async {
    server.on(
      'GET /reports/full-day-absences',
      (_) => Reply.ok(
        report(
          students: [
            {'id': 's-1', 'lastName': 'Abena', 'firstName': 'Awa', 'matricule': 'TW-0001'},
          ],
        ),
      ),
    );
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('open-full-day')));
    await generate(tester);

    expect(find.byType(PdfPreview), findsOneWidget);
    expect(find.text('1 élève dans 1 classe'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('full-day-share')));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pump();

    expect(sharer.shared.single.$2, 'absences-2026-11-02.pdf');
    expect(ascii.decode(sharer.shared.single.$1.sublist(0, 4)), '%PDF');
    expect(server.sent('GET /school/letterhead/logo'), isEmpty, reason: 'the school has no logo');
  });

  testWidgets('the day picker stops at today, and a refused day shows the server message, without a retry', (
    tester,
  ) async {
    server.on(
      'GET /reports/full-day-absences',
      (_) => Reply.error(400, 'report.not_a_school_day', "Ce jour n'est pas un jour de classe.", field: 'date'),
    );
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('open-full-day')));

    await tap(tester, find.byKey(const ValueKey('full-day-date')));
    final picker = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
    expect(picker.lastDate, DateTime(2026, 11, 2));
    expect(picker.firstDate, DateTime(2025, 9, 1));
    expect(picker.selectableDayPredicate!(DateTime(2026, 11, 1)), isFalse, reason: 'a Sunday is never a school day');
    expect(picker.selectableDayPredicate!(DateTime(2026, 10, 30)), isTrue);
    await tap(tester, find.byIcon(Icons.chevron_left));
    await tap(tester, find.text('30'));
    await tap(tester, find.text('OK'));
    expect(find.text('vendredi 30 octobre 2026'), findsOneWidget);

    await generate(tester);
    expect(server.sent('GET /reports/full-day-absences').single.query, {'date': '2026-10-30'});
    expect(find.text("Ce jour n'est pas un jour de classe."), findsOneWidget);
    // retrying cannot change the answer: another day can
    expect(find.text('Réessayer'), findsNothing);
    expect(find.byIcon(Icons.wifi_off_rounded), findsNothing);
  });

  testWidgets('without the network the message offers a retry', (tester) async {
    await open(tester);
    await tap(tester, find.byKey(const ValueKey('open-full-day')));
    server.on('GET /reports/full-day-absences', (_) => Reply.unreachable);

    await generate(tester);

    expect(find.text('Réessayer'), findsOneWidget);
  });

  testWidgets('the entry is only on the DM home', (tester) async {
    final teacher = userJson(roles: ['TEACHER']);
    server.on('GET /me', (_) => Reply.ok(teacher));
    await open(tester, user: teacher);

    expect(find.byKey(const ValueKey('open-full-day')), findsNothing);
  });
}

class _RecordingSharer implements ReportSharer {
  final shared = <(Uint8List, String)>[];

  @override
  Future<void> share(Uint8List bytes, String fileName) async => shared.add((bytes, fileName));
}
