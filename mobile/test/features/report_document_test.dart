import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:schoollink/features/dm/full_day/full_day_models.dart';
import 'package:schoollink/features/dm/full_day/report_document.dart';
import 'package:schoollink/l10n/app_localizations.dart';

/// UC-D9: the full-day absence PDF, drawn on the phone. Names and matricules are made up.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ReportFonts fonts;
  final generatedAt = DateTime(2026, 11, 3, 17, 12);

  setUpAll(() async {
    await initializeDateFormatting('fr');
    await initializeDateFormatting('en');
    fonts = await ReportFonts.load();
  });

  FullDayReport reportOf(List<ReportStudent> students, {List<UnrecordedClass> unrecorded = const []}) => FullDayReport(
    date: DateTime(2026, 11, 3),
    generatedBy: 'Jean Fictif',
    studentCount: students.length,
    classCount: 1,
    blocks: [
      ReportBlock(
        name: 'Bloc 1',
        classes: [ReportClass(name: '6e A', students: students)],
      ),
    ],
    unrecorded: unrecorded,
  );

  const head = Letterhead(
    schoolName: 'Lycée Fictif X',
    configured: true,
    french: [LetterheadLine('RÉPUBLIQUE FICTIVE', bold: true, size: LetterheadSize.large)],
    english: [LetterheadLine('FICTIONAL REPUBLIC', bold: true, size: LetterheadSize.large)],
  );

  ReportTexts texts(String language, FullDayReport report) =>
      ReportTexts.from(lookupAppLocalizations(Locale(language)), report: report, generatedAt: generatedAt);

  test('builds a PDF', () async {
    final report = reportOf([const ReportStudent(lastName: 'Abena', firstName: 'Awa', matricule: 'TW-0001')]);

    final bytes = await buildReportPdf(
      report: report,
      letterhead: head,
      texts: texts('fr', report),
      generatedAt: generatedAt,
      fonts: fonts,
    );

    expect(ascii.decode(bytes.sublist(0, 4)), '%PDF');
  });

  test('a logo that cannot be read leaves the header without it, not the DM without a document', () async {
    final report = reportOf([const ReportStudent(lastName: 'Abena', firstName: 'Awa', matricule: 'TW-0001')]);
    // a PNG signature and nothing usable after it
    final broken = Uint8List.fromList([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, ...List.filled(64, 0)]);

    final bytes = await buildReportPdf(
      report: report,
      letterhead: head,
      texts: texts('fr', report),
      generatedAt: generatedAt,
      fonts: fonts,
      logo: broken,
    );

    expect(ascii.decode(bytes.sublist(0, 4)), '%PDF');
  });

  test('a long report with accents runs over several pages', () async {
    final students = [
      for (var i = 1; i <= 70; i++)
        ReportStudent(
          lastName: 'Éboué-Ngoïé $i',
          firstName: 'François',
          matricule: 'TW-${i.toString().padLeft(4, '0')}',
        ),
    ];
    final report = reportOf(
      students,
      unrecorded: const [
        UnrecordedClass(className: '4e B', periods: [5]),
      ],
    );

    final document = buildReportDocument(
      report: report,
      letterhead: head,
      texts: texts('fr', report),
      generatedAt: generatedAt,
      fonts: fonts,
    );
    await document.save();

    expect(document.document.pdfPageList.pages.length, greaterThanOrEqualTo(2));
  });

  test('the texts are in the DM language, with the date spelt out', () {
    final report = reportOf(
      [const ReportStudent(lastName: 'Abena', firstName: 'Awa', matricule: 'TW-0001')],
      unrecorded: const [
        UnrecordedClass(className: '4e B', periods: [5]),
        UnrecordedClass(className: '3e A', periods: [2, 3]),
      ],
    );

    final fr = texts('fr', report);
    expect(fr.title, 'Élèves absents toute la journée');
    expect(fr.date, 'Mardi 3 novembre 2026');
    expect(fr.summary, 'Bloc 1 – Surveillant Général : Jean Fictif – 1 élève dans 1 classe');
    expect(
      fr.unrecorded,
      'Heures non saisies, les absences de toute la journée y sont donc inconnues : 4e B (H5), 3e A (H2, H3)',
    );
    expect(fr.footer, 'Généré par SchoolLink le 3 nov. 2026 à 17:12');
    expect(fr.page(1, 2), 'Page 1/2');

    final en = texts('en', report);
    expect(en.date, 'Tuesday 3 November 2026');
    expect(en.classHeading('6e A', 2), '6e A: 2 students');
    expect(en.unrecorded, endsWith('4e B (P5), 3e A (P2, P3)'));
  });

  test('an unconfigured letterhead is the school name alone', () {
    final columns = headerColumns(const Letterhead(schoolName: 'Lycée Fictif X'));

    expect(columns, hasLength(1));
    expect([for (final l in columns.single) (l.text, l.bold)], [('Lycée Fictif X', true)]);
  });

  test('a one-language layout has one column, both languages two', () {
    const french = Letterhead(
      schoolName: 'Lycée Fictif X',
      configured: true,
      layout: LetterheadLayout.french,
      french: [LetterheadLine('RÉPUBLIQUE FICTIVE')],
      english: [LetterheadLine('FICTIONAL REPUBLIC')],
    );

    expect([for (final c in headerColumns(french)) c.single.text], ['RÉPUBLIQUE FICTIVE']);
    expect([for (final c in headerColumns(head)) c.single.text], ['RÉPUBLIQUE FICTIVE', 'FICTIONAL REPUBLIC']);
  });

  test('the file name carries the date', () {
    expect(reportFileName(DateTime(2026, 11, 3)), 'absences-2026-11-03.pdf');
  });
}
