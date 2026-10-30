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

/// Starts the whole app against [server], with [store] as the phone's storage.
Future<ProviderContainer> pumpApp(
  WidgetTester tester, {
  required FakeServer server,
  MemoryDeviceStore? store,
  AppDatabase? database,
  Stream<bool> online = const Stream.empty(),
  String language = 'fr',
}) async {
  final db = database ?? AppDatabase(NativeDatabase.memory());
  final container = ProviderContainer(
    overrides: [
      appDatabaseProvider.overrideWithValue(db),
      connectivityProvider.overrideWithValue(online),
      deviceStoreProvider.overrideWithValue(store ?? MemoryDeviceStore()),
      httpAdapterProvider.overrideWithValue(server),
      initialLanguageProvider.overrideWithValue(language),
    ],
  );
  addTearDown(() async {
    container.dispose();
    await db.close();
  });
  await tester.pumpWidget(UncontrolledProviderScope(container: container, child: const SchoolLinkApp()));
  await tester.pumpAndSettle();
  return container;
}
