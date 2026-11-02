import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import '../lateness/lateness.dart' show latenessDayProvider;
import '../parent/inbox_screen.dart' show dayHeading;
import '../parent/parent.dart' show HistoryDay;
import 'dm.dart';
import 'dm_sheets.dart';

/// A record of the history: an absent lesson or a lateness, apart (BR-8, BR-9).
typedef _Record = ({bool lateness, String id});

/// UC-D8: a student's absences and lateness for the DM, day by day. From here the DM justifies
/// several records at once (UC-D1, BR-12), cancels one entered by mistake (UC-D6, BR-13) or
/// summons the parents (UC-D3). Cancelled records are not shown (BR-15).
class StudentHistoryScreen extends ConsumerStatefulWidget {
  const StudentHistoryScreen({super.key, required this.studentId});

  final String studentId;

  @override
  ConsumerState<StudentHistoryScreen> createState() => _StudentHistoryScreenState();
}

class _StudentHistoryScreenState extends ConsumerState<StudentHistoryScreen> {
  final Set<_Record> _selected = {};

  void _toggle(_Record record) =>
      setState(() => _selected.contains(record) ? _selected.remove(record) : _selected.add(record));

  /// After a change: the history, the alerts and the day's lateness are read again.
  void _changed(String message) {
    ref
      ..invalidate(studentHistoryProvider(widget.studentId))
      ..invalidate(openAlertsProvider)
      ..invalidate(latenessDayProvider);
    setState(_selected.clear);
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _justify() async {
    final l = AppLocalizations.of(context);
    final repository = ref.read(dmRepositoryProvider);
    final records = [..._selected];
    final done = await showActionSheet(
      context,
      JustifySheet(
        count: records.length,
        send: (reason, note) => repository.justify(
          reason: reason,
          note: note,
          absenceIds: [
            for (final r in records)
              if (!r.lateness) r.id,
          ],
          latenessIds: [
            for (final r in records)
              if (r.lateness) r.id,
          ],
        ),
      ),
    );
    if (done && mounted) {
      _changed(l.justifiedCount(records.length));
    }
  }

  Future<void> _cancel(_Record record, String what) async {
    final l = AppLocalizations.of(context);
    final repository = ref.read(dmRepositoryProvider);
    final done = await showActionSheet(
      context,
      CancelRecordSheet(
        what: what,
        send: (reason) => record.lateness
            ? repository.cancelLateness(record.id, reason)
            : repository.cancelAbsence(record.id, reason),
      ),
    );
    if (done && mounted) {
      _changed(l.recordCancelled);
    }
  }

  Future<void> _summon(StudentHistory history) async {
    final l = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final repository = ref.read(dmRepositoryProvider);
    SummonsSent? sent;
    final done = await showActionSheet(
      context,
      SummonSheet(
        studentName: history.firstName,
        reasonRequired: true,
        now: ref.read(clockProvider)(),
        send: (meeting, reason) async =>
            sent = await repository.summon(history.studentId, reason: reason, meeting: meeting),
      ),
    );
    if (done && mounted && sent != null) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              l.summonsSent(DateFormat.MMMMEEEEd(locale).format(sent!.meetingDate), sent!.meetingTime.toString()),
            ),
          ),
        );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final history = ref.watch(studentHistoryProvider(widget.studentId));
    final loaded = history.value;
    return Scaffold(
      appBar: AppBar(
        title: Text(loaded == null ? l.historyTitle : loaded.fullName),
        actions: [
          if (loaded != null)
            IconButton(
              key: const ValueKey('history-summon'),
              tooltip: l.decisionSummon,
              icon: const Icon(Icons.campaign_outlined),
              onPressed: () => _summon(loaded),
            ),
        ],
      ),
      body: switch (history) {
        AsyncValue(value: final history?) => RefreshIndicator(
          onRefresh: () => ref.refresh(studentHistoryProvider(widget.studentId).future).then((_) {}, onError: (_) {}),
          child: _HistoryList(history: history, selected: _selected, onToggle: _toggle, onCancel: _cancel),
        ),
        AsyncError(:final error) => FailureView(
          message: failureText(error, l),
          onRetry: () => ref.invalidate(studentHistoryProvider(widget.studentId)),
        ),
        _ => const Skeleton(child: SkeletonRows(count: 5)),
      },
      bottomNavigationBar: _selected.isEmpty
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(Gap.l, Gap.s, Gap.l, Gap.m),
                child: Row(
                  children: [
                    IconButton(
                      tooltip: l.clearSelection,
                      icon: const Icon(Icons.close_rounded),
                      onPressed: () => setState(_selected.clear),
                    ),
                    const SizedBox(width: Gap.s),
                    Expanded(
                      child: FilledButton.icon(
                        key: const ValueKey('justify-selected'),
                        onPressed: _justify,
                        icon: const Icon(Icons.verified_outlined),
                        label: Text(l.justifyCount(_selected.length)),
                        style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}

class _HistoryList extends ConsumerWidget {
  const _HistoryList({required this.history, required this.selected, required this.onToggle, required this.onCancel});

  final StudentHistory history;
  final Set<_Record> selected;
  final void Function(_Record record) onToggle;
  final void Function(_Record record, String what) onCancel;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    final period = l.periodFromTo(
      DateFormat.MMMMd(locale).format(history.from),
      DateFormat.MMMMd(locale).format(history.to),
    );

    Widget total(String key, int count, String label, String? detail) => Expanded(
      child: Semantics(
        container: true,
        child: DecoratedBox(
          key: ValueKey(key),
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
                Text(label, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeader)),
                if (detail case final detail?)
                  Text(detail, style: theme.textTheme.bodySmall?.copyWith(color: status.onHeaderMuted)),
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
            Text(
              l.classAndMatricule(history.className, history.matricule),
              style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted),
            ),
            Text(period, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted)),
            const SizedBox(height: Gap.m),
            IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  total(
                    'total-absences',
                    history.absentPeriods,
                    l.absentPeriodsLabel(history.absentPeriods),
                    history.absentPeriods == 0 ? null : l.unjustifiedAbsences(history.unjustifiedAbsentPeriods),
                  ),
                  const SizedBox(width: Gap.m),
                  total(
                    'total-lateness',
                    history.lateness,
                    l.latenessLabel(history.lateness),
                    history.lateness == 0 ? null : l.unjustifiedLateness(history.unjustifiedLateness),
                  ),
                ],
              ),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (history.days.isEmpty)
                EmptyState(icon: Icons.verified_outlined, title: l.historyEmpty(history.firstName), body: period)
              else ...[
                SectionHeader(title: l.dayByDay),
                Padding(
                  padding: const EdgeInsets.only(left: Gap.xs, right: Gap.xs, bottom: Gap.m),
                  child: Text(
                    l.selectToJustify,
                    style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ),
                for (final day in history.days) ...[
                  _DayCard(
                    day: day,
                    heading: dayHeading(day.date, now, l, locale),
                    selected: selected,
                    onToggle: onToggle,
                    onCancel: onCancel,
                  ),
                  const SizedBox(height: Gap.m),
                ],
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// One day: the lateness at the gate, then each absent lesson, each one selectable.
class _DayCard extends StatelessWidget {
  const _DayCard({
    required this.day,
    required this.heading,
    required this.selected,
    required this.onToggle,
    required this.onCancel,
  });

  final HistoryDay day;
  final String heading;
  final Set<_Record> selected;
  final void Function(_Record record) onToggle;
  final void Function(_Record record, String what) onCancel;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.xs),
            child: Semantics(header: true, child: Text(heading, style: theme.textTheme.titleSmall)),
          ),
          if (day.lateness case final late? when late.recordId != null)
            _RecordLine(
              record: (lateness: true, id: late.recordId!),
              title: l.eventLateness,
              detail: l.lateArrivedAt(late.arrivalTime.toString()),
              justified: late.justified,
              justifiedText: l.latenessJustified,
              unjustifiedText: l.latenessUnjustified,
              selected: selected,
              onToggle: onToggle,
              onCancel: onCancel,
            ),
          for (final absence in day.absences)
            if (absence.recordId case final id?)
              _RecordLine(
                record: (lateness: false, id: id),
                title: l.eventAbsence,
                detail: l.absentFromLesson(absence.startTime.toString()),
                justified: absence.justified,
                justifiedText: l.absenceJustified,
                unjustifiedText: l.absenceUnjustified,
                selected: selected,
                onToggle: onToggle,
                onCancel: onCancel,
              ),
          const SizedBox(height: Gap.xs),
        ],
      ),
    );
  }
}

class _RecordLine extends StatelessWidget {
  const _RecordLine({
    required this.record,
    required this.title,
    required this.detail,
    required this.justified,
    required this.justifiedText,
    required this.unjustifiedText,
    required this.selected,
    required this.onToggle,
    required this.onCancel,
  });

  final _Record record;
  final String title;
  final String detail;
  final bool justified;
  final String justifiedText;
  final String unjustifiedText;
  final Set<_Record> selected;
  final void Function(_Record record) onToggle;
  final void Function(_Record record, String what) onCancel;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final isSelected = selected.contains(record);
    return Material(
      color: isSelected ? scheme.primaryContainer : Colors.transparent,
      child: InkWell(
        key: ValueKey('record-${record.id}'),
        onTap: () => onToggle(record),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Gap.xs, Gap.xs, 0, Gap.xs),
          child: Row(
            children: [
              Checkbox(value: isSelected, onChanged: (_) => onToggle(record)),
              Icon(
                record.lateness ? Icons.schedule_rounded : Icons.event_busy_rounded,
                size: 20,
                color: record.lateness ? status.late : status.absent,
              ),
              const SizedBox(width: Gap.s),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: theme.textTheme.titleSmall),
                    Text(
                      detail,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: scheme.onSurfaceVariant,
                        fontFeatures: tabular,
                      ),
                    ),
                  ],
                ),
              ),
              justified
                  ? StatusPill(
                      label: justifiedText,
                      icon: Icons.check_rounded,
                      background: status.presentContainer,
                      foreground: status.present,
                    )
                  : StatusPill(
                      label: unjustifiedText,
                      background: scheme.surfaceContainerHigh,
                      foreground: scheme.onSurfaceVariant,
                    ),
              PopupMenuButton<void>(
                key: ValueKey('record-menu-${record.id}'),
                tooltip: l.recordMenu,
                itemBuilder: (context) => [
                  PopupMenuItem<void>(
                    onTap: () => onCancel(record, '$title, $detail'),
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
        ),
      ),
    );
  }
}
