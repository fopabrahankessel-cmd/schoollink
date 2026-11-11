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
import '../shell/tab_path.dart';
import 'dm.dart';

/// D41: the DM's Today tab. Three counts, each opening its list: the alerts to decide (UC-D2), the
/// periods not recorded yet and the day's absences and lateness (UC-D10). Then the full-day report
/// (UC-D9). A count not read shows why and a retry, never 0: "nothing to do" would be wrong.
class TodayScreen extends ConsumerWidget {
  const TodayScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    final alerts = ref.watch(openAlertsProvider);
    final day = ref.watch(dayProvider(null));

    Future<void> refresh() =>
        Future.wait([ref.refresh(openAlertsProvider.future), ref.refresh(dayProvider(null).future)])
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
                _capitalised(DateFormat.yMMMMEEEEd(locale).format(now)),
                style: theme.textTheme.titleMedium?.copyWith(color: status.onHeader),
              ),
              Padding(
                padding: const EdgeInsets.only(top: Gap.xs),
                child: Text(l.inYourClasses, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted)),
              ),
              const SizedBox(height: Gap.l),
              Material(
                color: scheme.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(Radii.m),
                child: InkWell(
                  key: const ValueKey('open-search'),
                  borderRadius: BorderRadius.circular(Radii.m),
                  onTap: () => context.go('/home/students'),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: Gap.m, vertical: Gap.m + 2),
                    child: Row(
                      children: [
                        Icon(Icons.search_rounded, color: scheme.onSurfaceVariant),
                        const SizedBox(width: Gap.m),
                        Expanded(
                          child: Text(
                            l.findStudent,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.titleMedium?.copyWith(color: scheme.onSurfaceVariant),
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
                const SizedBox(height: Gap.l),
                _Count(
                  key: const ValueKey('today-alerts'),
                  retryKey: const ValueKey('today-retry-alerts'),
                  value: alerts.whenData((list) => list.length),
                  icon: Icons.notification_important_outlined,
                  text: l.todayAlerts,
                  urgent: (count) => count > 0,
                  background: status.absentContainer,
                  foreground: status.absent,
                  onTap: () => context.push(underTab(context, 'alerts')),
                  onRetry: () => ref.invalidate(openAlertsProvider),
                ),
                const SizedBox(height: Gap.m),
                _Count(
                  key: const ValueKey('today-not-recorded'),
                  retryKey: const ValueKey('today-retry-day'),
                  value: day.whenData((d) => d.notRecorded),
                  icon: Icons.edit_calendar_outlined,
                  text: l.todayNotRecorded,
                  urgent: (count) => count > 0,
                  background: status.lateContainer,
                  foreground: status.onLateContainer,
                  onTap: () => context.push(underTab(context, 'day')),
                  onRetry: () => ref.invalidate(dayProvider(null)),
                ),
                const SizedBox(height: Gap.m),
                // the same request as the periods: one retry is enough
                if (day is! AsyncError)
                  _Count(
                    key: const ValueKey('today-absences'),
                    retryKey: null,
                    value: day.whenData((d) => d.absentStudents),
                    icon: Icons.groups_outlined,
                    text: (absent) => l.todayAbsences(absent, day.value?.lateness ?? 0),
                    urgent: (_) => false,
                    background: scheme.primaryContainer,
                    foreground: scheme.onPrimaryContainer,
                    onTap: () => context.push(underTab(context, 'day')),
                    onRetry: () => ref.invalidate(dayProvider(null)),
                  ),
                SectionHeader(title: l.reportSectionTitle),
                Card(
                  margin: EdgeInsets.zero,
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    key: const ValueKey('open-full-day'),
                    minVerticalPadding: Gap.m,
                    leading: Icon(Icons.description_outlined, color: scheme.primary),
                    title: Text(l.fullDayEntry, style: theme.textTheme.titleMedium),
                    subtitle: Text(l.fullDayEntryHint),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () => context.push(underTab(context, 'full-day')),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

String _capitalised(String text) => text.isEmpty ? text : text[0].toUpperCase() + text.substring(1);

/// One count of the day, tappable to its list; a skeleton while it loads, the reason and a retry
/// when it could not be read.
class _Count extends StatelessWidget {
  const _Count({
    super.key,
    required this.retryKey,
    required this.value,
    required this.icon,
    required this.text,
    required this.urgent,
    required this.background,
    required this.foreground,
    required this.onTap,
    required this.onRetry,
  });

  final Key? retryKey;
  final AsyncValue<int> value;
  final IconData icon;
  final String Function(int count) text;
  final bool Function(int count) urgent;
  final Color background;
  final Color foreground;
  final VoidCallback onTap;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return switch (value) {
      AsyncValue(value: final count?) => Card(
        margin: EdgeInsets.zero,
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.m, Gap.l),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 22,
                  backgroundColor: urgent(count) ? background : scheme.surfaceContainerHigh,
                  child: Icon(icon, color: urgent(count) ? foreground : scheme.onSurfaceVariant),
                ),
                const SizedBox(width: Gap.m),
                Expanded(
                  child: Text(text(count), style: theme.textTheme.titleMedium?.copyWith(fontFeatures: tabular)),
                ),
                Icon(Icons.chevron_right_rounded, color: scheme.onSurfaceVariant),
              ],
            ),
          ),
        ),
      ),
      AsyncError(:final error) => Card(
        margin: EdgeInsets.zero,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.s, Gap.m),
          child: Row(
            children: [
              Icon(Icons.error_outline_rounded, color: scheme.error),
              const SizedBox(width: Gap.m),
              Expanded(child: Text(failureText(error, l), style: theme.textTheme.bodyMedium)),
              TextButton(key: retryKey, onPressed: onRetry, child: Text(l.retry)),
            ],
          ),
        ),
      ),
      _ => const Skeleton(child: Bone(width: double.infinity, height: 72)),
    };
  }
}
