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
import '../lateness/lateness.dart' show latenessDayProvider;
import 'dm.dart';
import 'dm_sheets.dart';

/// UC-D2, UC-D3: one open alert, what its rule found, and the DM's four decisions (BR-18). Nothing
/// happens until the DM chooses: the app never decides (BR-16 to BR-19).
class AlertScreen extends ConsumerWidget {
  const AlertScreen({super.key, required this.alertId});

  final String alertId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final alerts = ref.watch(openAlertsProvider);
    final alert = alerts.value?.where((a) => a.id == alertId).firstOrNull;
    return Scaffold(
      appBar: AppBar(title: Text(alert?.fullName ?? l.alertTitle)),
      body: switch (alerts) {
        _ when alert != null => _AlertBody(alert: alert),
        AsyncError(:final error) => FailureView(
          message: failureText(error, l),
          onRetry: () => ref.invalidate(openAlertsProvider),
        ),
        AsyncLoading() => const Skeleton(child: SkeletonRows(count: 3)),
        _ => EmptyState(icon: Icons.task_alt_rounded, title: l.alertAlreadyDecided),
      },
    );
  }
}

class _AlertBody extends ConsumerWidget {
  const _AlertBody({required this.alert});

  final RiskAlert alert;

  Future<void> _decide(BuildContext context, WidgetRef ref, Decision decision) async {
    final l = AppLocalizations.of(context);
    final repository = ref.read(dmRepositoryProvider);
    final messenger = ScaffoldMessenger.of(context);
    final router = GoRouter.of(context);
    final done = await showActionSheet(
      context,
      decision == Decision.summon
          ? SummonSheet(
              studentName: alert.firstName,
              reasonRequired: false,
              now: ref.read(clockProvider)(),
              send: (meeting, reason) => repository.decide(alert.id, decision, note: reason, meeting: meeting),
            )
          : DecisionSheet(
              decision: decision,
              send: (note) => repository.decide(alert.id, decision, note: note),
            ),
    );
    if (!done) {
      return;
    }
    ref
      ..invalidate(openAlertsProvider)
      ..invalidate(latenessDayProvider);
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(decision == Decision.summon ? l.parentsSummoned : l.decisionRecorded)));
    if (router.canPop()) {
      router.pop();
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();

    Widget choice(Decision decision, IconData icon, String detail) => Card(
      key: ValueKey('decide-${decision.name}'),
      child: ListTile(
        minVerticalPadding: Gap.m,
        leading: Icon(icon, color: decision == Decision.summon ? scheme.primary : scheme.onSurfaceVariant),
        title: Text(decisionLabel(decision, l), style: theme.textTheme.titleMedium),
        subtitle: Text(detail),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: () => _decide(context, ref, decision),
      ),
    );

    return ListView(
      padding: const EdgeInsets.only(bottom: Gap.xl),
      children: [
        HeaderBand(
          children: [
            Text(
              l.classAndMatricule(alert.className, alert.matricule),
              style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted),
            ),
            const SizedBox(height: Gap.xs),
            Text(ruleTitle(alert.rule, l), style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader)),
            const SizedBox(height: Gap.xs),
            Text(
              l.alertRaisedOn(DateFormat.MMMMEEEEd(locale).format(alert.raisedAt)),
              style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SectionHeader(title: l.whatTheRuleFound),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(Gap.l),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(evidenceText(alert, l, locale), style: theme.textTheme.bodyLarge),
                      const SizedBox(height: Gap.s),
                      TextButton.icon(
                        key: const ValueKey('alert-history'),
                        onPressed: () => context.push('/home/alerts/student/${alert.studentId}'),
                        icon: const Icon(Icons.history_rounded),
                        label: Text(l.seeHistory),
                        style: TextButton.styleFrom(minimumSize: const Size(0, 48)),
                      ),
                    ],
                  ),
                ),
              ),
              SectionHeader(title: l.yourDecision),
              Padding(
                padding: const EdgeInsets.only(left: Gap.xs, right: Gap.xs, bottom: Gap.m),
                child: Text(
                  l.decisionHint,
                  style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                ),
              ),
              choice(Decision.summon, Icons.campaign_outlined, l.decisionSummonDetail),
              const SizedBox(height: Gap.s),
              choice(Decision.dismiss, Icons.do_not_disturb_on_outlined, l.decisionDismissDetail),
              const SizedBox(height: Gap.s),
              choice(Decision.disciplinary, Icons.gavel_rounded, l.decisionDisciplinaryDetail),
              const SizedBox(height: Gap.s),
              choice(Decision.refer, Icons.support_outlined, l.decisionReferDetail),
            ],
          ),
        ),
      ],
    );
  }
}

String ruleTitle(AlertRule rule, AppLocalizations l) => switch (rule) {
  AlertRule.fullDayAbsence => l.ruleFullDayAbsence,
  AlertRule.consecutiveLateness => l.ruleConsecutiveLateness,
  AlertRule.unjustifiedAbsences => l.ruleUnjustifiedAbsences,
  AlertRule.other => l.ruleOther,
};

/// What the rule saw, in words: the days of a streak, or the count of unjustified lessons.
String evidenceText(RiskAlert alert, AppLocalizations l, String locale) {
  String days(List<DateTime> dates) => dates.map(DateFormat.MMMd(locale).format).join(', ');
  final dates = alert.dates.isEmpty ? [alert.asOf] : alert.dates;
  return switch (alert.rule) {
    AlertRule.fullDayAbsence => l.evidenceFullDay(dates.length, days(dates)),
    AlertRule.consecutiveLateness => l.evidenceLateness(dates.length, days(dates)),
    AlertRule.unjustifiedAbsences => l.evidenceUnjustified(
      alert.periods ?? alert.threshold,
      DateFormat.MMMd(locale).format(alert.from ?? alert.asOf),
      DateFormat.MMMd(locale).format(alert.to ?? alert.asOf),
    ),
    AlertRule.other => l.ruleOther,
  };
}
