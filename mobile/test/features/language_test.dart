import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// NFR-12: every screen in French and English, per user.
void main() {
  FakeServer server() => FakeServer()..on('GET /auth/schools', (_) => const Reply.ok([]));

  testWidgets('sign-in renders in French', (tester) async {
    await pumpApp(tester, server: server(), language: 'fr');

    expect(find.text('Connexion'), findsOneWidget);
    expect(find.text('Numéro de téléphone ou e-mail'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Se connecter'), findsOneWidget);
  });

  testWidgets('sign-in renders in English', (tester) async {
    await pumpApp(tester, server: server(), language: 'en');

    expect(find.text('Sign in'), findsNWidgets(2)); // title and button
    expect(find.text('Phone number or email'), findsOneWidget);
  });

  testWidgets('the language switch changes the screen, is kept on the phone and sent to the server', (tester) async {
    final fake = server()..on('POST /auth/login', (_) => Reply.error(401, 'auth.invalid_credentials', 'Incorrect.'));
    final store = MemoryDeviceStore();
    await pumpApp(tester, server: fake, store: store, language: 'fr');

    await tester.tap(find.text('EN'));
    await tester.pumpAndSettle();

    expect(find.text('Phone number or email'), findsOneWidget);
    expect(store.values['device.language'], 'en');
    await tester.enterText(find.widgetWithText(TextFormField, 'Phone number or email'), 'x@example.org');
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'secret');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
    expect(fake.sent('POST /auth/login').single.headers['Accept-Language'], 'en');
  });

  testWidgets("a signed-in user sees their account's language, whatever the phone's", (tester) async {
    final user = userJson(roles: ['PARENT'], language: 'EN');
    final fake = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('GET /me/children', (_) => const Reply.ok([]))
      ..on('GET /me/notifications', (_) => const Reply.ok([]));
    await pumpApp(tester, server: fake, store: MemoryDeviceStore(signedIn(user)), language: 'fr');

    expect(find.widgetWithText(AppBar, 'Children'), findsOneWidget);
    expect(find.text('No child is linked to your account'), findsOneWidget);
  });

  test('both languages have the same keys', () {
    Set<String> keys(String file) => (jsonDecode(File('lib/l10n/$file').readAsStringSync()) as Map<String, dynamic>)
        .keys
        .where((k) => !k.startsWith('@'))
        .toSet();

    expect(keys('app_fr.arb'), keys('app_en.arb'));
  });
}
