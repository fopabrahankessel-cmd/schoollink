import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/providers.dart';
import 'core/push/push_client.dart';
import 'core/push/push_registration.dart';
import 'core/storage/device_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final store = SecureDeviceStore();
  // read before the first frame (the native splash is still up), so the first screen is in the
  // language chosen last time
  final language = await store.read(PreferredLanguage.key);
  // BR-21: null when the app was built without a Firebase project; it then runs without push
  final push = await FirebasePushClient.start(
    Locale(language ?? (PlatformDispatcher.instance.locale.languageCode == 'en' ? 'en' : 'fr')),
  );
  runApp(
    ProviderScope(
      retry: noRetry,
      overrides: [
        deviceStoreProvider.overrideWithValue(store),
        if (language != null) initialLanguageProvider.overrideWithValue(language),
        pushClientProvider.overrideWithValue(push),
      ],
      child: const SchoolLinkApp(),
    ),
  );
}
