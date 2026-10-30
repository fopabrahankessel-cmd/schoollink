import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/auth/activation_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// Task 2.18, UC-P1: a parent activates with the school's invitation code and their phone, then the
/// SMS one-time code, and is signed in. The phone goes in request bodies only (NFR-9).
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
        ]),
      )
      ..on(
        'POST /auth/parent/activate',
        (_) => const Reply.ok({'activationId': 'act-1', 'expiresAt': '2026-11-02T07:10:00Z'}),
      )
      ..on('POST /auth/parent/verify', (_) => Reply.ok(tokensJson('access-p', 'refresh-p', user: parent)));
  });

  Future<void> openActivation(WidgetTester tester, {String language = 'fr'}) async {
    // a phone's screen, so the whole form is in view
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(tester, server: server, store: store, language: language);
    await tester.tap(
      find.text(language == 'fr' ? "Activer avec mon code d'invitation" : 'Activate with my invitation code'),
    );
    await tester.pumpAndSettle();
    expect(find.byType(ActivationScreen), findsOneWidget);
  }

  Future<void> enterCodeAndPhone(WidgetTester tester, {String code = 'ab12cd', String phone = '6 99 00 00 01'}) async {
    await tester.enterText(find.byKey(const ValueKey('invitation-code')), code);
    await tester.enterText(find.byKey(const ValueKey('phone')), phone);
    await tester.tap(find.text('Recevoir un code par SMS'));
    await tester.pumpAndSettle();
  }

  Future<void> enterOtp(WidgetTester tester, String otp) async {
    await tester.enterText(find.byKey(const ValueKey('otp')), otp);
    await tester.tap(find.widgetWithText(FilledButton, 'Activer mon compte'));
    await tester.pumpAndSettle();
  }

  testWidgets('a parent activates and lands on their children (UC-P1)', (tester) async {
    await openActivation(tester);

    await enterCodeAndPhone(tester);
    expect(server.sent('POST /auth/parent/activate').single.body, {'code': 'AB12CD', 'phone': '+237699000001'});
    expect(find.text('Nous avons envoyé un code à 6 chiffres par SMS au +237 699 00 00 01.'), findsOneWidget);
    expect(find.text('Étape 2 sur 2'), findsOneWidget);

    await enterOtp(tester, '123456');

    expect(server.sent('POST /auth/parent/verify').single.body, {'activationId': 'act-1', 'otp': '123456'});
    expect(find.byType(ActivationScreen), findsNothing);
    expect(find.widgetWithText(AppBar, 'Enfants'), findsOneWidget);
    expect(await store.read('session.refresh'), 'refresh-p');
    expect(server.requests.every((r) => !r.path.contains('699')), isTrue, reason: 'no phone in a URL (NFR-9)');
  });

  testWidgets('a number that is not a Cameroon mobile number is refused before sending', (tester) async {
    await openActivation(tester);

    await enterCodeAndPhone(tester, phone: '12345');

    expect(find.text('Saisissez un numéro camerounais à 9 chiffres, par exemple 6 99 00 00 01.'), findsOneWidget);
    expect(server.sent('POST /auth/parent/activate'), isEmpty);
  });

  testWidgets('the number may be typed with the country code', (tester) async {
    await openActivation(tester);

    await enterCodeAndPhone(tester, phone: '+237 699000001');

    expect((server.sent('POST /auth/parent/activate').single.body! as Map)['phone'], '+237699000001');
  });

  testWidgets('a wrong code or number shows the server message and stays on step 1', (tester) async {
    const message =
        "Ce code et ce numéro ne correspondent à aucune invitation valide. Vérifiez-les ou demandez un nouveau code à l'établissement.";
    server.on('POST /auth/parent/activate', (_) => Reply.error(400, 'auth.invalid_activation', message));
    await openActivation(tester);

    await enterCodeAndPhone(tester);

    expect(find.text(message), findsOneWidget);
    expect(find.text('Étape 1 sur 2'), findsOneWidget);
  });

  testWidgets('a wrong one-time code says so; a new code replaces it', (tester) async {
    const message = 'Ce code de vérification est incorrect ou a expiré. Vérifiez le SMS ou demandez un nouveau code.';
    server.on('POST /auth/parent/verify', (_) => Reply.error(400, 'auth.invalid_otp', message, field: 'otp'));
    await openActivation(tester);
    await enterCodeAndPhone(tester);

    await enterOtp(tester, '000000');
    expect(find.text(message), findsOneWidget);

    server
      ..on(
        'POST /auth/parent/activate',
        (_) => const Reply.ok({'activationId': 'act-2', 'expiresAt': '2026-11-02T07:20:00Z'}),
      )
      ..on('POST /auth/parent/verify', (_) => Reply.ok(tokensJson('access-p', 'refresh-p', user: parent)));
    await tester.tap(find.text('Renvoyer un code'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Un nouveau code a été envoyé.'), findsOneWidget);
    expect(find.text(message), findsNothing);

    await enterOtp(tester, '654321');
    expect((server.sent('POST /auth/parent/verify').last.body! as Map)['activationId'], 'act-2');
    expect(find.widgetWithText(AppBar, 'Enfants'), findsOneWidget);
  });

  testWidgets('the code must have 6 digits', (tester) async {
    await openActivation(tester);
    await enterCodeAndPhone(tester);

    await enterOtp(tester, '123');

    expect(find.text('Saisissez les 6 chiffres du code.'), findsOneWidget);
    expect(server.sent('POST /auth/parent/verify'), isEmpty);
  });

  testWidgets('"Change the number" and the back arrow return to step 1 with what was typed', (tester) async {
    await openActivation(tester);
    await enterCodeAndPhone(tester);

    await tester.tap(find.text('Changer de numéro'));
    await tester.pumpAndSettle();
    expect(find.text('Étape 1 sur 2'), findsOneWidget);
    expect(find.text('6 99 00 00 01'), findsOneWidget);

    await tester.tap(find.text('Recevoir un code par SMS'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();
    expect(find.text('Étape 1 sur 2'), findsOneWidget);
    expect(find.byType(ActivationScreen), findsOneWidget);
  });

  testWidgets('activation reads in English (NFR-12)', (tester) async {
    await openActivation(tester, language: 'en');

    expect(find.text('Your invitation code'), findsOneWidget);
    expect(find.text('Receive a code by SMS'), findsOneWidget);
  });

  test('Cameroon numbers are read however they are typed (D15)', () {
    expect(cameroonDigits('6 99 00 00 01'), '699000001');
    expect(cameroonDigits('+237 699 00 00 01'), '699000001');
    expect(cameroonDigits('00237699000001'), '699000001');
    expect(cameroonDigits('237699000001'), '699000001');
    expect(cameroonDigits('69900000'), isNull);
    expect(cameroonDigits('+33 6 12 34 56 78'), isNull);
  });
}
