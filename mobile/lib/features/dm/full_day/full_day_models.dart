/// UC-D9, D2: the students of the DM's classes absent from every timetabled period of a day,
/// unjustified, grouped by block and class. A class with a lesson never recorded lists nobody and
/// is named in [unrecorded] instead.
class FullDayReport {
  const FullDayReport({
    required this.date,
    required this.generatedBy,
    required this.studentCount,
    required this.classCount,
    required this.blocks,
    required this.unrecorded,
  });

  factory FullDayReport.fromJson(Map<String, dynamic> json) => FullDayReport(
    date: DateTime.parse(json['date'] as String),
    generatedBy: json['generatedBy'] as String? ?? '',
    studentCount: json['studentCount'] as int,
    classCount: json['classCount'] as int,
    blocks: [for (final b in _maps(json['blocks'])) ReportBlock.fromJson(b)],
    unrecorded: [for (final u in _maps(json['unrecorded'])) UnrecordedClass.fromJson(u)],
  );

  final DateTime date;

  /// The DM who asked for it, "First Last".
  final String generatedBy;
  final int studentCount;
  final int classCount;
  final List<ReportBlock> blocks;
  final List<UnrecordedClass> unrecorded;

  /// Nobody was absent all day: no document is made, only the message.
  bool get isEmpty => blocks.isEmpty;
}

class ReportBlock {
  const ReportBlock({required this.name, required this.classes});

  factory ReportBlock.fromJson(Map<String, dynamic> json) => ReportBlock(
    name: json['blockName'] as String,
    classes: [for (final c in _maps(json['classes'])) ReportClass.fromJson(c)],
  );

  final String name;
  final List<ReportClass> classes;
}

class ReportClass {
  const ReportClass({required this.name, required this.students});

  factory ReportClass.fromJson(Map<String, dynamic> json) => ReportClass(
    name: json['className'] as String,
    students: [for (final s in _maps(json['students'])) ReportStudent.fromJson(s)],
  );

  final String name;
  final List<ReportStudent> students;
}

class ReportStudent {
  const ReportStudent({required this.lastName, required this.firstName, required this.matricule});

  factory ReportStudent.fromJson(Map<String, dynamic> json) => ReportStudent(
    lastName: json['lastName'] as String,
    firstName: json['firstName'] as String,
    matricule: json['matricule'] as String? ?? '',
  );

  final String lastName;
  final String firstName;
  final String matricule;
}

/// A class whose [periods] (by number) were never recorded that day.
class UnrecordedClass {
  const UnrecordedClass({required this.className, required this.periods});

  factory UnrecordedClass.fromJson(Map<String, dynamic> json) => UnrecordedClass(
    className: json['className'] as String,
    periods: [for (final p in json['periods'] as List) (p as num).toInt()],
  );

  final String className;
  final List<int> periods;
}

/// UC-A7, D36: how the header is laid out: French left, logo centre, English right; or one
/// language centred under the logo.
enum LetterheadLayout {
  bilingual,
  french,
  english;

  static LetterheadLayout parse(String? text) => switch (text) {
    'FRENCH' => french,
    'ENGLISH' => english,
    _ => bilingual,
  };
}

enum LetterheadSize {
  small,
  normal,
  large;

  static LetterheadSize parse(String? text) => switch (text) {
    'SMALL' => small,
    'LARGE' => large,
    _ => normal,
  };
}

class LetterheadLine {
  const LetterheadLine(this.text, {this.bold = false, this.size = LetterheadSize.normal});

  factory LetterheadLine.fromJson(Map<String, dynamic> json) => LetterheadLine(
    json['text'] as String,
    bold: json['bold'] as bool? ?? false,
    size: LetterheadSize.parse(json['size'] as String?),
  );

  final String text;
  final bool bold;
  final LetterheadSize size;
}

/// The school's letterhead, set by the admin on the web. Not [configured]: the header is the
/// school name alone.
class Letterhead {
  const Letterhead({
    required this.schoolName,
    this.configured = false,
    this.layout = LetterheadLayout.bilingual,
    this.french = const [],
    this.english = const [],
    this.hasLogo = false,
    this.logoVersion,
  });

  factory Letterhead.fromJson(Map<String, dynamic> json) => Letterhead(
    schoolName: json['schoolName'] as String,
    configured: json['configured'] as bool? ?? false,
    layout: LetterheadLayout.parse(json['layout'] as String?),
    french: [for (final l in _maps(json['french'])) LetterheadLine.fromJson(l)],
    english: [for (final l in _maps(json['english'])) LetterheadLine.fromJson(l)],
    hasLogo: json['hasLogo'] as bool? ?? false,
    logoVersion: json['logoVersion'] as String?,
  );

  final String schoolName;
  final bool configured;
  final LetterheadLayout layout;
  final List<LetterheadLine> french;
  final List<LetterheadLine> english;
  final bool hasLogo;

  /// Changes with each new logo: the key under which the app keeps it.
  final String? logoVersion;
}

List<Map<String, dynamic>> _maps(Object? list) => (list as List? ?? const []).cast<Map<String, dynamic>>();
