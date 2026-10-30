import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'inbox_screen.dart' show dayHeading;
import 'parent.dart';

/// UC-P3: a child's absences and lateness over the last 90 days, with the totals first and then
/// day by day. Absences and lateness are shown side by side, never merged (BR-8, BR-9); cancelled
/// records are not there (BR-15). In plain words, for a parent with no training (NFR-2).
class ChildHistoryScreen extends ConsumerWidget {
  const ChildHistoryScreen({super.key, required this.childId});

  final String childId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final history = ref.watch(childHistoryProvider(childId));
    // the name is known from the children list while the history loads
    final child = ref.watch(childrenProvider).value?.value.where((c) => c.id == childId).firstOrNull;
    final name = history.value?.firstName ?? child?.firstName;
    return Scaffold(
      appBar: AppBar(title: Text(name == null ? l.historyTitle : l.historyOf(name))),
      body: switch (history) {
        AsyncValue(value: final history?) => RefreshIndicator(
          onRefresh: () => ref.refresh(childHistoryProvider(childId).future).then((_) {}, onError: (_) {}),
          child: _HistoryList(history: history),
        ),
        AsyncError(:final error) => FailureView(
          message: failureText(error, l),
          onRetry: () => ref.invalidate(childHistoryProvider(childId)),
        ),
        _ => const _HistorySkeleton(),
      },
    );
  }
}

class _HistoryList extends ConsumerWidget {
  const _HistoryList({required this.history});

  final ChildHistory history;

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
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: Gap.xl),
      children: [
        HeaderBand(
          children: [
            Text(period, style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted)),
            const SizedBox(height: Gap.m),
            IntrinsicHeight(
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: _Total(
                      key: const ValueKey('total-absences'),
                      count: history.absentPeriods,
                      label: l.absentPeriodsLabel(history.absentPeriods),
                      detail: history.absentPeriods == 0
                          ? null
                          : l.unjustifiedAbsences(history.unjustifiedAbsentPeriods),
                    ),
                  ),
                  const SizedBox(width: Gap.m),
                  Expanded(
                    child: _Total(
                      key: const ValueKey('total-lateness'),
                      count: history.lateness,
                      label: l.latenessLabel(history.lateness),
                      detail: history.lateness == 0 ? null : l.unjustifiedLateness(history.unjustifiedLateness),
                    ),
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
              if (history.fetchedAt case final fetchedAt?) ...[
                const SizedBox(height: Gap.l),
                InfoBanner(
                  icon: Icons.cloud_off_rounded,
                  text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                  background: theme.colorScheme.secondaryContainer,
                  foreground: theme.colorScheme.onSecondaryContainer,
                ),
              ],
              if (history.days.isEmpty)
                EmptyState(icon: Icons.verified_outlined, title: l.historyEmpty(history.firstName), body: period)
              else ...[
                SectionHeader(title: l.dayByDay),
                for (final day in history.days) ...[
                  _DayCard(day: day, heading: dayHeading(day.date, now, l, locale)),
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

/// A total in the header: the figure, what it counts, and how many are not justified.
class _Total extends StatelessWidget {
  const _Total({super.key, required this.count, required this.label, required this.detail});

  final int count;
  final String label;
  final String? detail;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    return Semantics(
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
                style: theme.textTheme.headlineMedium?.copyWith(
                  color: status.onHeader,
                  fontWeight: FontWeight.w700,
                  fontFeatures: tabular,
                ),
              ),
              Text(label, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeader)),
              if (detail case final detail?) ...[
                const SizedBox(height: 2),
                Text(detail, style: theme.textTheme.bodySmall?.copyWith(color: status.onHeaderMuted)),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// One day: each absent lesson, then the lateness at the gate, each with whether it is justified.
class _DayCard extends StatelessWidget {
  const _DayCard({required this.day, required this.heading});

  final HistoryDay day;
  final String heading;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final y = day.date;
    final key = '${y.year}-${y.month.toString().padLeft(2, '0')}-${y.day.toString().padLeft(2, '0')}';
    return Card(
      key: ValueKey('day-$key'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.s),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Semantics(header: true, child: Text(heading, style: theme.textTheme.titleSmall)),
            const SizedBox(height: Gap.xs),
            if (day.lateness case final late?)
              _Line(
                icon: Icons.schedule_rounded,
                color: status.onLateContainer,
                background: status.lateContainer,
                title: l.eventLateness,
                detail: l.lateArrivedAt(late.arrivalTime.toString()),
                justified: late.justified,
                justifiedText: l.latenessJustified,
                unjustifiedText: l.latenessUnjustified,
              ),
            for (final absence in day.absences)
              _Line(
                icon: Icons.event_busy_rounded,
                color: status.absent,
                background: status.absentContainer,
                title: l.eventAbsence,
                detail: l.absentFromLesson(absence.startTime.toString()),
                justified: absence.justified,
                justifiedText: l.absenceJustified,
                unjustifiedText: l.absenceUnjustified,
              ),
          ],
        ),
      ),
    );
  }
}

class _Line extends StatelessWidget {
  const _Line({
    required this.icon,
    required this.color,
    required this.background,
    required this.title,
    required this.detail,
    required this.justified,
    required this.justifiedText,
    required this.unjustifiedText,
  });

  final IconData icon;
  final Color color;
  final Color background;

  /// What it is ("Absence"), then when ("Lesson at 08:25"): the time never wraps alone.
  final String title;
  final String detail;
  final bool justified;
  final String justifiedText;
  final String unjustifiedText;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: Gap.s),
      child: Row(
        children: [
          ExcludeSemantics(
            child: Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(color: background, shape: BoxShape.circle),
              child: Icon(icon, size: 18, color: color),
            ),
          ),
          const SizedBox(width: Gap.m),
          Expanded(
            child: MergeSemantics(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: theme.textTheme.titleSmall),
                  Text(
                    detail,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                      fontFeatures: tabular,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: Gap.s),
          justified
              ? StatusPill(
                  label: justifiedText,
                  icon: Icons.check_rounded,
                  background: status.presentContainer,
                  foreground: status.present,
                )
              : StatusPill(
                  label: unjustifiedText,
                  background: theme.colorScheme.surfaceContainerHigh,
                  foreground: theme.colorScheme.onSurfaceVariant,
                ),
        ],
      ),
    );
  }
}

class _HistorySkeleton extends StatelessWidget {
  const _HistorySkeleton();

  @override
  Widget build(BuildContext context) {
    Widget total() => DecoratedBox(
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(Radii.l),
      ),
      child: const Padding(
        padding: EdgeInsets.all(Gap.m),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Bone(width: 40, height: 32, onDark: true),
            SizedBox(height: Gap.s),
            Bone(width: 110, height: 14, onDark: true),
            SizedBox(height: Gap.s),
            Bone(width: 90, height: 10, onDark: true),
          ],
        ),
      ),
    );
    Widget day() => const Card(
      child: Padding(
        padding: EdgeInsets.all(Gap.l),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Bone(width: 140, height: 14),
            SizedBox(height: Gap.m),
            Row(
              children: [
                Bone.circle(size: 32),
                SizedBox(width: Gap.m),
                Expanded(child: Bone(height: 14)),
                SizedBox(width: Gap.xl),
                Bone(width: 80, height: 26, radius: Radii.l),
              ],
            ),
          ],
        ),
      ),
    );
    return Skeleton(
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        children: [
          HeaderBand(
            children: [
              const Bone(width: 200, height: 14, onDark: true),
              const SizedBox(height: Gap.m),
              Row(
                children: [
                  Expanded(child: total()),
                  const SizedBox(width: Gap.m),
                  Expanded(child: total()),
                ],
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.xl, Gap.l, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Align(alignment: AlignmentDirectional.centerStart, child: Bone(width: 120, height: 14)),
                const SizedBox(height: Gap.m),
                for (var i = 0; i < 3; i++) ...[day(), const SizedBox(height: Gap.m)],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
