import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/providers.dart';
import 'core/push/push_registration.dart';
import 'core/sync/sync_providers.dart';
import 'core/theme.dart';
import 'l10n/app_localizations.dart';
import 'router.dart';

class SchoolLinkApp extends ConsumerWidget {
  const SchoolLinkApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    ref.watch(syncSchedulerProvider); // BR-6: sends the signed-in user's offline records
    ref.watch(pushRegistrationProvider); // BR-21: the server knows where to push
    return MaterialApp.router(
      onGenerateTitle: (context) => AppLocalizations.of(context).appTitle,
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      locale: ref.watch(appLocaleProvider),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      routerConfig: ref.watch(routerProvider),
    );
  }
}
