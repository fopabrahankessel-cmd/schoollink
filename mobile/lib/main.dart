import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/providers.dart';
import 'core/storage/device_store.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final store = SecureDeviceStore();
  // read before the first frame (the native splash is still up), so the first screen is in the
  // language chosen last time
  final language = await store.read(PreferredLanguage.key);
  runApp(
    ProviderScope(
      overrides: [
        deviceStoreProvider.overrideWithValue(store),
        if (language != null) initialLanguageProvider.overrideWithValue(language),
      ],
      child: const SchoolLinkApp(),
    ),
  );
}
