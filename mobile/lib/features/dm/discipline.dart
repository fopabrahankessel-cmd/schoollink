import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import 'dm.dart';

/// UC-D5, D37: what a disciplinary incident was about.
enum DisciplinaryCategory {
  behaviour('BEHAVIOUR'),
  insolence('INSOLENCE'),
  fight('FIGHT'),
  absenteeism('ABSENTEEISM'),
  cheating('CHEATING'),
  dress('DRESS'),
  phone('PHONE'),
  damage('DAMAGE'),
  other('OTHER');

  const DisciplinaryCategory(this.wire);

  final String wire;

  static DisciplinaryCategory fromWire(String wire) =>
      values.firstWhere((c) => c.wire == wire, orElse: () => DisciplinaryCategory.other);
}

/// UC-D5, D37: what the DM decided about it.
enum DisciplinaryMeasure {
  none('NONE'),
  warning('WARNING'),
  detention('DETENTION'),
  suspension('SUSPENSION'),
  other('OTHER');

  const DisciplinaryMeasure(this.wire);

  final String wire;

  static DisciplinaryMeasure fromWire(String wire) =>
      values.firstWhere((m) => m.wire == wire, orElse: () => DisciplinaryMeasure.other);
}

String categoryLabel(DisciplinaryCategory category, AppLocalizations l) => switch (category) {
  DisciplinaryCategory.behaviour => l.categoryBehaviour,
  DisciplinaryCategory.insolence => l.categoryInsolence,
  DisciplinaryCategory.fight => l.categoryFight,
  DisciplinaryCategory.absenteeism => l.categoryAbsenteeism,
  DisciplinaryCategory.cheating => l.categoryCheating,
  DisciplinaryCategory.dress => l.categoryDress,
  DisciplinaryCategory.phone => l.categoryPhone,
  DisciplinaryCategory.damage => l.categoryDamage,
  DisciplinaryCategory.other => l.categoryOther,
};

String measureLabel(DisciplinaryMeasure measure, AppLocalizations l) => switch (measure) {
  DisciplinaryMeasure.none => l.measureNone,
  DisciplinaryMeasure.warning => l.measureWarning,
  DisciplinaryMeasure.detention => l.measureDetention,
  DisciplinaryMeasure.suspension => l.measureSuspension,
  DisciplinaryMeasure.other => l.measureOther,
};

/// UC-D5, D37: one incident of a student, as the DM sees it. Never edited: closed once, or
/// cancelled (and then no longer listed).
class DisciplinaryRecord {
  const DisciplinaryRecord({
    required this.id,
    required this.category,
    required this.measure,
    required this.occurredOn,
    required this.description,
    required this.open,
    required this.closeNote,
    required this.recordedByName,
    required this.alertId,
  });

  factory DisciplinaryRecord.fromJson(Map<String, dynamic> json) => DisciplinaryRecord(
    id: json['id'] as String,
    category: DisciplinaryCategory.fromWire(json['category'] as String),
    measure: DisciplinaryMeasure.fromWire(json['measure'] as String),
    occurredOn: DateTime.parse(json['occurredOn'] as String),
    description: json['description'] as String,
    open: json['status'] == 'OPEN',
    closeNote: json['closeNote'] as String?,
    recordedByName: json['recordedByName'] as String,
    alertId: json['alertId'] as String?,
  );

  final String id;
  final DisciplinaryCategory category;
  final DisciplinaryMeasure measure;

  /// The day of the incident (a school day, date only).
  final DateTime occurredOn;
  final String description;
  final bool open;
  final String? closeNote;
  final String recordedByName;

  /// Set when the record came from a decision on a risk alert (D4).
  final String? alertId;
}

/// UC-D5: one student's incidents, newest first.
final disciplinaryRecordsProvider = FutureProvider.autoDispose.family<List<DisciplinaryRecord>, String>(
  (ref, studentId) => ref.watch(dmRepositoryProvider).disciplinaryRecords(studentId),
);
