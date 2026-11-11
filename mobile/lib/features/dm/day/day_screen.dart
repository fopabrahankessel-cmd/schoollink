import 'package:flutter/material.dart' hide DayPeriod;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../core/providers.dart';
import '../../../core/theme.dart';
import '../../../core/ui/skeleton.dart';
import '../../../core/ui/widgets.dart';
import '../../../l10n/app_localizations.dart';
import '../../auth/failure_text.dart';
import '../dm.dart';
import '../dm_sheets.dart';
import 'day_models.dart';
import '../../shell/tab_path.dart';

/// UC-D10, D40: "Présences du jour". One day of the DM's classes, period by period, with the day's
/// lateness under each class (BR-8, BR-9: side by side, never merged). A period not recorded is
/// information only: the DM never records a roll call here (BR-4). From here the DM justifies
/// several records at once (BR-12) or cancels one (BR-13); cancelled rows stay listed, struck
/// through, never counted (BR-15).
class DayScreen extends ConsumerStatefulWidget {
  const DayScreen({super.key});

  @override
  ConsumerState<DayScreen> createState() => _DayScreenState();
}

class _DayScreenState extends ConsumerState<DayScreen> {
  /// Null: today at school, as the server reads it.
  DateTime? _date;

  /// The ticked lateness records, to justify together.
  final Set<String> _lateness = {};

  Future<void> _pick(DateTime shown) async {
    final today = DateUtils.dateOnly(ref.read(clockProvider)());
    final chosen = await showDatePicker(
      context: context,
      initialDate: shown.isAfter(today) ? today : shown,
      firstDate: DateTime(today.year - 1, 9, 1),
      lastDate: today,
    );
    if (chosen != null && mounted && !DateUtils.isSameDay(chosen, shown)) {
      setState(() {
        _date = chosen;
        _lateness.clear();
      });
    }
  }

  Future<void> _justifyLateness() async {
    final l = AppLocalizations.of(context);
    final ids = [..._lateness];
    final done = await showActionSheet(
      context,
      JustifySheet(
        count: ids.length,
        send: (reason, note) =>
            ref.read(dmRepositoryProvider).justify(reason: reason, note: note, absenceIds: [], latenessIds: ids),
      ),
    );
    if (done && mounted) {
      setState(_lateness.clear);
      dayChanged(context, ref, _date, l.justifiedCount(ids.length));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final day = ref.watch(dayProvider(_date));
    return Scaffold(
      appBar: AppBar(title: Text(l.dayTitle)),
      body: switch (day) {
        AsyncValue(value: final day?) => RefreshIndicator(
          onRefresh: () => ref.refresh(dayProvider(_date).future).then((_) {}, onError: (_) {}),
          child: _DayList(
            day: day,
            date: _date,
            onPick: () => _pick(day.date),
            selected: _lateness,
            onToggle: (id) => setState(() => _lateness.contains(id) ? _lateness.remove(id) : _lateness.add(id)),
            onCancelled: (id) => setState(() => _lateness.remove(id)),
          ),
        ),
        AsyncError(:final error) => FailureView(
          message: failureText(error, l),
          onRetry: () => ref.invalidate(dayProvider(_date)),
        ),
        _ => const Skeleton(child: SkeletonRows(count: 6)),
      },
      bottomNavigationBar: _lateness.isEmpty
          ? null
          : _JustifyBar(count: _lateness.length, onClear: () => setState(_lateness.clear), onJustify: _justifyLateness),
    );
  }
}

/// After a justification or a cancellation: the day and the alerts are read again.
void dayChanged(BuildContext context, WidgetRef ref, DateTime? date, String message) {
  ref
    ..invalidate(dayProvider(date))
    ..invalidate(openAlertsProvider);
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));
}

/// Cancels one absence or lateness, with a mandatory reason (BR-13). True once cancelled: the
/// caller drops it from its selection, since a cancelled record can no longer be justified.
Future<bool> cancelDayRecord(
  BuildContext context,
  WidgetRef ref,
  DateTime? date, {
  required String recordId,
  required bool lateness,
  required String what,
}) async {
  final l = AppLocalizations.of(context);
  final repository = ref.read(dmRepositoryProvider);
  final done = await showActionSheet(
    context,
    CancelRecordSheet(
      what: what,
      send: (reason) =>
          lateness ? repository.cancelLateness(recordId, reason) : repository.cancelAbsence(recordId, reason),
    ),
  );
  if (done && context.mounted) {
    dayChanged(context, ref, date, l.recordCancelled);
  }
  return done;
}

class _JustifyBar extends StatelessWidget {
  const _JustifyBar({required this.count, required this.onClear, required this.onJustify});

  final int count;
  final VoidCallback onClear;
  final VoidCallback onJustify;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(Gap.l, Gap.s, Gap.l, Gap.m),
        child: Row(
          children: [
            IconButton(tooltip: l.clearSelection, icon: const Icon(Icons.close_rounded), onPressed: onClear),
            const SizedBox(width: Gap.s),
            Expanded(
              child: FilledButton.icon(
                key: const ValueKey('day-justify'),
                onPressed: onJustify,
                icon: const Icon(Icons.verified_outlined),
                label: Text(l.justifyCount(count)),
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _DayList extends StatelessWidget {
  const _DayList({
    required this.day,
    required this.date,
    required this.onPick,
    required this.selected,
    required this.onToggle,
    required this.onCancelled,
  });

  final DayAttendance day;
  final DateTime? date;
  final VoidCallback onPick;
  final Set<String> selected;
  final void Function(String id) onToggle;
  final void Function(String id) onCancelled;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();

    Widget total(int count, String label) => Expanded(
      child: Semantics(
        container: true,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(Radii.l),
          ),
          child: Padding(
            padding: const EdgeInsets.all(Gap.m),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$count',
                  style: theme.textTheme.headlineMedium?.copyWith(color: status.onHeader, fontFeatures: tabular),
                ),
                Text(label, style: theme.textTheme.bodySmall?.copyWith(color: status.onHeaderMuted)),
              ],
            ),
          ),
        ),
      ),
    );

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: Gap.xl),
      children: [
        HeaderBand(
          children: [
            Material(
              color: status.onHeader.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(Radii.m),
              child: InkWell(
                key: const ValueKey('day-date'),
                borderRadius: BorderRadius.circular(Radii.m),
                onTap: onPick,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: Gap.m, vertical: Gap.m),
                  child: Row(
                    children: [
                      Icon(Icons.calendar_today_outlined, color: status.onHeader, size: 20),
                      const SizedBox(width: Gap.m),
                      Expanded(
                        child: Text(
                          DateFormat.yMMMMEEEEd(locale).format(day.date),
                          style: theme.textTheme.titleMedium?.copyWith(color: status.onHeader),
                        ),
                      ),
                      Text(l.dayChangeDate, style: theme.textTheme.bodySmall?.copyWith(color: status.onHeaderMuted)),
                    ],
                  ),
                ),
              ),
            ),
            if (day.schoolDay) ...[
              const SizedBox(height: Gap.m),
              IntrinsicHeight(
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    total(day.notRecorded, l.dayTotalNotRecorded(day.notRecorded)),
                    const SizedBox(width: Gap.s),
                    total(day.absentStudents, l.dayTotalAbsent(day.absentStudents)),
                    const SizedBox(width: Gap.s),
                    total(day.lateness, l.dayTotalLateness(day.lateness)),
                  ],
                ),
              ),
            ],
          ],
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (!day.schoolDay)
                EmptyState(icon: Icons.event_busy_outlined, title: l.dayNoLessons)
              else if (day.classes.isEmpty)
                EmptyState(icon: Icons.class_outlined, title: l.dayNoClasses)
              else
                for (final schoolClass in day.classes) ...[
                  SectionHeader(title: schoolClass.className),
                  _ClassCard(
                    schoolClass: schoolClass,
                    date: date,
                    selected: selected,
                    onToggle: onToggle,
                    onCancelled: onCancelled,
                  ),
                ],
            ],
          ),
        ),
      ],
    );
  }
}

/// One class: its periods, then the day's lateness.
class _ClassCard extends ConsumerWidget {
  const _ClassCard({
    required this.schoolClass,
    required this.date,
    required this.selected,
    required this.onToggle,
    required this.onCancelled,
  });

  final DayClass schoolClass;
  final DateTime? date;
  final Set<String> selected;
  final void Function(String id) onToggle;
  final void Function(String id) onCancelled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final period in schoolClass.periods) _PeriodRow(schoolClass: schoolClass, period: period, date: date),
          if (schoolClass.lateness.isNotEmpty) ...[
            const Divider(),
            Padding(
              padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.xs),
              child: Semantics(header: true, child: Text(l.dayLateness, style: theme.textTheme.titleSmall)),
            ),
            for (final late in schoolClass.lateness)
              _RecordRow(
                key: ValueKey('day-lateness-${late.recordId}'),
                name: late.fullName,
                detail: l.lateArrivedAt(late.arrivalTime.toString()),
                state: late.state,
                lateness: true,
                selected: selected.contains(late.recordId),
                onToggle: late.state == RecordState.unjustified ? () => onToggle(late.recordId) : null,
                menuKey: ValueKey('day-record-menu-${late.recordId}'),
                onCancel: () async {
                  if (await cancelDayRecord(
                    context,
                    ref,
                    date,
                    recordId: late.recordId,
                    lateness: true,
                    what: '${l.eventLateness}, ${late.fullName}, ${late.arrivalTime}',
                  )) {
                    onCancelled(late.recordId);
                  }
                },
                onHistory: () => context.push(underTab(context, 'student/${late.studentId}')),
              ),
            const SizedBox(height: Gap.xs),
          ],
        ],
      ),
    );
  }
}

class _PeriodRow extends StatelessWidget {
  const _PeriodRow({required this.schoolClass, required this.period, required this.date});

  final DayClass schoolClass;
  final DayPeriod period;
  final DateTime? date;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final line = [
      l.dayPeriodShort(period.number),
      period.startTime.toString(),
      ?period.subject,
      ?period.teacherName,
    ].join(' · ');
    final (label, background, foreground) = switch (period.status) {
      PeriodStatus.recorded => (l.dayRecorded(period.absentCount), scheme.primaryContainer, scheme.onPrimaryContainer),
      PeriodStatus.notRecorded => (l.dayNotRecorded, status.lateContainer, status.onLateContainer),
      PeriodStatus.upcoming => (l.dayUpcoming, scheme.surfaceContainerHigh, scheme.onSurfaceVariant),
    };
    return InkWell(
      key: ValueKey('day-period-${period.number}-${schoolClass.className}'),
      onTap: switch (period.status) {
        PeriodStatus.recorded => () => showModalBottomSheet<void>(
          context: context,
          isScrollControlled: true,
          useSafeArea: true,
          showDragHandle: true,
          builder: (context) => _AbsenteesSheet(date: date, classId: schoolClass.classId, number: period.number),
        ),
        // information only (BR-4): the teacher records it, the DM cannot
        PeriodStatus.notRecorded =>
          () => ScaffoldMessenger.of(context)
            ..hideCurrentSnackBar()
            ..showSnackBar(SnackBar(content: Text(l.dayNotRecordedHint))),
        PeriodStatus.upcoming => null,
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: Gap.l, vertical: Gap.m),
        child: Row(
          children: [
            Expanded(
              child: Text(line, style: theme.textTheme.bodyLarge?.copyWith(fontFeatures: tabular)),
            ),
            const SizedBox(width: Gap.s),
            StatusPill(label: label, background: background, foreground: foreground),
            if (period.status == PeriodStatus.recorded)
              Icon(Icons.chevron_right_rounded, color: scheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}

/// The absentees of one recorded period, read from the day so they follow every change.
class _AbsenteesSheet extends ConsumerStatefulWidget {
  const _AbsenteesSheet({required this.date, required this.classId, required this.number});

  final DateTime? date;
  final String classId;
  final int number;

  @override
  ConsumerState<_AbsenteesSheet> createState() => _AbsenteesSheetState();
}

class _AbsenteesSheetState extends ConsumerState<_AbsenteesSheet> {
  final Set<String> _selected = {};

  Future<void> _justify() async {
    final l = AppLocalizations.of(context);
    final ids = [..._selected];
    final done = await showActionSheet(
      context,
      JustifySheet(
        count: ids.length,
        send: (reason, note) =>
            ref.read(dmRepositoryProvider).justify(reason: reason, note: note, absenceIds: ids, latenessIds: []),
      ),
    );
    if (done && mounted) {
      setState(_selected.clear);
      dayChanged(context, ref, widget.date, l.justifiedCount(ids.length));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final period = ref
        .watch(dayProvider(widget.date))
        .value
        ?.classes
        .where((c) => c.classId == widget.classId)
        .firstOrNull
        ?.periods
        .where((p) => p.number == widget.number)
        .firstOrNull;
    if (period == null) {
      return const Padding(
        padding: EdgeInsets.all(Gap.xl),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: Gap.l),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Gap.l),
            child: Text(l.dayAbsentees(period.number), style: theme.textTheme.titleLarge),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.xs, Gap.l, Gap.s),
            child: Text(
              [?period.subject, ?period.teacherName].join(' · '),
              style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
          Flexible(
            child: ListView(
              shrinkWrap: true,
              children: [
                for (final absence in period.absences)
                  _RecordRow(
                    key: ValueKey('day-absence-row-${absence.recordId}'),
                    toggleKey: ValueKey('day-absence-${absence.recordId}'),
                    name: absence.fullName,
                    detail: absence.matricule,
                    state: absence.state,
                    lateness: false,
                    selected: _selected.contains(absence.recordId),
                    onToggle: absence.state == RecordState.unjustified
                        ? () => setState(
                            () => _selected.contains(absence.recordId)
                                ? _selected.remove(absence.recordId)
                                : _selected.add(absence.recordId),
                          )
                        : null,
                    menuKey: ValueKey('day-record-menu-${absence.recordId}'),
                    onCancel: () async {
                      if (await cancelDayRecord(
                            context,
                            ref,
                            widget.date,
                            recordId: absence.recordId,
                            lateness: false,
                            what: '${l.eventAbsence}, ${absence.fullName}, ${l.dayPeriodShort(period.number)}',
                          ) &&
                          mounted) {
                        setState(() => _selected.remove(absence.recordId));
                      }
                    },
                    onHistory: () {
                      Navigator.of(context).pop();
                      context.push(underTab(context, 'student/${absence.studentId}'));
                    },
                  ),
              ],
            ),
          ),
          if (_selected.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, 0),
              child: FilledButton.icon(
                key: const ValueKey('day-justify'),
                onPressed: _justify,
                icon: const Icon(Icons.verified_outlined),
                label: Text(l.justifyCount(_selected.length)),
              ),
            ),
        ],
      ),
    );
  }
}

/// One absence or lateness. Unjustified ones can be ticked; cancelled ones are struck through, with
/// no checkbox and no menu (BR-15).
class _RecordRow extends StatelessWidget {
  const _RecordRow({
    super.key,
    this.toggleKey,
    required this.name,
    required this.detail,
    required this.state,
    required this.lateness,
    required this.selected,
    required this.onToggle,
    required this.menuKey,
    required this.onCancel,
    required this.onHistory,
  });

  final Key? toggleKey;
  final String name;
  final String detail;
  final RecordState state;
  final bool lateness;
  final bool selected;
  final VoidCallback? onToggle;
  final Key menuKey;
  final VoidCallback onCancel;
  final VoidCallback onHistory;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final cancelled = state == RecordState.cancelled;
    final muted = scheme.onSurfaceVariant;
    final pill = switch (state) {
      RecordState.unjustified => StatusPill(
        label: lateness ? l.latenessUnjustified : l.absenceUnjustified,
        background: lateness ? status.lateContainer : status.absentContainer,
        foreground: lateness ? status.onLateContainer : status.onAbsentContainer,
      ),
      RecordState.justified => StatusPill(
        label: lateness ? l.latenessJustified : l.absenceJustified,
        icon: Icons.check_rounded,
        background: status.presentContainer,
        foreground: status.present,
      ),
      RecordState.cancelled => StatusPill(
        label: lateness ? l.dayLatenessCancelled : l.dayCancelled,
        background: scheme.surfaceContainerHigh,
        foreground: muted,
      ),
    };
    final row = Padding(
      padding: const EdgeInsets.fromLTRB(Gap.xs, Gap.xs, 0, Gap.xs),
      child: Row(
        children: [
          if (onToggle != null) Checkbox(value: selected, onChanged: (_) => onToggle!()) else const SizedBox(width: 48),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: theme.textTheme.titleSmall?.copyWith(
                    color: cancelled ? muted : null,
                    decoration: cancelled ? TextDecoration.lineThrough : null,
                  ),
                ),
                Text(
                  detail,
                  style: theme.textTheme.bodyMedium?.copyWith(color: muted, fontFeatures: tabular),
                ),
              ],
            ),
          ),
          pill,
          if (cancelled)
            const SizedBox(width: 48)
          else
            PopupMenuButton<void>(
              key: menuKey,
              tooltip: l.recordMenu,
              itemBuilder: (context) => [
                PopupMenuItem<void>(
                  onTap: onHistory,
                  child: ListTile(
                    leading: const Icon(Icons.history_rounded),
                    title: Text(l.daySeeHistory),
                    contentPadding: EdgeInsets.zero,
                  ),
                ),
                PopupMenuItem<void>(
                  onTap: onCancel,
                  child: ListTile(
                    leading: Icon(Icons.undo_rounded, color: scheme.error),
                    title: Text(l.cancelRecordAction),
                    contentPadding: EdgeInsets.zero,
                  ),
                ),
              ],
            ),
        ],
      ),
    );
    return Material(
      color: selected ? scheme.primaryContainer : Colors.transparent,
      child: onToggle == null ? row : InkWell(key: toggleKey, onTap: onToggle, child: row),
    );
  }
}
