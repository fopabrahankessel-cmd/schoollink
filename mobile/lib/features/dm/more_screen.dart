import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../l10n/app_localizations.dart';
import '../shell/destinations.dart';
import '../shell/home_shell.dart' show signOutChecked;
import '../shell/tab_path.dart';

/// D41: the DM's More tab. First the tabs that did not fit in the bar, then the decided alerts
/// (UC-D2), the scope dashboard (UC-D8), the settings (UC-D7), and signing out.
class MoreScreen extends ConsumerWidget {
  const MoreScreen({super.key, required this.overflow, this.unread = 0});

  final List<Destination> overflow;

  /// The parent's unread messages (UC-P2), counted on the inbox entry when it is listed here.
  final int unread;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(Gap.l, Gap.s, Gap.l, Gap.xl),
      children: [
        if (overflow.isNotEmpty)
          _Group(
            children: [
              for (final destination in overflow)
                _Entry(
                  key: ValueKey('more-tab-${destination.path}'),
                  icon: destination.icon,
                  title: destination.label(l),
                  count: destination == Destination.inbox ? unread : 0,
                  onTap: () => context.go('/home/${destination.path}'),
                ),
            ],
          ),
        _Group(
          children: [
            _Entry(
              key: const ValueKey('more-closed-alerts'),
              icon: Icons.task_alt_rounded,
              title: l.closedAlertsTitle,
              subtitle: l.closedAlertsHint,
              onTap: () => context.push(underTab(context, 'alerts/closed')),
            ),
            _Entry(
              key: const ValueKey('more-dashboard'),
              icon: Icons.insights_rounded,
              title: l.dashboardTitle,
              subtitle: l.dashboardHint,
              onTap: () => context.push(underTab(context, 'dashboard')),
            ),
            _Entry(
              key: const ValueKey('more-settings'),
              icon: Icons.tune_rounded,
              title: l.settingsTitle,
              subtitle: l.settingsHint,
              onTap: () => context.push(underTab(context, 'settings')),
            ),
          ],
        ),
        _Group(
          children: [
            _Entry(
              key: const ValueKey('more-sign-out'),
              icon: Icons.logout_rounded,
              title: l.signOut,
              color: scheme.error,
              chevron: false,
              onTap: () => signOutChecked(context, ref),
            ),
          ],
        ),
      ],
    );
  }
}

class _Group extends StatelessWidget {
  const _Group({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: Gap.l),
    child: Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: Column(
        children: [
          for (final (i, child) in children.indexed) ...[if (i > 0) const Divider(indent: 56), child],
        ],
      ),
    ),
  );
}

class _Entry extends StatelessWidget {
  const _Entry({
    super.key,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
    this.color,
    this.chevron = true,
    this.count = 0,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final Color? color;
  final bool chevron;
  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ListTile(
      minTileHeight: 56,
      leading: Icon(icon, color: color ?? scheme.primary),
      title: Text(title, style: TextStyle(color: color)),
      subtitle: subtitle == null ? null : Text(subtitle!),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (count > 0) Badge(label: Text(count > 99 ? '99+' : '$count')),
          if (chevron) const Icon(Icons.chevron_right_rounded),
        ],
      ),
      onTap: onTap,
    );
  }
}
