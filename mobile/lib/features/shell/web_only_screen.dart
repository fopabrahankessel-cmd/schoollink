import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';

/// The administrator and the researcher work on the web dashboard only (SPEC §3).
class WebOnlyScreen extends ConsumerWidget {
  const WebOnlyScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.computer_outlined, size: 56, color: theme.colorScheme.primary),
                const SizedBox(height: 16),
                Text(l.webOnlyTitle, style: theme.textTheme.headlineSmall, textAlign: TextAlign.center),
                const SizedBox(height: 8),
                Text(l.webOnlyBody, style: theme.textTheme.bodyLarge, textAlign: TextAlign.center),
                const SizedBox(height: 32),
                OutlinedButton(
                  onPressed: () => ref.read(authControllerProvider.notifier).signOut(),
                  child: Text(l.signOut),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
