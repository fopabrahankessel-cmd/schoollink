import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import '../shell/tab_path.dart';
import 'alert_screen.dart' show ruleTitle;
import 'dm.dart';

/// UC-D2, D41: the alerts the DM already decided, with the decision and its day. Each opens the
/// student page. Nothing here can be decided again.
class ClosedAlertsScreen extends ConsumerWidget {
  const ClosedAlertsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.closedAlertsTitle)),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(closedAlertsProvider.future).then((_) {}, onError: (_) {}),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, Gap.xl),
          children: [
            switch (ref.watch(closedAlertsProvider)) {
              AsyncValue(value: final list?) when list.isNotEmpty => Card(
                margin: EdgeInsets.zero,
                clipBehavior: Clip.antiAlias,
                child: Column(
                  children: [
                    for (final (i, alert) in list.indexed) ...[
                      if (i > 0) const Divider(indent: 68),
                      _ClosedAlert(alert: alert),
                    ],
                  ],
                ),
              ),
              AsyncValue(value: final _?) => Card(
                child: EmptyState(icon: Icons.task_alt_rounded, title: l.noClosedAlerts),
              ),
              AsyncError(:final error) => FailureView(
                message: failureText(error, l),
                onRetry: () => ref.invalidate(closedAlertsProvider),
              ),
              _ => const Skeleton(child: SkeletonRows(count: 4)),
            },
          ],
        ),
      ),
    );
  }
}

class _ClosedAlert extends StatelessWidget {
  const _ClosedAlert({required this.alert});

  final RiskAlert alert;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final decidedAt = alert.decidedAt;
    return InkWell(
      key: ValueKey('closed-${alert.id}'),
      onTap: () => context.push(underTab(context, 'student/${alert.studentId}')),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.s, Gap.m),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            InitialsAvatar(firstName: alert.firstName, lastName: alert.lastName),
            const SizedBox(width: Gap.m),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(alert.fullName, style: theme.textTheme.titleMedium),
                  Text('${ruleTitle(alert.rule, l)} · ${alert.className}', style: theme.textTheme.bodyMedium),
                  if (alert.decision case final decision?)
                    Text(
                      decidedLabel(decision, l),
                      style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600, color: scheme.primary),
                    ),
                  if (decidedAt != null)
                    Text(
                      l.decidedOn(DateFormat.MMMd(locale).format(decidedAt)),
                      style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
                    ),
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: scheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}

/// What became of the alert, said as done: "Parents convoqués".
String decidedLabel(Decision decision, AppLocalizations l) => switch (decision) {
  Decision.summon => l.decidedSummon,
  Decision.dismiss => l.decidedDismiss,
  Decision.disciplinary => l.decidedDisciplinary,
  Decision.refer => l.decidedRefer,
};
