import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/sync/sync_providers.dart';
import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';
import 'destinations.dart';

/// The role-based shell: one tab per screen the user's roles give (SPEC §8). With a single
/// screen there is no tab bar. The screens themselves arrive with tasks 2.13 to 2.19 and 3.10.
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

    return Scaffold(
      appBar: AppBar(
        title: Text(current.label(l)),
        actions: [
          PopupMenuButton<_AccountAction>(
            tooltip: l.accountMenu,
            icon: const Icon(Icons.account_circle_outlined),
            onSelected: (_) => _signOut(context, ref, pending),
            itemBuilder: (context) => [
              PopupMenuItem(
                value: _AccountAction.signOut,
                child: ListTile(
                  leading: const Icon(Icons.logout),
                  title: Text(l.signOut),
                  contentPadding: EdgeInsets.zero,
                ),
              ),
            ],
          ),
        ],
      ),
      body: _Placeholder(current),
      bottomNavigationBar: destinations.length < 2
          ? null
          : NavigationBar(
              selectedIndex: index < 0 ? 0 : index,
              onDestinationSelected: (i) => context.go('/home/${destinations[i].path}'),
              destinations: [
                for (final destination in destinations)
                  NavigationDestination(
                    icon: Icon(destination.icon),
                    selectedIcon: Icon(destination.selectedIcon),
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

class _Placeholder extends StatelessWidget {
  const _Placeholder(this.destination);

  final Destination destination;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(destination.icon, size: 56, color: theme.colorScheme.primary),
            const SizedBox(height: 16),
            Text(
              AppLocalizations.of(context).comingSoon,
              style: theme.textTheme.bodyLarge,
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
