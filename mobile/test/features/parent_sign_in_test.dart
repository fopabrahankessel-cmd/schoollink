import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/auth/activation_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// D24: a parent who already activated signs in again with their school, phone and an SMS one-time
/// code. The phone goes in request bodies only (NFR-9).
void main() {
  late FakeServer server;
  late MemoryDeviceStore store;
  final parent = userJson(roles: ['PARENT']);

  setUp(() {
    store = MemoryDeviceStore();
    server = FakeServer()
      ..on(
        'GET /auth/schools',
        (_) => const Reply.ok([
          {'id': 'school-1', 'name': 'Lycée Démo A (fictif)'},
          {'id': 'school-2', 'name': 'Collège Démo B (fictif)'},
        ]),
      )
      ..on(
        'POST /auth/parent/sign-in',
        (_) => const Reply.ok({'activationId': 'sign-1', 'expiresAt': '2026-11-02T07:50:00Z'}),
      )
      ..on('POST /auth/parent/sign-in/verify', (_) => Reply.ok(tokensJson('access-p', 'refresh-p', user: parent)));
  });

  Future<void> openSignIn(WidgetTester tester, {String language = 'fr'}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(tester, server: server, store: store, language: language);
    await tester.tap(find.text(language == 'fr' ? 'Me connecter avec un code par SMS' : 'Sign in with a code by SMS'));
    await tester.pumpAndSettle();
    expect(find.byType(ActivationScreen), findsOneWidget);
  }

  Future<void> pickSchool(WidgetTester tester, String name) async {
    await tester.tap(find.byKey(const ValueKey('school')));
    await tester.pumpAndSettle();
    await tester.tap(find.text(name).last);
    await tester.pumpAndSettle();
  }

  Future<void> enterPhone(WidgetTester tester, {String phone = '6 99 00 00 01'}) async {
    await tester.enterText(find.byKey(const ValueKey('phone')), phone);
    await tester.tap(find.text('Recevoir un code par SMS'));
    await tester.pumpAndSettle();
  }

  testWidgets('a parent signs in again with their school, phone and SMS code (D24)', (tester) async {
    await openSignIn(tester);
    expect(find.text('Connexion parent'), findsOneWidget);
    expect(find.byKey(const ValueKey('invitation-code')), findsNothing);

    await pickSchool(tester, 'Collège Démo B (fictif)');
    await enterPhone(tester);

    expect(server.sent('POST /auth/parent/sign-in').single.body, {'schoolId': 'school-2', 'phone': '+237699000001'});
    expect(server.sent('POST /auth/parent/activate'), isEmpty);
    // NFR-7: the server does not say whether the number has an account
    expect(
      find.text(
        'Si le +237 699 00 00 01 a un compte parent SchoolLink, un code à 6 chiffres lui a été envoyé par SMS.',
      ),
      findsOneWidget,
    );

    await tester.enterText(find.byKey(const ValueKey('otp')), '123456');
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();

    expect(server.sent('POST /auth/parent/sign-in/verify').single.body, {'activationId': 'sign-1', 'otp': '123456'});
    expect(server.sent('POST /auth/parent/verify'), isEmpty);
    expect(find.widgetWithText(AppBar, 'Enfants'), findsOneWidget);
    expect(await store.read('session.refresh'), 'refresh-p');
    expect(server.requests.every((r) => !r.path.contains('699')), isTrue, reason: 'no phone in a URL (NFR-9)');
  });

  testWidgets('a school must be chosen when there are several', (tester) async {
    await openSignIn(tester);

    await enterPhone(tester);

    expect(find.text('Ce champ est obligatoire.'), findsOneWidget);
    expect(server.sent('POST /auth/parent/sign-in'), isEmpty);
  });

  testWidgets('the only school is chosen already', (tester) async {
    server.on(
      'GET /auth/schools',
      (_) => const Reply.ok([
        {'id': 'school-1', 'name': 'Lycée Démo A (fictif)'},
      ]),
    );
    await openSignIn(tester);

    await enterPhone(tester);

    expect((server.sent('POST /auth/parent/sign-in').single.body! as Map)['schoolId'], 'school-1');
  });

  testWidgets('sign-in reads in English (NFR-12)', (tester) async {
    await openSignIn(tester, language: 'en');

    expect(find.text('Parent sign-in'), findsOneWidget);
    expect(find.text('Your phone number'), findsOneWidget);
  });
}
