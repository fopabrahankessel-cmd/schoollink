import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import '../shell/tab_path.dart';
import 'alert_screen.dart' show evidenceText, ruleTitle;
import 'dm.dart';

/// UC-D2: the open risk alerts of the DM's classes, to decide one by one; opened from the Today
/// tab's count (D41). The app shows; the DM decides (BR-16 to BR-19).
class OpenAlertsScreen extends ConsumerWidget {
  const OpenAlertsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.openAlertsTitle)),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(openAlertsProvider.future).then((_) {}, onError: (_) {}),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, Gap.xl),
          children: [
            switch (ref.watch(openAlertsProvider)) {
              AsyncValue(value: final list?) when list.isNotEmpty => AlertList(alerts: list),
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
          ],
        ),
      ),
    );
  }
}

/// The open alerts, the longest waiting first (D30): who, which rule, and what it found.
class AlertList extends StatelessWidget {
  const AlertList({super.key, required this.alerts});

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
              onTap: () => context.push(underTab(context, 'alert/${alert.id}')),
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
