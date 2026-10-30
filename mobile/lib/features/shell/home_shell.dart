import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/auth/user_info.dart';
import '../../core/sync/sync_providers.dart';
import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';
import '../dm/dm_home_screen.dart';
import '../lateness/gate_screen.dart';
import '../teacher/sessions_screen.dart';
import 'destinations.dart';
import '../parent/children_screen.dart';
import '../parent/inbox_screen.dart';
import '../parent/parent.dart';

/// The role-based shell: one tab per screen the user's roles give (SPEC §8). With a single
/// screen there is no tab bar. Records saved offline are announced above every screen, with a way
/// to send them now (BR-6, UC-T2).
class HomeShell extends ConsumerWidget {
  const HomeShell({super.key, required this.current});

  final Destination current;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final user = ref.watch(authControllerProvider).value;
    final destinations = user == null ? const <Destination>[] : destinationsFor(user.roles);
    final index = destinations.indexOf(current);
    final pending = ref.watch(pendingSyncProvider).value ?? 0;
    // listened from the start, so the banner knows the network state when it appears
    final offline = ref.watch(onlineProvider).value == false;
    // UC-P2: unread messages counted on the tab, so a parent sees them from the children
    final unread = user?.roles.contains(Role.parent) == true ? ref.watch(inboxProvider).value?.unread ?? 0 : 0;

    return Scaffold(
      appBar: AppBar(
        title: Text(current.label(l)),
        actions: [
          _AccountMenu(roles: user?.roles ?? const {}, onSignOut: () => _signOut(context, ref, pending)),
          const SizedBox(width: Gap.s),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: switch (current) {
              Destination.sessions => const SessionsScreen(),
              Destination.gate => const GateScreen(),
              Destination.alerts => const DmHomeScreen(),
              Destination.children => const ChildrenScreen(),
              Destination.inbox => const InboxScreen(),
              _ => _Placeholder(current),
            },
          ),
          // at the bottom, near the thumb, and never hiding the screen's own header
          if (pending > 0) SyncBanner(pending: pending, offline: offline),
        ],
      ),
      bottomNavigationBar: destinations.length < 2
          ? null
          : NavigationBar(
              selectedIndex: index < 0 ? 0 : index,
              onDestinationSelected: (i) => context.go('/home/${destinations[i].path}'),
              destinations: [
                for (final destination in destinations)
                  NavigationDestination(
                    icon: _withBadge(Icon(destination.icon), destination == Destination.inbox ? unread : 0),
                    selectedIcon: _withBadge(
                      Icon(destination.selectedIcon),
                      destination == Destination.inbox ? unread : 0,
                    ),
                    label: destination.label(l),
                  ),
              ],
            ),
    );
  }

  /// D22: records not yet sent stay on the phone for this user's next sign-in; say so first.
  Future<void> _signOut(BuildContext context, WidgetRef ref, int pending) async {
    if (pending > 0) {
      final l = AppLocalizations.of(context);
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          icon: const Icon(Icons.cloud_upload_outlined),
          title: Text(l.signOutPendingTitle),
          content: Text(l.signOutPendingBody(pending)),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.cancel)),
            TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l.signOut)),
          ],
        ),
      );
      if (confirmed != true) {
        return;
      }
    }
    await ref.read(authControllerProvider.notifier).signOut();
  }
}

enum _AccountAction { signOut }

/// The account button: who is signed in (by role, the app has no names) and signing out.
class _AccountMenu extends StatelessWidget {
  const _AccountMenu({required this.roles, required this.onSignOut});

  final Set<Role> roles;
  final VoidCallback onSignOut;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final roleNames = [
      for (final role in Role.values)
        if (roles.contains(role)) roleName(role, l),
    ];
    return PopupMenuButton<_AccountAction>(
      tooltip: l.accountMenu,
      position: PopupMenuPosition.under,
      onSelected: (_) => onSignOut(),
      icon: CircleAvatar(
        radius: 17,
        backgroundColor: status.onHeader.withValues(alpha: 0.14),
        child: Icon(Icons.person_outline_rounded, color: status.onHeader, size: 22),
      ),
      itemBuilder: (context) => [
        PopupMenuItem<_AccountAction>(
          enabled: false,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(l.signedInAs, style: theme.textTheme.labelMedium),
              Text(
                roleNames.join(', '),
                style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.onSurface),
              ),
            ],
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: _AccountAction.signOut,
          child: ListTile(
            leading: const Icon(Icons.logout_rounded),
            title: Text(l.signOut),
            contentPadding: EdgeInsets.zero,
          ),
        ),
      ],
    );
  }
}

String roleName(Role role, AppLocalizations l) => switch (role) {
  Role.admin => l.roleAdmin,
  Role.teacher => l.roleTeacher,
  Role.assistantDm => l.roleAssistantDm,
  Role.dm => l.roleDm,
  Role.counsellor => l.roleCounsellor,
  Role.parent => l.roleParent,
  Role.researcher => l.roleResearcher,
};

/// UC-T2, BR-6: records saved on this phone and not on the server yet. They go by themselves when
/// the network is back; "Send now" tries at once, skipping the wait between attempts.
class SyncBanner extends ConsumerStatefulWidget {
  const SyncBanner({super.key, required this.pending, required this.offline});

  final int pending;
  final bool offline;

  @override
  ConsumerState<SyncBanner> createState() => _SyncBannerState();
}

class _SyncBannerState extends ConsumerState<SyncBanner> {
  bool _sending = false;

  Future<void> _sendNow() async {
    final scheduler = ref.read(syncSchedulerProvider);
    if (scheduler == null) {
      return;
    }
    setState(() => _sending = true);
    try {
      await scheduler.sync(now: true);
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final status = StatusColors.of(context);
    final offline = widget.offline;
    return Material(
      key: const ValueKey('sync-banner'),
      color: status.lateContainer,
      child: SafeArea(
        top: false,
        bottom: false,
        child: Semantics(
          liveRegion: true,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.xs, Gap.s, Gap.xs),
            child: Row(
              children: [
                Icon(offline ? Icons.cloud_off_rounded : Icons.cloud_upload_outlined, color: status.onLateContainer),
                const SizedBox(width: Gap.m),
                Expanded(
                  child: Text(
                    offline ? l.pendingOffline(widget.pending) : l.pendingOnline(widget.pending),
                    style: Theme.of(context).textTheme.bodyMedium
                        ?.copyWith(color: status.onLateContainer, fontWeight: FontWeight.w600),
                  ),
                ),
                if (_sending)
                  const Padding(
                    padding: EdgeInsets.all(Gap.m),
                    child: SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5)),
                  )
                else
                  TextButton(
                    onPressed: _sendNow,
                    style: TextButton.styleFrom(foregroundColor: status.onLateContainer),
                    child: Text(l.sendNow),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

Widget _withBadge(Widget icon, int count) => count == 0
    ? icon
    : Badge(key: const ValueKey('unread-badge'), label: Text(count > 99 ? '99+' : '$count'), child: icon);

class _Placeholder extends StatelessWidget {
  const _Placeholder(this.destination);

  final Destination destination;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: EmptyState(icon: destination.icon, title: AppLocalizations.of(context).comingSoon),
    );
  }
}
