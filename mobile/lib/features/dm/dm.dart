import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_failure.dart';
import '../../core/providers.dart';
import '../parent/parent.dart' show HistoryDay;
import '../teacher/attendance.dart' show isoDate;
import '../teacher/teacher_sessions.dart' show DayTime;

/// The three threshold rules (BR-16); [other] for a rule this version of the app does not know.
enum AlertRule {
  fullDayAbsence,
  consecutiveLateness,
  unjustifiedAbsences,
  other;

  static AlertRule parse(String? text) => switch (text) {
    'FULL_DAY_ABSENCE' => fullDayAbsence,
    'CONSECUTIVE_LATENESS' => consecutiveLateness,
    'UNJUSTIFIED_ABSENCES' => unjustifiedAbsences,
    _ => other,
  };
}

/// UC-D2: an open risk alert of the DM's classes, with what the rule saw (D30). The app only
/// shows it; the DM decides (BR-16 to BR-19).
class RiskAlert {
  const RiskAlert({
    required this.id,
    required this.studentId,
    required this.firstName,
    required this.lastName,
    required this.matricule,
    required this.className,
    required this.rule,
    required this.threshold,
    required this.asOf,
    required this.raisedAt,
    this.dates = const [],
    this.periods,
    this.from,
    this.to,
  });

  factory RiskAlert.fromJson(Map<String, dynamic> json) {
    final evidence = (json['evidence'] as Map?)?.cast<String, dynamic>() ?? const {};
    DateTime? date(Object? text) => text is String ? DateTime.parse(text) : null;
    return RiskAlert(
      id: json['id'] as String,
      studentId: json['studentId'] as String,
      firstName: json['firstName'] as String,
      lastName: json['lastName'] as String,
      matricule: json['matricule'] as String? ?? '',
      className: json['className'] as String? ?? '',
      rule: AlertRule.parse(json['ruleType'] as String?),
      threshold: json['threshold'] as int,
      asOf: DateTime.parse(json['asOf'] as String),
      raisedAt: DateTime.parse(json['raisedAt'] as String).toLocal(),
      dates: [for (final d in (evidence['dates'] as List? ?? const [])) DateTime.parse(d as String)],
      periods: (evidence['periods'] as num?)?.toInt(),
      from: date(evidence['from']),
      to: date(evidence['to']),
    );
  }

  final String id;
  final String studentId;
  final String firstName;
  final String lastName;
  final String matricule;
  final String className;
  final AlertRule rule;

  /// The rule's count when it fired.
  final int threshold;

  /// The school day the facts end on.
  final DateTime asOf;
  final DateTime raisedAt;

  /// Full-day absence and consecutive lateness: the days of the streak, oldest first.
  final List<DateTime> dates;

  /// Unjustified absences: how many periods, from when to when.
  final int? periods;
  final DateTime? from;
  final DateTime? to;

  String get fullName => '$lastName $firstName';
}

/// BR-18: the four decisions on an alert, always the DM's.
enum Decision {
  summon('SUMMON'),
  dismiss('DISMISS'),
  disciplinary('DISCIPLINARY'),
  refer('REFER');

  const Decision(this.wire);

  final String wire;

  /// D30: dismiss and disciplinary need the note (the reason, or the issue recorded).
  bool get needsNote => this == dismiss || this == disciplinary;
}

/// BR-11: why an absence or lateness is justified.
enum JustificationReason {
  illness('ILLNESS'),
  family('FAMILY'),
  schoolActivity('SCHOOL_ACTIVITY'),
  other('OTHER');

  const JustificationReason(this.wire);

  final String wire;

  /// NFR-10, D26: only "illness" is stored about health, so an illness takes no note.
  bool get takesNote => this != illness;
}

/// A meeting with the parents; both null: the next school day at the start of school (D31).
class Meeting {
  const Meeting({this.date, this.time});

  final DateTime? date;
  final DayTime? time;

  bool get isDefault => date == null;

  Map<String, Object> toJson() => {
    if (date case final date?) 'meetingDate': isoDate(date),
    if (time case final time?) 'meetingTime': time.toString(),
  };
}

/// A summons as the server stored it, to confirm the meeting to the DM.
class SummonsSent {
  const SummonsSent({required this.meetingDate, required this.meetingTime});

  factory SummonsSent.fromJson(Map<String, dynamic> json) => SummonsSent(
    meetingDate: DateTime.parse(json['meetingDate'] as String),
    meetingTime: DayTime.parse(json['meetingTime'] as String),
  );

  final DateTime meetingDate;
  final DayTime meetingTime;
}

/// UC-D8: a student's history for the DM, with the id of each record to justify or cancel it.
class StudentHistory {
  const StudentHistory({
    required this.studentId,
    required this.matricule,
    required this.firstName,
    required this.lastName,
    required this.className,
    required this.from,
    required this.to,
    required this.absentPeriods,
    required this.unjustifiedAbsentPeriods,
    required this.lateness,
    required this.unjustifiedLateness,
    required this.days,
  });

  factory StudentHistory.fromJson(Map<String, dynamic> json) {
    final totals = json['totals'] as Map<String, dynamic>;
    return StudentHistory(
      studentId: json['studentId'] as String,
      matricule: json['matricule'] as String? ?? '',
      firstName: json['firstName'] as String,
      lastName: json['lastName'] as String,
      className: json['className'] as String? ?? '',
      from: DateTime.parse(json['from'] as String),
      to: DateTime.parse(json['to'] as String),
      absentPeriods: totals['absentPeriods'] as int,
      unjustifiedAbsentPeriods: totals['unjustifiedAbsentPeriods'] as int,
      lateness: totals['lateness'] as int,
      unjustifiedLateness: totals['unjustifiedLateness'] as int,
      days: [for (final d in (json['days'] as List).cast<Map<String, dynamic>>()) HistoryDay.fromJson(d)],
    );
  }

  final String studentId;
  final String matricule;
  final String firstName;
  final String lastName;
  final String className;
  final DateTime from;
  final DateTime to;
  final int absentPeriods;
  final int unjustifiedAbsentPeriods;
  final int lateness;
  final int unjustifiedLateness;

  /// Newest day first.
  final List<HistoryDay> days;

  String get fullName => '$lastName $firstName';
}

/// The DM's reads and decisions. They need the network: only the teacher and assistant DM screens
/// work offline (NFR-3, D35). Every write is the DM's own act; the server audits it (NFR-11).
class DmRepository {
  DmRepository(this._dio);

  final Dio _dio;

  /// D30: the open alerts of the DM's classes, the longest waiting first.
  Future<List<RiskAlert>> openAlerts() async {
    final response = await _call(() => _dio.get<List<dynamic>>('/alerts', queryParameters: {'status': 'open'}));
    return [for (final a in response.data!.cast<Map<String, dynamic>>()) RiskAlert.fromJson(a)];
  }

  /// BR-18, D30, D31: the DM's decision; SUMMON stores the summons and closes the alert at once.
  Future<void> decide(String alertId, Decision decision, {String? note, Meeting meeting = const Meeting()}) => _call(
    () => _dio.post<void>(
      '/alerts/$alertId/decision',
      data: {'decision': decision.wire, 'note': ?_blankToNull(note), ...meeting.toJson()},
    ),
  );

  /// UC-D8, D34: the last 90 days of the student.
  Future<StudentHistory> history(String studentId) async {
    final response = await _call(() => _dio.get<Map<String, dynamic>>('/students/$studentId/history'));
    return StudentHistory.fromJson(response.data!);
  }

  /// BR-11, BR-12, D26: one justification for all of [absenceIds] and [latenessIds], or none.
  Future<void> justify({
    required JustificationReason reason,
    String? note,
    required List<String> absenceIds,
    required List<String> latenessIds,
  }) => _call(
    () => _dio.post<void>(
      '/justifications',
      data: {
        'reason': reason.wire,
        if (reason.takesNote) 'note': ?_blankToNull(note),
        'absenceIds': absenceIds,
        'latenessIds': latenessIds,
      },
    ),
  );

  /// BR-13, BR-14, D27: cancels an absence recorded by mistake; a parent already told gets a
  /// correction.
  Future<void> cancelAbsence(String recordId, String reason) =>
      _call(() => _dio.post<void>('/records/$recordId/cancel', data: {'reason': reason.trim()}));

  /// D11, D27: the same for a lateness.
  Future<void> cancelLateness(String recordId, String reason) =>
      _call(() => _dio.post<void>('/lateness/$recordId/cancel', data: {'reason': reason.trim()}));

  /// UC-D3, D31: summons the student's parents on the DM's own initiative.
  Future<SummonsSent> summon(String studentId, {required String reason, Meeting meeting = const Meeting()}) async {
    final response = await _call(
      () => _dio.post<Map<String, dynamic>>(
        '/summons',
        data: {'studentId': studentId, 'reason': reason.trim(), ...meeting.toJson()},
      ),
    );
    return SummonsSent.fromJson(response.data!);
  }

  static String? _blankToNull(String? text) => text == null || text.trim().isEmpty ? null : text.trim();

  Future<Response<T>> _call<T>(Future<Response<T>> Function() request) async {
    try {
      return await request();
    } on DioException catch (e) {
      throw ApiFailure.from(e);
    }
  }
}

final dmRepositoryProvider = Provider<DmRepository>((ref) => DmRepository(ref.watch(dioProvider)));

/// UC-D2: the open alerts of the DM's classes.
final openAlertsProvider = FutureProvider.autoDispose<List<RiskAlert>>(
  (ref) => ref.watch(dmRepositoryProvider).openAlerts(),
);

/// UC-D8: one student's history.
final studentHistoryProvider = FutureProvider.autoDispose.family<StudentHistory, String>(
  (ref, studentId) => ref.watch(dmRepositoryProvider).history(studentId),
);
