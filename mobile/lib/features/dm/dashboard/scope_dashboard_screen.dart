import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/providers.dart';
import '../../../core/theme.dart';
import '../../../core/ui/skeleton.dart';
import '../../../core/ui/widgets.dart';
import '../../../l10n/app_localizations.dart';
import '../../auth/failure_text.dart';
import '../alert_screen.dart' show ruleTitle;
import '../dm.dart';
import 'scope_dashboard_models.dart';

/// UC-D8, D34, D41: the DM's classes over a range, the last 30 days by default as on the web.
/// Four figures, the classes with the most absences first, and the alerts each rule raised.
/// Absences and lateness are shown apart, never merged (BR-8, BR-9).
class ScopeDashboardScreen extends ConsumerStatefulWidget {
  const ScopeDashboardScreen({super.key});

  /// The web's default too.
  static const defaultDays = 30;

  @override
  ConsumerState<ScopeDashboardScreen> createState() => _ScopeDashboardScreenState();
}

class _ScopeDashboardScreenState extends ConsumerState<ScopeDashboardScreen> {
  late DateSpan _span;

  @override
  void initState() {
    super.initState();
    final now = ref.read(clockProvider)();
    final today = DateTime(now.year, now.month, now.day);
    _span = (from: today.subtract(const Duration(days: ScopeDashboardScreen.defaultDays - 1)), to: today);
  }

  Future<void> _pickRange() async {
    final now = ref.read(clockProvider)();
    final today = DateTime(now.year, now.month, now.day);
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(today.year - 1, today.month, today.day),
      lastDate: today,
      initialDateRange: DateTimeRange(start: _span.from, end: _span.to),
    );
    if (picked != null && mounted) {
      setState(() => _span = (from: picked.start, to: picked.end));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final dashboard = ref.watch(scopeDashboardProvider(_span));
    final dateFormat = DateFormat.MMMd(locale);

    return Scaffold(
      appBar: AppBar(title: Text(l.dashboardTitle)),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(scopeDashboardProvider(_span).future).then((_) {}, onError: (_) {}),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, Gap.xl),
          children: [
            Align(
              alignment: Alignment.centerLeft,
              child: ActionChip(
                key: const ValueKey('dashboard-range'),
                avatar: const Icon(Icons.date_range_rounded, size: 18),
                label: Text(l.dashboardRange(dateFormat.format(_span.from), dateFormat.format(_span.to))),
                onPressed: _pickRange,
              ),
            ),
            const SizedBox(height: Gap.m),
            switch (dashboard) {
              AsyncValue(value: final data?) => _Content(data: data),
              AsyncError(:final error) => FailureView(
                message: failureText(error, l),
                onRetry: () => ref.invalidate(scopeDashboardProvider(_span)),
              ),
              _ => const Skeleton(child: SkeletonRows(count: 5)),
            },
            Padding(
              padding: const EdgeInsets.only(top: Gap.l),
              child: Text(
                l.dashboardCancelledNote,
                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Content extends StatelessWidget {
  const _Content({required this.data});

  final ScopeDashboard data;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final rate = data.totals.absenceRate;
    final most = data.classes.isEmpty ? 0 : data.classes.first.counts.absentPeriods;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: Gap.m,
          crossAxisSpacing: Gap.m,
          childAspectRatio: 1.6,
          children: [
            _Figure(
              key: const ValueKey('figure-rate'),
              label: l.kpiAbsenceRate,
              value: rate == null ? '—' : NumberFormat.percentPattern(locale).format(rate),
            ),
            _Figure(
              key: const ValueKey('figure-unjustified'),
              label: l.kpiUnjustified,
              value: '${data.totals.unjustifiedAbsentPeriods}',
            ),
            _Figure(key: const ValueKey('figure-lateness'), label: l.kpiLateness, value: '${data.totals.lateness}'),
            _Figure(key: const ValueKey('figure-open-alerts'), label: l.kpiOpenAlerts, value: '${data.openAlerts}'),
          ],
        ),
        SectionHeader(title: l.dashboardByClass),
        if (data.classes.isEmpty)
          Card(
            child: EmptyState(icon: Icons.groups_outlined, title: l.dashboardNoActivity),
          )
        else
          Card(
            margin: EdgeInsets.zero,
            child: Column(
              children: [
                for (final (i, c) in data.classes.indexed) ...[
                  if (i > 0) const Divider(height: 1),
                  Padding(
                    key: ValueKey('class-${c.classId}'),
                    padding: const EdgeInsets.all(Gap.l),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(c.name, style: theme.textTheme.titleMedium),
                        const SizedBox(height: Gap.xs),
                        Text(
                          l.dashboardClassLine(c.counts.absentPeriods, c.counts.lateness),
                          style: theme.textTheme.bodyMedium?.copyWith(fontFeatures: tabular),
                        ),
                        const SizedBox(height: Gap.s),
                        // the share of the class with the most absences: a reading aid, the number says it
                        ClipRRect(
                          borderRadius: BorderRadius.circular(2),
                          child: LinearProgressIndicator(
                            value: most == 0 ? 0 : c.counts.absentPeriods / most,
                            minHeight: 4,
                            color: scheme.primary,
                            backgroundColor: scheme.surfaceContainerHigh,
                            semanticsLabel: c.name,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ],
            ),
          ),
        SectionHeader(title: l.dashboardAlertsByRule),
        Card(
          margin: EdgeInsets.zero,
          child: Column(
            children: [
              for (final (i, r) in data.alerts.indexed) ...[
                if (i > 0) const Divider(height: 1),
                ListTile(
                  title: Text(ruleTitle(r.rule, l)),
                  subtitle: Text(l.dashboardRuleLine(r.raised, r.closed, r.open)),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _Figure extends StatelessWidget {
  const _Figure({super.key, required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(Gap.m),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(value, style: theme.textTheme.headlineSmall?.copyWith(fontFeatures: tabular)),
            const SizedBox(height: Gap.xs),
            Text(
              label,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }
}
