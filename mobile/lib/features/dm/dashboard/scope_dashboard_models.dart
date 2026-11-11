import '../dm.dart';

/// UC-D8, D34: what was recorded over a range. Cancelled records are not counted (BR-15);
/// absences and lateness are counted apart (BR-8, BR-9).
class Counts {
  const Counts({
    required this.sessions,
    required this.studentPeriods,
    required this.absentPeriods,
    required this.unjustifiedAbsentPeriods,
    required this.lateness,
    required this.unjustifiedLateness,
  });

  factory Counts.fromJson(Map<String, dynamic> json) => Counts(
    sessions: json['sessions'] as int,
    studentPeriods: json['studentPeriods'] as int,
    absentPeriods: json['absentPeriods'] as int,
    unjustifiedAbsentPeriods: json['unjustifiedAbsentPeriods'] as int,
    lateness: json['lateness'] as int,
    unjustifiedLateness: json['unjustifiedLateness'] as int,
  );

  final int sessions;

  /// Students expected over all the recorded periods: the base of the absence rate.
  final int studentPeriods;
  final int absentPeriods;
  final int unjustifiedAbsentPeriods;
  final int lateness;
  final int unjustifiedLateness;

  /// Null when no lesson was recorded: there is nothing to divide, and 0 % would be wrong.
  double? get absenceRate => studentPeriods == 0 ? null : absentPeriods / studentPeriods;
}

class ClassCounts {
  const ClassCounts({required this.classId, required this.name, required this.counts});

  factory ClassCounts.fromJson(Map<String, dynamic> json) => ClassCounts(
    classId: json['classId'] as String,
    name: json['name'] as String? ?? '',
    counts: Counts.fromJson((json['counts'] as Map).cast<String, dynamic>()),
  );

  final String classId;
  final String name;
  final Counts counts;
}

/// The alerts one rule raised and closed in the range, and those open now.
class RuleCount {
  const RuleCount({required this.rule, required this.open, required this.raised, required this.closed});

  factory RuleCount.fromJson(Map<String, dynamic> json) => RuleCount(
    rule: AlertRule.parse(json['rule'] as String?),
    open: json['open'] as int,
    raised: json['raised'] as int,
    closed: json['closed'] as int,
  );

  final AlertRule rule;
  final int open;
  final int raised;
  final int closed;
}

class ScopeDashboard {
  const ScopeDashboard({required this.totals, required this.classes, required this.alerts});

  /// The days of the web's chart are not read on the phone.
  factory ScopeDashboard.fromJson(Map<String, dynamic> json) => ScopeDashboard(
    totals: Counts.fromJson((json['totals'] as Map).cast<String, dynamic>()),
    classes: [for (final c in (json['classes'] as List).cast<Map<String, dynamic>>()) ClassCounts.fromJson(c)]
      ..sort((a, b) => b.counts.absentPeriods.compareTo(a.counts.absentPeriods)),
    alerts: [for (final a in (json['alerts'] as List).cast<Map<String, dynamic>>()) RuleCount.fromJson(a)],
  );

  final Counts totals;

  /// Most absences first.
  final List<ClassCounts> classes;
  final List<RuleCount> alerts;

  int get openAlerts => alerts.fold(0, (sum, r) => sum + r.open);
}

/// A range of school days, both ends included.
typedef DateSpan = ({DateTime from, DateTime to});
