import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/auth/parent_sign_in_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-P1, D24, D45: a parent signs in with their school, phone and password. An SMS code is asked
/// for only the first time or when the password is forgotten, and with it the parent chooses their
/// password. The phone goes in request bodies only (NFR-9).
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
    await tester.tap(
      find.text(language == 'fr' ? 'Première connexion ou mot de passe oublié' : 'First sign-in or forgotten password'),
    );
    await tester.pumpAndSettle();
    expect(find.byType(ParentSignInScreen), findsOneWidget);
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

  Future<void> enterCodeAndPassword(
    WidgetTester tester, {
    String otp = '123456',
    String password = 'mot-de-passe-parent',
    String? confirm,
  }) async {
    await tester.enterText(find.byKey(const ValueKey('otp')), otp);
    await tester.enterText(find.byKey(const ValueKey('newPassword')), password);
    await tester.enterText(find.byKey(const ValueKey('confirmPassword')), confirm ?? password);
    await tester.ensureVisible(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();
  }

  testWidgets('a parent signs in on the sign-in screen with their phone and password, no SMS (D45)', (tester) async {
    server
      ..on(
        'GET /auth/schools',
        (_) => const Reply.ok([
          {'id': 'school-1', 'name': 'Lycée Démo A (fictif)'},
        ]),
      )
      ..on('POST /auth/login', (_) => Reply.ok(tokensJson('access-p', 'refresh-p', user: parent)));
    await pumpApp(tester, server: server, store: store);

    await tester.enterText(find.byType(TextFormField).at(0), '+237699000001');
    await tester.enterText(find.byType(TextFormField).at(1), 'mot-de-passe-parent');
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    expect(server.sent('POST /auth/login').single.body, {
      'schoolId': 'school-1',
      'login': '+237699000001',
      'password': 'mot-de-passe-parent',
    });
    expect(server.sent('POST /auth/parent/sign-in'), isEmpty);
    expect(find.widgetWithText(AppBar, 'Enfants'), findsOneWidget);
  });

  testWidgets('the first time, a parent chooses their password with the SMS code (UC-P1, D45)', (tester) async {
    await openSignIn(tester);
    expect(find.text('Connexion parent'), findsOneWidget);

    await pickSchool(tester, 'Collège Démo B (fictif)');
    await enterPhone(tester);

    expect(server.sent('POST /auth/parent/sign-in').single.body, {'schoolId': 'school-2', 'phone': '+237699000001'});
    // NFR-7: the server does not say whether it knows the number
    expect(
      find.text("Si l'établissement connaît le +237 699 00 00 01, un code à 6 chiffres lui a été envoyé par SMS."),
      findsOneWidget,
    );

    expect(find.text('Saisissez le code et choisissez votre mot de passe'), findsOneWidget);
    await enterCodeAndPassword(tester);

    expect(server.sent('POST /auth/parent/sign-in/verify').single.body, {
      'activationId': 'sign-1',
      'otp': '123456',
      'password': 'mot-de-passe-parent',
    });
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

  group('with one school', () {
    setUp(() {
      server.on(
        'GET /auth/schools',
        (_) => const Reply.ok([
          {'id': 'school-1', 'name': 'Lycée Démo A (fictif)'},
        ]),
      );
    });

    Future<void> enterOtp(WidgetTester tester, String otp) => enterCodeAndPassword(tester, otp: otp);

    testWidgets('the password has at least 8 characters and is typed twice the same', (tester) async {
      await openSignIn(tester);
      await enterPhone(tester);

      await enterCodeAndPassword(tester, password: 'court');
      expect(find.text('Utilisez au moins 8 caractères.'), findsOneWidget);

      await enterCodeAndPassword(tester, password: 'mot-de-passe-parent', confirm: 'autre-mot-de-passe');
      expect(find.text('Les deux mots de passe ne correspondent pas.'), findsOneWidget);
      expect(server.sent('POST /auth/parent/sign-in/verify'), isEmpty);
    });

    testWidgets('a number that is not a Cameroon mobile number is refused before sending', (tester) async {
      await openSignIn(tester);

      await enterPhone(tester, phone: '12345');

      expect(find.text('Saisissez un numéro camerounais à 9 chiffres, par exemple 6 99 00 00 01.'), findsOneWidget);
      expect(server.sent('POST /auth/parent/sign-in'), isEmpty);
    });

    testWidgets('the number may be typed with the country code', (tester) async {
      await openSignIn(tester);

      await enterPhone(tester, phone: '+237 699000001');

      expect((server.sent('POST /auth/parent/sign-in').single.body! as Map)['phone'], '+237699000001');
    });

    testWidgets('an SMS that could not be sent shows the server message and stays on step 1', (tester) async {
      const message =
          "Le SMS contenant votre code de vérification n'a pas pu être envoyé. Réessayez dans quelques minutes.";
      server.on('POST /auth/parent/sign-in', (_) => Reply.error(503, 'auth.otp_not_sent', message));
      await openSignIn(tester);

      await enterPhone(tester);

      expect(find.text(message), findsOneWidget);
      expect(find.text('Étape 1 sur 2'), findsOneWidget);
    });

    testWidgets('after 3 password resets this year, the parent is sent to the school (D46)', (tester) async {
      const message =
          "Vous avez déjà réinitialisé votre mot de passe 3 fois cette année scolaire. Contactez l'administration de l'école.";
      server.on('POST /auth/parent/sign-in', (_) => Reply.error(403, 'auth.password_reset_locked', message));
      await openSignIn(tester);

      await enterPhone(tester);

      expect(find.text(message), findsOneWidget);
      expect(find.text('Étape 1 sur 2'), findsOneWidget);
      expect(find.byType(ParentSignInScreen), findsOneWidget);
    });

    testWidgets('a wrong one-time code says so; a new code replaces it', (tester) async {
      const message = 'Ce code de vérification est incorrect ou a expiré. Vérifiez le SMS ou demandez un nouveau code.';
      server.on('POST /auth/parent/sign-in/verify', (_) => Reply.error(400, 'auth.invalid_otp', message, field: 'otp'));
      await openSignIn(tester);
      await enterPhone(tester);

      await enterOtp(tester, '000000');
      expect(find.text(message), findsOneWidget);

      server
        ..on(
          'POST /auth/parent/sign-in',
          (_) => const Reply.ok({'activationId': 'sign-2', 'expiresAt': '2026-11-02T07:20:00Z'}),
        )
        ..on('POST /auth/parent/sign-in/verify', (_) => Reply.ok(tokensJson('access-p', 'refresh-p', user: parent)));
      await tester.tap(find.text('Renvoyer un code'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Un nouveau code a été envoyé.'), findsOneWidget);
      expect(find.text(message), findsNothing);

      await enterOtp(tester, '654321');
      expect((server.sent('POST /auth/parent/sign-in/verify').last.body! as Map)['activationId'], 'sign-2');
      expect(find.widgetWithText(AppBar, 'Enfants'), findsOneWidget);
    });

    testWidgets('a new code refused for too many requests says so (NFR-7)', (tester) async {
      const message = 'Trop de tentatives. Réessayez dans 51 minutes.';
      await openSignIn(tester);
      await enterPhone(tester);

      await tester.tap(find.text('Renvoyer un code'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Un nouveau code a été envoyé.'), findsOneWidget);

      server.on('POST /auth/parent/sign-in', (_) => Reply.error(429, 'rate_limited', message));
      await tester.tap(find.text('Renvoyer un code'));
      await tester.pumpAndSettle();

      expect(find.text(message), findsOneWidget);
      // no new code: the last one sent still works, and the screen must not say otherwise
      expect(find.textContaining('Un nouveau code a été envoyé.'), findsNothing);
      expect(find.textContaining("Le code est valable jusqu'à"), findsOneWidget);
    });

    testWidgets('the code must have 6 digits', (tester) async {
      await openSignIn(tester);
      await enterPhone(tester);

      await enterOtp(tester, '123');

      expect(find.text('Saisissez les 6 chiffres du code.'), findsOneWidget);
      expect(server.sent('POST /auth/parent/sign-in/verify'), isEmpty);
    });

    testWidgets('"Change the number" and the back arrow return to step 1 with what was typed', (tester) async {
      await openSignIn(tester);
      await enterPhone(tester);

      await tester.tap(find.text('Changer de numéro'));
      await tester.pumpAndSettle();
      expect(find.text('Étape 1 sur 2'), findsOneWidget);
      expect(find.text('6 99 00 00 01'), findsOneWidget);

      await tester.tap(find.text('Recevoir un code par SMS'));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(BackButton));
      await tester.pumpAndSettle();
      expect(find.text('Étape 1 sur 2'), findsOneWidget);
      expect(find.byType(ParentSignInScreen), findsOneWidget);
    });
  });

  test('Cameroon numbers are read however they are typed (D15)', () {
    expect(cameroonDigits('6 99 00 00 01'), '699000001');
    expect(cameroonDigits('+237 699 00 00 01'), '699000001');
    expect(cameroonDigits('00237699000001'), '699000001');
    expect(cameroonDigits('237699000001'), '699000001');
    expect(cameroonDigits('69900000'), isNull);
    expect(cameroonDigits('+33 6 12 34 56 78'), isNull);
  });

  testWidgets('sign-in reads in English (NFR-12)', (tester) async {
    await openSignIn(tester, language: 'en');

    expect(find.text('Parent sign-in'), findsOneWidget);
    expect(find.text('Your phone number'), findsOneWidget);
  });
}
