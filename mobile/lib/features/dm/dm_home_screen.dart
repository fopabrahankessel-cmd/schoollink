import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import '../lateness/gate_screen.dart';
import '../lateness/lateness.dart';

/// The DM's home: the risk alerts to decide (UC-D2, phase 3) and, meanwhile, today's lateness in
/// the classes they are in charge of (UC-AD2, `GET /lateness`). Read only: the DM decides, the
/// app never does (BR-16 to BR-19).
class DmHomeScreen extends ConsumerWidget {
  const DmHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    final day = ref.watch(latenessDayProvider);
    final entries = day.value?.entries ?? const <LatenessEntry>[];
    final rows = mergeDay(server: entries, local: const [], students: const {});

    return RefreshIndicator(
      onRefresh: () => ref.refresh(latenessDayProvider.future).then((_) {}, onError: (_) {}),
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
              Text(
                l.latenessToday(rows.length),
                style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader, fontFeatures: tabular),
              ),
              Padding(
                padding: const EdgeInsets.only(top: Gap.xs),
                child: Text(l.inYourClasses, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted)),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Gap.l),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (day.value?.fetchedAt case final fetchedAt?) ...[
                  const SizedBox(height: Gap.l),
                  InfoBanner(
                    icon: Icons.cloud_off_rounded,
                    text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                    background: theme.colorScheme.secondaryContainer,
                    foreground: theme.colorScheme.onSecondaryContainer,
                  ),
                ],
                SectionHeader(title: l.alertsTitle),
                Card(
                  child: EmptyState(
                    icon: Icons.notification_important_outlined,
                    title: l.alertsSoonTitle,
                    body: l.alertsSoonBody,
                  ),
                ),
                SectionHeader(title: l.latenessListTitle),
                switch (day) {
                  AsyncError(:final error) when rows.isEmpty => FailureView(
                    message: failureText(error, l),
                    onRetry: () => ref.invalidate(latenessDayProvider),
                  ),
                  AsyncLoading() when rows.isEmpty => const Padding(
                    padding: EdgeInsets.all(Gap.xxl),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                  _ when rows.isEmpty => Card(
                    child: EmptyState(icon: Icons.door_front_door_outlined, title: l.noLatenessToday),
                  ),
                  _ => LatenessList(rows: rows),
                },
              ],
            ),
          ),
        ],
      ),
    );
  }
}
