import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/api/api_failure.dart';
import 'package:schoollink/features/dm/dm.dart';
import 'package:schoollink/features/dm/full_day/full_day_models.dart';

import '../support/fake_server.dart';

/// UC-D9, UC-A7: the data of the full-day absence report and of the letterhead it is printed
/// under. Names and matricules are made up.
void main() {
  late FakeServer server;
  late DmRepository repository;

  setUp(() {
    server = FakeServer();
    repository = DmRepository(Dio(BaseOptions(baseUrl: 'http://test/api/v1'))..httpClientAdapter = server);
  });

  Map<String, dynamic> student(String last, String first, String matricule) => {
    'id': 'id-$matricule',
    'lastName': last,
    'firstName': first,
    'matricule': matricule,
  };

  final report = {
    'date': '2026-11-03',
    'generatedBy': 'Jean Fictif',
    'studentCount': 3,
    'classCount': 2,
    'blocks': [
      {
        'blockId': 'b-1',
        'blockName': 'Bloc 1',
        'classes': [
          {
            'classId': 'c-1',
            'className': '6e A',
            'students': [student('Abena', 'Awa', 'TW-0001'), student('Bello', 'Paul', 'TW-0002')],
          },
        ],
      },
      {
        'blockId': 'b-3',
        'blockName': 'Bloc 3',
        'classes': [
          {
            'classId': 'c-4',
            'className': '4e D',
            'students': [student('Coulibaly', 'Inès', 'TW-0009')],
          },
        ],
      },
    ],
    'unrecorded': [
      {
        'classId': 'c-2',
        'className': '6e B',
        'periods': [2, 5],
      },
    ],
  };

  test('reads a report of two blocks, sending the chosen day', () async {
    server.on('GET /reports/full-day-absences', (_) => Reply.ok(report));

    final result = await repository.fullDayReport(DateTime(2026, 11, 3));

    expect(server.requests.single.query, {'date': '2026-11-03'});
    expect(result.date, DateTime(2026, 11, 3));
    expect(result.generatedBy, 'Jean Fictif');
    expect((result.studentCount, result.classCount, result.isEmpty), (3, 2, false));
    expect([for (final b in result.blocks) b.name], ['Bloc 1', 'Bloc 3']);
    final sixthA = result.blocks.first.classes.single;
    expect(sixthA.name, '6e A');
    expect(
      [for (final s in sixthA.students) '${s.lastName} ${s.firstName} ${s.matricule}'],
      ['Abena Awa TW-0001', 'Bello Paul TW-0002'],
    );
    expect(result.unrecorded.single.className, '6e B');
    expect(result.unrecorded.single.periods, [2, 5]);
  });

  test('a day with nobody absent all day is empty, and still names the unrecorded lessons', () async {
    server.on(
      'GET /reports/full-day-absences',
      (_) => Reply.ok({...report, 'studentCount': 0, 'classCount': 0, 'blocks': []}),
    );

    final result = await repository.fullDayReport(DateTime(2026, 11, 3));

    expect(result.isEmpty, isTrue);
    expect(result.unrecorded, hasLength(1));
  });

  test("a day that is not over gives the server's message", () async {
    server.on(
      'GET /reports/full-day-absences',
      (_) => Reply.error(
        400,
        'report.day_not_finished',
        "La journée n'est pas terminée : le rapport du jour est disponible à partir de 17 h.",
        field: 'date',
      ),
    );

    await expectLater(
      repository.fullDayReport(DateTime(2026, 11, 3)),
      throwsA(
        isA<ApiFailure>()
            .having((f) => f.code, 'code', 'report.day_not_finished')
            .having((f) => f.message, 'message', contains('17 h')),
      ),
    );
  });

  test('the default day is the one the server picks', () async {
    server.on('GET /reports/full-day-absences/default-date', (_) => const Reply.ok({'date': '2026-10-30'}));

    expect(await repository.defaultReportDate(), DateTime(2026, 10, 30));
  });

  test('reads the letterhead, its lines in order with weight and size', () async {
    server.on(
      'GET /school/letterhead',
      (_) => const Reply.ok({
        'schoolName': 'Lycée Fictif X',
        'configured': true,
        'layout': 'FRENCH',
        'french': [
          {'text': 'RÉPUBLIQUE FICTIVE', 'bold': true, 'size': 'LARGE'},
          {'text': 'Paix – Travail', 'bold': false, 'size': 'SMALL'},
        ],
        'english': <Object>[],
        'hasLogo': true,
        'logoVersion': '1793635722000',
      }),
    );

    final head = await repository.letterhead();

    expect(
      (head.schoolName, head.configured, head.layout, head.hasLogo),
      ('Lycée Fictif X', true, LetterheadLayout.french, true),
    );
    expect(head.logoVersion, '1793635722000');
    expect(
      [for (final l in head.french) (l.text, l.bold, l.size)],
      [('RÉPUBLIQUE FICTIVE', true, LetterheadSize.large), ('Paix – Travail', false, LetterheadSize.small)],
    );
    expect(head.english, isEmpty);
  });

  test('reads the logo bytes, and null when the school has none', () async {
    server.on('GET /school/letterhead/logo', (_) => Reply.bytes([0x89, 0x50, 0x4E, 0x47], 'image/png'));
    expect(await repository.letterheadLogo(), [0x89, 0x50, 0x4E, 0x47]);

    server.on('GET /school/letterhead/logo', (_) => Reply.error(404, 'not_found', 'Introuvable.'));
    expect(await repository.letterheadLogo(), isNull);
  });
}
