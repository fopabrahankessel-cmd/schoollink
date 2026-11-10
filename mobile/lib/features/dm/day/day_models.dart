import '../../teacher/teacher_sessions.dart' show DayTime;

/// UC-D10, D40: one day of attendance in the DM's classes, as `GET /attendance/day` returns it. The
/// server computes every status and count, so the phone and the web show the same thing.
class DayAttendance {
  const DayAttendance({
    required this.date,
    required this.schoolDay,
    required this.notRecorded,
    required this.absentStudents,
    required this.lateness,
    required this.classes,
  });

  factory DayAttendance.fromJson(Map<String, dynamic> json) {
    final totals = json['totals'] as Map<String, dynamic>;
    return DayAttendance(
      date: DateTime.parse(json['date'] as String),
      schoolDay: json['schoolDay'] as bool,
      notRecorded: totals['notRecorded'] as int,
      absentStudents: totals['absentStudents'] as int,
      lateness: totals['lateness'] as int,
      classes: [for (final c in json['classes'] as List) DayClass.fromJson(c as Map<String, dynamic>)],
    );
  }

  final DateTime date;
  final bool schoolDay;
  final int notRecorded;
  final int absentStudents;
  final int lateness;
  final List<DayClass> classes;
}

class DayClass {
  const DayClass({required this.classId, required this.className, required this.periods, required this.lateness});

  factory DayClass.fromJson(Map<String, dynamic> json) => DayClass(
    classId: json['classId'] as String,
    className: json['className'] as String,
    periods: [for (final p in json['periods'] as List) DayPeriod.fromJson(p as Map<String, dynamic>)],
    lateness: [for (final l in json['lateness'] as List) DayLateness.fromJson(l as Map<String, dynamic>)],
  );

  final String classId;
  final String className;
  final List<DayPeriod> periods;
  final List<DayLateness> lateness;
}

/// RECORDED: a session exists. NOT_RECORDED: started or past, without a roll call (information
/// only, BR-4). UPCOMING: not started yet.
enum PeriodStatus {
  recorded('RECORDED'),
  notRecorded('NOT_RECORDED'),
  upcoming('UPCOMING');

  const PeriodStatus(this.wire);

  final String wire;

  static PeriodStatus parse(String wire) => values.firstWhere((s) => s.wire == wire);
}

/// BR-15: a cancelled record is listed but never counted.
enum RecordState {
  unjustified('UNJUSTIFIED'),
  justified('JUSTIFIED'),
  cancelled('CANCELLED');

  const RecordState(this.wire);

  final String wire;

  static RecordState parse(String wire) => values.firstWhere((s) => s.wire == wire);
}

class DayPeriod {
  const DayPeriod({
    required this.periodId,
    required this.number,
    required this.startTime,
    required this.subject,
    required this.teacherName,
    required this.status,
    required this.absentCount,
    required this.absences,
  });

  factory DayPeriod.fromJson(Map<String, dynamic> json) => DayPeriod(
    periodId: json['periodId'] as String,
    number: json['number'] as int,
    startTime: DayTime.parse(json['startTime'] as String),
    subject: json['subject'] as String?,
    teacherName: json['teacherName'] as String?,
    status: PeriodStatus.parse(json['status'] as String),
    absentCount: json['absentCount'] as int,
    absences: [for (final a in json['absences'] as List) DayAbsence.fromJson(a as Map<String, dynamic>)],
  );

  final String periodId;
  final int number;
  final DayTime startTime;
  final String? subject;
  final String? teacherName;
  final PeriodStatus status;
  final int absentCount;
  final List<DayAbsence> absences;
}

class DayAbsence {
  const DayAbsence({
    required this.recordId,
    required this.studentId,
    required this.firstName,
    required this.lastName,
    required this.matricule,
    required this.state,
  });

  factory DayAbsence.fromJson(Map<String, dynamic> json) => DayAbsence(
    recordId: json['recordId'] as String,
    studentId: json['studentId'] as String,
    firstName: json['firstName'] as String,
    lastName: json['lastName'] as String,
    matricule: json['matricule'] as String,
    state: RecordState.parse(json['state'] as String),
  );

  final String recordId;
  final String studentId;
  final String firstName;
  final String lastName;
  final String matricule;
  final RecordState state;

  String get fullName => '$firstName $lastName';
}

class DayLateness {
  const DayLateness({
    required this.recordId,
    required this.studentId,
    required this.firstName,
    required this.lastName,
    required this.matricule,
    required this.arrivalTime,
    required this.state,
  });

  factory DayLateness.fromJson(Map<String, dynamic> json) => DayLateness(
    recordId: json['recordId'] as String,
    studentId: json['studentId'] as String,
    firstName: json['firstName'] as String,
    lastName: json['lastName'] as String,
    matricule: json['matricule'] as String,
    arrivalTime: DayTime.parse(json['arrivalTime'] as String),
    state: RecordState.parse(json['state'] as String),
  );

  final String recordId;
  final String studentId;
  final String firstName;
  final String lastName;
  final String matricule;
  final DayTime arrivalTime;
  final RecordState state;

  String get fullName => '$firstName $lastName';
}
