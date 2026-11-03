import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:intl/intl.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../../../l10n/app_localizations.dart';
import '../../teacher/attendance.dart' show isoDate;
import 'full_day_models.dart';

/// UC-D9: the full-day absence report as an A4 PDF, drawn on the phone from the server's listing
/// and shared by the DM. The letterhead is printed as the admin typed it (UC-A7, D36); everything
/// else is in the DM's language (NFR-12). The measures match the web preview of the letterhead.

const _margin = 15 * PdfPageFormat.mm;
const _logo = 30 * PdfPageFormat.mm;
const _gap = 12.0;
const _points = {LetterheadSize.small: 7.0, LetterheadSize.normal: 8.5, LetterheadSize.large: 10.0};

const _ink = PdfColor.fromInt(0xFF111111);
const _muted = PdfColor.fromInt(0xFF4A5B5A);
const _brand = PdfColor.fromInt(0xFF0F766E);
const _rule = PdfColor.fromInt(0xFF9AA9A8);
const _headFill = PdfColor.fromInt(0xFFE3F1EF);
const _zebra = PdfColor.fromInt(0xFFF6F9F9);
const _amber = PdfColor.fromInt(0xFFB45309);
const _amberFill = PdfColor.fromInt(0xFFFEF6E7);

/// Noto Sans, bundled with the app: it has every French accent, and the PDF looks the same on
/// every phone.
class ReportFonts {
  const ReportFonts(this.regular, this.bold);

  static Future<ReportFonts> load() async => ReportFonts(
    pw.Font.ttf(await rootBundle.load('assets/fonts/NotoSans-Regular.ttf')),
    pw.Font.ttf(await rootBundle.load('assets/fonts/NotoSans-Bold.ttf')),
  );

  final pw.Font regular;
  final pw.Font bold;
}

/// Every text of the document but the letterhead, in the DM's language.
class ReportTexts {
  const ReportTexts({
    required this.title,
    required this.date,
    required this.summary,
    required this.columnNumber,
    required this.columnName,
    required this.columnMatricule,
    required this.classHeading,
    required this.unrecorded,
    required this.footer,
    required this.page,
  });

  factory ReportTexts.from(AppLocalizations l, {required FullDayReport report, required DateTime generatedAt}) {
    final locale = l.localeName;
    final day = DateFormat('EEEE d MMMM y', locale).format(report.date);
    final blocks = [for (final b in report.blocks) b.name].join(', ');
    final unrecorded = [
      for (final u in report.unrecorded)
        '${u.className} (${[for (final p in u.periods) l.reportPeriod(p)].join(', ')})',
    ].join(', ');
    return ReportTexts(
      title: l.reportTitle,
      date: day.substring(0, 1).toUpperCase() + day.substring(1),
      summary: [
        if (blocks.isNotEmpty) blocks,
        if (report.generatedBy.isNotEmpty) l.reportBy(report.generatedBy),
        l.reportSummary(report.studentCount, report.classCount),
      ].join(' – '),
      columnNumber: l.reportColumnNumber,
      columnName: l.reportColumnName,
      columnMatricule: l.reportColumnMatricule,
      classHeading: l.reportClassHeading,
      unrecorded: unrecorded.isEmpty ? null : l.reportUnrecorded(unrecorded),
      footer: l.reportFooter(DateFormat.yMMMd(locale).format(generatedAt), DateFormat.Hm(locale).format(generatedAt)),
      page: l.reportPage,
    );
  }

  final String title;

  /// The day reported, spelt out: "Mardi 3 novembre 2026".
  final String date;

  /// Blocks, the DM, and how many students in how many classes.
  final String summary;
  final String columnNumber;
  final String columnName;
  final String columnMatricule;
  final String Function(String className, int count) classHeading;

  /// The classes with lessons never recorded that day, or null when every lesson was recorded.
  final String? unrecorded;
  final String footer;
  final String Function(int page, int pages) page;
}

/// absences-2026-11-03.pdf
String reportFileName(DateTime date) => 'absences-${isoDate(date)}.pdf';

Future<Uint8List> buildReportPdf({
  required FullDayReport report,
  required Letterhead letterhead,
  Uint8List? logo,
  required ReportTexts texts,
  required DateTime generatedAt,
  required ReportFonts fonts,
}) => buildReportDocument(
  report: report,
  letterhead: letterhead,
  logo: logo,
  texts: texts,
  generatedAt: generatedAt,
  fonts: fonts,
).save();

/// The columns of the header, left to right: French and English, or the one language of the
/// layout. With no line to print, the school name alone (D36).
@visibleForTesting
List<List<LetterheadLine>> headerColumns(Letterhead letterhead) {
  final columns = switch (letterhead.layout) {
    LetterheadLayout.bilingual => [letterhead.french, letterhead.english],
    LetterheadLayout.french => [letterhead.french],
    LetterheadLayout.english => [letterhead.english],
  };
  if (columns.every((c) => c.isEmpty)) {
    return [
      [LetterheadLine(letterhead.schoolName, bold: true, size: LetterheadSize.large)],
    ];
  }
  return columns;
}

@visibleForTesting
pw.Document buildReportDocument({
  required FullDayReport report,
  required Letterhead letterhead,
  Uint8List? logo,
  required ReportTexts texts,
  required DateTime generatedAt,
  required ReportFonts fonts,
}) {
  final document = pw.Document(title: '${texts.title} – ${texts.date}', author: 'SchoolLink', creator: 'SchoolLink');
  final several = report.blocks.length > 1;
  document.addPage(
    pw.MultiPage(
      pageTheme: pw.PageTheme(
        pageFormat: PdfPageFormat.a4,
        margin: const pw.EdgeInsets.all(_margin),
        theme: pw.ThemeData.withFont(base: fonts.regular, bold: fonts.bold).copyWith(
          defaultTextStyle: pw.TextStyle(font: fonts.regular, fontBold: fonts.bold, fontSize: 9, color: _ink),
        ),
      ),
      footer: (context) => _footer(context, texts),
      build: (context) => [
        _header(letterhead, logo),
        _doubleRule(),
        _titleBlock(texts),
        for (final block in report.blocks) ...[
          if (several) _blockHeading(block.name),
          for (final c in block.classes) ..._classTable(c, texts),
        ],
        if (texts.unrecorded case final line?) _unrecorded(line),
      ],
    ),
  );
  return document;
}

/// UC-D9: a logo the phone cannot decode is left out of the header; the report itself still matters more.
pw.MemoryImage? _readable(Uint8List? logo) {
  if (logo == null) return null;
  try {
    return pw.MemoryImage(logo);
  } on Exception {
    return null;
  }
}

pw.Widget _header(Letterhead letterhead, Uint8List? logo) {
  final columns = headerColumns(letterhead);
  final picture = _readable(logo);
  final image = picture == null
      ? null
      : pw.SizedBox(
          width: _logo,
          height: _logo,
          child: pw.Image(picture, fit: pw.BoxFit.contain),
        );
  pw.Widget column(List<LetterheadLine> lines) => pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.center,
    children: [
      for (final line in lines)
        pw.Text(
          line.text,
          textAlign: pw.TextAlign.center,
          style: pw.TextStyle(
            fontSize: _points[line.size],
            fontWeight: line.bold ? pw.FontWeight.bold : pw.FontWeight.normal,
            lineSpacing: 0.6,
          ),
        ),
    ],
  );
  if (columns.length == 2) {
    return pw.Row(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Expanded(child: column(columns[0])),
        pw.SizedBox(width: _gap),
        image ?? pw.SizedBox(width: _logo),
        pw.SizedBox(width: _gap),
        pw.Expanded(child: column(columns[1])),
      ],
    );
  }
  return pw.Center(
    child: pw.Column(
      children: [
        if (image != null) ...[image, pw.SizedBox(height: 6)],
        column(columns.single),
      ],
    ),
  );
}

/// A thick rule over a thin one, as on official letters.
pw.Widget _doubleRule() => pw.Padding(
  padding: const pw.EdgeInsets.only(top: 10, bottom: 14),
  child: pw.Column(
    children: [
      pw.Container(height: 1.4, color: _ink),
      pw.SizedBox(height: 1.6),
      pw.Container(height: 0.5, color: _ink),
    ],
  ),
);

pw.Widget _titleBlock(ReportTexts texts) => pw.Center(
  child: pw.Column(
    children: [
      pw.Text(
        texts.title.toUpperCase(),
        style: pw.TextStyle(fontSize: 10.5, fontWeight: pw.FontWeight.bold, color: _brand, letterSpacing: 1.4),
      ),
      pw.SizedBox(height: 4),
      pw.Text(texts.date, style: pw.TextStyle(fontSize: 20, fontWeight: pw.FontWeight.bold)),
      pw.SizedBox(height: 4),
      pw.Text(
        texts.summary,
        textAlign: pw.TextAlign.center,
        style: const pw.TextStyle(fontSize: 9, color: _muted),
      ),
      pw.SizedBox(height: 8),
    ],
  ),
);

pw.Widget _blockHeading(String name) => pw.Container(
  margin: const pw.EdgeInsets.only(top: 14, bottom: 2),
  padding: const pw.EdgeInsets.only(bottom: 3),
  decoration: const pw.BoxDecoration(
    border: pw.Border(bottom: pw.BorderSide(color: _brand, width: 0.8)),
  ),
  child: pw.Text(
    name.toUpperCase(),
    style: pw.TextStyle(fontSize: 10, fontWeight: pw.FontWeight.bold, color: _brand, letterSpacing: 1),
  ),
);

/// "6e A: 2 students" with a brand bar, then the table; its heading row repeats on a new page.
List<pw.Widget> _classTable(ReportClass c, ReportTexts texts) {
  const cell = pw.EdgeInsets.symmetric(horizontal: 6, vertical: 3.5);
  pw.Widget text(String value, {bool bold = false, pw.Alignment align = pw.Alignment.centerLeft}) => pw.Container(
    padding: cell,
    alignment: align,
    child: pw.Text(value, style: pw.TextStyle(fontSize: 9, fontWeight: bold ? pw.FontWeight.bold : null)),
  );
  return [
    pw.Container(
      margin: const pw.EdgeInsets.only(top: 12, bottom: 4),
      padding: const pw.EdgeInsets.only(left: 6),
      decoration: const pw.BoxDecoration(
        border: pw.Border(left: pw.BorderSide(color: _brand, width: 3)),
      ),
      child: pw.Text(
        texts.classHeading(c.name, c.students.length),
        style: pw.TextStyle(fontSize: 11, fontWeight: pw.FontWeight.bold),
      ),
    ),
    pw.Table(
      border: const pw.TableBorder(
        left: pw.BorderSide(color: _rule, width: 0.5),
        right: pw.BorderSide(color: _rule, width: 0.5),
        top: pw.BorderSide(color: _rule, width: 0.5),
        bottom: pw.BorderSide(color: _rule, width: 0.5),
        horizontalInside: pw.BorderSide(color: _rule, width: 0.5),
        verticalInside: pw.BorderSide(color: _rule, width: 0.5),
      ),
      columnWidths: const {0: pw.FixedColumnWidth(30), 1: pw.FlexColumnWidth(), 2: pw.FixedColumnWidth(100)},
      children: [
        pw.TableRow(
          decoration: const pw.BoxDecoration(color: _headFill),
          repeat: true,
          children: [
            text(texts.columnNumber, bold: true, align: pw.Alignment.center),
            text(texts.columnName, bold: true),
            text(texts.columnMatricule, bold: true, align: pw.Alignment.center),
          ],
        ),
        for (final (i, s) in c.students.indexed)
          pw.TableRow(
            decoration: i.isOdd ? const pw.BoxDecoration(color: _zebra) : null,
            children: [
              text('${i + 1}', align: pw.Alignment.center),
              // last name first, in capitals, as on the school's own lists
              pw.Container(
                padding: cell,
                child: pw.RichText(
                  text: pw.TextSpan(
                    style: const pw.TextStyle(fontSize: 9),
                    children: [
                      pw.TextSpan(
                        text: s.lastName.toUpperCase(),
                        style: pw.TextStyle(fontWeight: pw.FontWeight.bold),
                      ),
                      pw.TextSpan(text: ' ${s.firstName}'),
                    ],
                  ),
                ),
              ),
              text(s.matricule, align: pw.Alignment.center),
            ],
          ),
      ],
    ),
  ];
}

pw.Widget _unrecorded(String line) => pw.Container(
  width: double.infinity,
  margin: const pw.EdgeInsets.only(top: 16),
  padding: const pw.EdgeInsets.fromLTRB(8, 6, 8, 6),
  decoration: const pw.BoxDecoration(
    color: _amberFill,
    border: pw.Border(left: pw.BorderSide(color: _amber, width: 3)),
  ),
  child: pw.Text(line, style: const pw.TextStyle(fontSize: 8.5)),
);

pw.Widget _footer(pw.Context context, ReportTexts texts) => pw.Container(
  margin: const pw.EdgeInsets.only(top: 10),
  padding: const pw.EdgeInsets.only(top: 4),
  decoration: const pw.BoxDecoration(
    border: pw.Border(top: pw.BorderSide(color: _rule, width: 0.5)),
  ),
  child: pw.Row(
    mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
    children: [
      pw.Text(texts.footer, style: const pw.TextStyle(fontSize: 7.5, color: _muted)),
      pw.Text(
        texts.page(context.pageNumber, context.pagesCount),
        style: const pw.TextStyle(fontSize: 7.5, color: _muted),
      ),
    ],
  ),
);
