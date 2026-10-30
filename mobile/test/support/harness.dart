import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/app.dart';
import 'package:schoollink/core/providers.dart';
import 'package:schoollink/core/storage/device_store.dart';

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
  String language = 'fr',
}) async {
  final container = ProviderContainer(
    overrides: [
      deviceStoreProvider.overrideWithValue(store ?? MemoryDeviceStore()),
      httpAdapterProvider.overrideWithValue(server),
      initialLanguageProvider.overrideWithValue(language),
    ],
  );
  addTearDown(container.dispose);
  await tester.pumpWidget(UncontrolledProviderScope(container: container, child: const SchoolLinkApp()));
  await tester.pumpAndSettle();
  return container;
}
