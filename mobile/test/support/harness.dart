import 'dart:convert';

import 'package:drift/native.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/app.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/providers.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/core/sync/sync_providers.dart';

import 'fake_server.dart';

/// Device store contents for a user already signed in on the phone.
Map<String, String> signedIn(Map<String, dynamic> user) => {
  'session.access': 'access-0',
  'session.refresh': 'refresh-0',
  'session.user': jsonEncode(user),
};

/// The running app of a test.
class TestApp {
  TestApp(this.container);

  final ProviderContainer container;
  bool _disposed = false;

  /// Ends the providers, and their timers (the sync backoff), before the test ends. Otherwise
  /// done after the test.
  void dispose() {
    if (!_disposed) {
      _disposed = true;
      container.dispose();
    }
  }
}

/// Starts the whole app against [server], with [store] as the phone's storage.
Future<TestApp> pumpApp(
  WidgetTester tester, {
  required FakeServer server,
  MemoryDeviceStore? store,
  AppDatabase? database,
  Stream<bool> online = const Stream.empty(),
  DateTime Function()? clock,
  String language = 'fr',
}) async {
  final db = database ?? AppDatabase(NativeDatabase.memory());
  final app = TestApp(
    ProviderContainer(
      retry: noRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        connectivityProvider.overrideWithValue(online),
        if (clock != null) clockProvider.overrideWithValue(clock),
        deviceStoreProvider.overrideWithValue(store ?? MemoryDeviceStore()),
        httpAdapterProvider.overrideWithValue(server),
        initialLanguageProvider.overrideWithValue(language),
      ],
    ),
  );
  addTearDown(() async {
    app.dispose();
    await db.close();
  });
  await tester.pumpWidget(UncontrolledProviderScope(container: app.container, child: const SchoolLinkApp()));
  await tester.pumpAndSettle();
  return app;
}
