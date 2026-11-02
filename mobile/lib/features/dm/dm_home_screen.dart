import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import '../lateness/gate_screen.dart';
import '../lateness/lateness.dart';
import 'alert_screen.dart' show evidenceText, ruleTitle;
import 'dm.dart';

/// The DM's home: the open risk alerts of their classes, to decide one by one (UC-D2), a way to
/// find any student of those classes (D35), and today's lateness in them (UC-AD2). Each leads to
/// the student's history (UC-D8). The app shows; the DM decides (BR-16 to BR-19).
class DmHomeScreen extends ConsumerWidget {
  const DmHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    final alerts = ref.watch(openAlertsProvider);
    final day = ref.watch(latenessDayProvider);
    final entries = day.value?.entries ?? const <LatenessEntry>[];
    final rows = mergeDay(server: entries, local: const [], students: const {});

    Future<void> refresh() =>
        Future.wait([ref.refresh(openAlertsProvider.future), ref.refresh(latenessDayProvider.future)])
            .then((_) {}, onError: (_) {});

    return RefreshIndicator(
      onRefresh: refresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: Gap.xl),
        children: [
          HeaderBand(
            children: [
              Text(
                DateFormat.yMMMMEEEEd(locale).format(now),
                style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted),
              ),
              const SizedBox(height: Gap.xs),
              // the count is not known yet: "no alert to decide" would be wrong
              if (alerts.value case final list?)
                Text(
                  l.alertsToDecide(list.length),
                  key: const ValueKey('alerts-count'),
                  style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader, fontFeatures: tabular),
                )
              else
                const Skeleton(child: Bone(width: 220, height: 32, onDark: true)),
              Padding(
                padding: const EdgeInsets.only(top: Gap.xs),
                child: Text(l.inYourClasses, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted)),
              ),
              const SizedBox(height: Gap.l),
              Material(
                color: theme.colorScheme.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(Radii.m),
                child: InkWell(
                  key: const ValueKey('open-search'),
                  borderRadius: BorderRadius.circular(Radii.m),
                  onTap: () => context.push('/home/alerts/students'),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: Gap.m, vertical: Gap.m + 2),
                    child: Row(
                      children: [
                        Icon(Icons.search_rounded, color: theme.colorScheme.onSurfaceVariant),
                        const SizedBox(width: Gap.m),
                        Expanded(
                          child: Text(
                            l.findStudent,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Gap.l),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SectionHeader(title: l.alertsTitle),
                switch (alerts) {
                  AsyncValue(value: final list?) when list.isNotEmpty => _AlertList(alerts: list),
                  AsyncValue(value: final _?) => Card(
                    child: EmptyState(
                      icon: Icons.notification_important_outlined,
                      title: l.noAlertsTitle,
                      body: l.noAlertsBody,
                    ),
                  ),
                  AsyncError(:final error) => FailureView(
                    message: failureText(error, l),
                    onRetry: () => ref.invalidate(openAlertsProvider),
                  ),
                  _ => const Skeleton(child: SkeletonRows(count: 3)),
                },
                SectionHeader(title: l.latenessListTitle),
                if (day.value?.fetchedAt case final fetchedAt?) ...[
                  InfoBanner(
                    icon: Icons.cloud_off_rounded,
                    text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                    background: theme.colorScheme.secondaryContainer,
                    foreground: theme.colorScheme.onSecondaryContainer,
                  ),
                  const SizedBox(height: Gap.m),
                ],
                switch (day) {
                  AsyncError(:final error) when rows.isEmpty => FailureView(
                    message: failureText(error, l),
                    onRetry: () => ref.invalidate(latenessDayProvider),
                  ),
                  AsyncLoading() when rows.isEmpty => const Skeleton(child: SkeletonRows(count: 4)),
                  _ when rows.isEmpty => Card(
                    child: EmptyState(icon: Icons.door_front_door_outlined, title: l.noLatenessToday),
                  ),
                  _ => LatenessList(rows: rows, onTap: (row) => context.push('/home/alerts/student/${row.studentId}')),
                },
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// The open alerts, the longest waiting first (D30): who, which rule, and what it found.
class _AlertList extends StatelessWidget {
  const _AlertList({required this.alerts});

  final List<RiskAlert> alerts;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    return Card(
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (final (i, alert) in alerts.indexed) ...[
            if (i > 0) const Divider(indent: 68),
            InkWell(
              key: ValueKey('alert-${alert.id}'),
              onTap: () => context.push('/home/alerts/alert/${alert.id}'),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.s, Gap.m),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    InitialsAvatar(
                      firstName: alert.firstName,
                      lastName: alert.lastName,
                      icon: alert.rule == AlertRule.consecutiveLateness
                          ? Icons.schedule_rounded
                          : Icons.event_busy_rounded,
                      background: alert.rule == AlertRule.consecutiveLateness
                          ? status.lateContainer
                          : status.absentContainer,
                      foreground: alert.rule == AlertRule.consecutiveLateness ? status.onLateContainer : status.absent,
                    ),
                    const SizedBox(width: Gap.m),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(alert.fullName, style: theme.textTheme.titleMedium),
                          Text(
                            '${ruleTitle(alert.rule, l)} · ${alert.className}',
                            style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            evidenceText(alert, l, locale),
                            style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right_rounded, color: scheme.onSurfaceVariant),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
