import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'discipline.dart';
import 'discipline_sheets.dart';
import 'dm.dart';
import 'dm_sheets.dart';

/// UC-D5, D37: opens the sheet to record an incident for [studentId]; the list is read again once
/// the server has it. Used by the student page's button.
Future<void> recordIncident(BuildContext context, WidgetRef ref, String studentId) async {
  final l = AppLocalizations.of(context);
  final messenger = ScaffoldMessenger.of(context);
  final repository = ref.read(dmRepositoryProvider);
  final now = ref.read(clockProvider)();
  final done = await showActionSheet(
    context,
    RecordIncidentSheet(
      today: DateTime(now.year, now.month, now.day),
      send: (category, measure, day, description) => repository.recordIncident(
        studentId,
        category: category,
        measure: measure,
        occurredOn: day,
        description: description,
      ),
    ),
  );
  if (done) {
    ref.invalidate(disciplinaryRecordsProvider(studentId));
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(l.incidentRecorded)));
  }
}

/// UC-D5, D37: the student's incidents, newest first. Tapping one offers to close it (once) or to
/// cancel it when it was recorded by mistake.
class DisciplineTab extends ConsumerWidget {
  const DisciplineTab({super.key, required this.studentId});

  final String studentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return switch (ref.watch(disciplinaryRecordsProvider(studentId))) {
      AsyncValue(value: final records?) => RefreshIndicator(
        onRefresh: () => ref.refresh(disciplinaryRecordsProvider(studentId).future).then((_) {}, onError: (_) {}),
        child: records.isEmpty
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  EmptyState(icon: Icons.gavel_outlined, title: l.noIncidents, body: l.noIncidentsBody),
                ],
              )
            : ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                // room for the floating button over the last card
                padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, 96),
                children: [
                  for (final record in records)
                    Padding(
                      padding: const EdgeInsets.only(bottom: Gap.m),
                      child: _IncidentCard(
                        record: record,
                        onTap: () => _actions(context, ref, record),
                      ),
                    ),
                ],
              ),
      ),
      AsyncError(:final error) => FailureView(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(disciplinaryRecordsProvider(studentId)),
      ),
      _ => const Skeleton(child: SkeletonRows(count: 4)),
    };
  }

  Future<void> _actions(BuildContext context, WidgetRef ref, DisciplinaryRecord record) async {
    final l = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final repository = ref.read(dmRepositoryProvider);
    final what = '${categoryLabel(record.category, l)}, ${record.description}';
    final choice = await showModalBottomSheet<_Action>(
      context: context,
      showDragHandle: true,
      useSafeArea: true,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (record.open)
              ListTile(
                leading: const Icon(Icons.task_alt_rounded),
                title: Text(l.closeIncident),
                onTap: () => Navigator.pop(context, _Action.close),
              ),
            ListTile(
              leading: Icon(Icons.delete_outline_rounded, color: Theme.of(context).colorScheme.error),
              title: Text(l.cancelIncident),
              onTap: () => Navigator.pop(context, _Action.cancel),
            ),
          ],
        ),
      ),
    );
    if (choice == null || !context.mounted) {
      return;
    }
    final done = await showActionSheet(
      context,
      switch (choice) {
        _Action.close => CloseIncidentSheet(what: what, send: (note) => repository.closeIncident(record.id, note: note)),
        _Action.cancel => CancelRecordSheet(
          what: what,
          body: l.cancelIncidentBody,
          send: (reason) => repository.cancelIncident(record.id, reason),
        ),
      },
    );
    if (done) {
      ref.invalidate(disciplinaryRecordsProvider(studentId));
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(content: Text(choice == _Action.close ? l.incidentClosedDone : l.incidentCancelled)),
        );
    }
  }
}

enum _Action { close, cancel }

class _IncidentCard extends StatelessWidget {
  const _IncidentCard({required this.record, required this.onTap});

  final DisciplinaryRecord record;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final muted = theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final locale = Localizations.localeOf(context).toLanguageTag();
    return Card(
      key: ValueKey('incident-${record.id}'),
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(Gap.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(child: Text(categoryLabel(record.category, l), style: theme.textTheme.titleMedium)),
                  StatusPill(
                    label: record.open ? l.incidentOpen : l.incidentClosed,
                    background: record.open ? status.lateContainer : status.presentContainer,
                    foreground: record.open ? status.onLateContainer : status.present,
                  ),
                ],
              ),
              const SizedBox(height: Gap.xs),
              Text(DateFormat.yMMMd(locale).format(record.occurredOn), style: muted),
              if (record.measure != DisciplinaryMeasure.none)
                Text(measureLabel(record.measure, l), style: theme.textTheme.bodyMedium),
              const SizedBox(height: Gap.s),
              Text(record.description, style: theme.textTheme.bodyLarge),
              if (record.closeNote case final note?) ...[
                const SizedBox(height: Gap.s),
                Text(l.incidentCloseNote(note), style: muted),
              ],
              const SizedBox(height: Gap.s),
              Text(l.incidentRecordedBy(record.recordedByName), style: theme.textTheme.bodySmall),
            ],
          ),
        ),
      ),
    );
  }
}
