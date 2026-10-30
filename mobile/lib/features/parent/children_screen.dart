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
import 'parent.dart';

/// UC-P3: the parent's children; each opens their attendance history.
class ChildrenScreen extends ConsumerWidget {
  const ChildrenScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return switch (ref.watch(childrenProvider)) {
      AsyncValue(value: final children?) => RefreshIndicator(
        onRefresh: () => ref.refresh(childrenProvider.future).then((_) {}, onError: (_) {}),
        child: _ChildrenList(children: children),
      ),
      AsyncError(:final error) => FailureView(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(childrenProvider),
      ),
      _ => const _ChildrenSkeleton(),
    };
  }
}

class _ChildrenList extends ConsumerWidget {
  const _ChildrenList({required this.children});

  final Fetched<List<Child>> children;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    return ListView(
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
              l.yourChildren(children.value.length),
              style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader),
            ),
            if (children.value.isNotEmpty) ...[
              const SizedBox(height: Gap.xs),
              Text(l.childrenHint, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted)),
            ],
          ],
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, 0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (children.fetchedAt case final fetchedAt?) ...[
                InfoBanner(
                  icon: Icons.cloud_off_rounded,
                  text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                  background: theme.colorScheme.secondaryContainer,
                  foreground: theme.colorScheme.onSecondaryContainer,
                ),
                const SizedBox(height: Gap.l),
              ],
              if (children.value.isEmpty)
                EmptyState(icon: Icons.family_restroom_rounded, title: l.noChildren, body: l.noChildrenHint)
              else
                for (final child in children.value) ...[_ChildCard(child: child), const SizedBox(height: Gap.m)],
            ],
          ),
        ),
      ],
    );
  }
}

class _ChildCard extends StatelessWidget {
  const _ChildCard({required this.child});

  final Child child;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        key: ValueKey('child-${child.id}'),
        onTap: () => context.push('/home/children/child/${child.id}'),
        child: Padding(
          padding: const EdgeInsets.all(Gap.l),
          child: Row(
            children: [
              InitialsAvatar(firstName: child.firstName, lastName: child.lastName, size: 52),
              const SizedBox(width: Gap.l),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('${child.firstName} ${child.lastName}', style: theme.textTheme.titleMedium),
                    if (child.className case final className?) ...[
                      const SizedBox(height: 2),
                      Text(
                        l.classLabel(className),
                        style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                    ],
                    const SizedBox(height: Gap.s),
                    Text(l.seeHistory, style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary)),
                  ],
                ),
              ),
              Icon(Icons.chevron_right_rounded, color: theme.colorScheme.onSurfaceVariant),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChildrenSkeleton extends StatelessWidget {
  const _ChildrenSkeleton();

  @override
  Widget build(BuildContext context) {
    Widget card() => const Card(
      child: Padding(
        padding: EdgeInsets.all(Gap.l),
        child: Row(
          children: [
            Bone.circle(size: 52),
            SizedBox(width: Gap.l),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  FractionallySizedBox(widthFactor: 0.6, child: Bone(height: 18)),
                  SizedBox(height: Gap.s),
                  FractionallySizedBox(widthFactor: 0.3, child: Bone(height: 12)),
                  SizedBox(height: Gap.s),
                  FractionallySizedBox(widthFactor: 0.45, child: Bone(height: 12)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
    return Skeleton(
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        children: [
          const HeaderBand(
            children: [
              Bone(width: 160, height: 14, onDark: true),
              SizedBox(height: Gap.s),
              Bone(width: 180, height: 28, onDark: true),
              SizedBox(height: Gap.s),
              Bone(width: 240, height: 12, onDark: true),
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(Gap.l),
            child: Column(
              children: [
                card(),
                const SizedBox(height: Gap.m),
                card(),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
