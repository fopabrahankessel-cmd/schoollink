import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/auth/change_password_screen.dart';
import 'package:schoollink/features/auth/login_screen.dart';
import 'package:schoollink/features/shell/home_shell.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// Staff sign-in (D15, D16) and the first password (D18). Text is checked in French, the default.
void main() {
  late FakeServer server;

  setUp(() {
    server = FakeServer()
      ..on(
        'GET /auth/schools',
        (_) => const Reply.ok([
          {'id': 'school-1', 'name': 'Lycée Fictif de Douala'},
          {'id': 'school-2', 'name': 'Collège Fictif de Yaoundé'},
        ]),
      );
  });

  Future<void> fillIn(WidgetTester tester, {String login = '699200001', String password = 'secret-pass'}) async {
    await tester.enterText(find.widgetWithText(TextFormField, 'Numéro de téléphone ou e-mail'), login);
    await tester.enterText(find.widgetWithText(TextFormField, 'Mot de passe'), password);
  }

  Future<void> chooseSchool(WidgetTester tester, String name) async {
    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text(name).last);
    await tester.pumpAndSettle();
  }

  testWidgets('a teacher signs in and lands on the attendance screen', (tester) async {
    server.on('POST /auth/login', (_) => Reply.ok(tokensJson('access-1', 'refresh-1')));
    final store = MemoryDeviceStore();
    await pumpApp(tester, server: server, store: store);

    expect(find.byType(LoginScreen), findsOneWidget);
    await chooseSchool(tester, 'Lycée Fictif de Douala');
    await fillIn(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    expect(find.byType(HomeShell), findsOneWidget);
    expect(find.text('Appel'), findsOneWidget);
    final sent = server.sent('POST /auth/login').single;
    expect(sent.body, {'schoolId': 'school-1', 'login': '699200001', 'password': 'secret-pass'});
    expect(sent.authorization, isNull);
    expect(store.values['session.refresh'], 'refresh-1');
    expect(store.values['device.school'], 'school-1');
  });

  testWidgets('the fields are required', (tester) async {
    await pumpApp(tester, server: server);

    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    expect(find.text('Ce champ est obligatoire.'), findsNWidgets(2));
    expect(server.sent('POST /auth/login'), isEmpty);
  });

  testWidgets('a wrong password shows the server message and stays on sign-in', (tester) async {
    server.on(
      'POST /auth/login',
      (_) => Reply.error(401, 'auth.invalid_credentials', 'Identifiant ou mot de passe incorrect.'),
    );
    await pumpApp(tester, server: server);

    await fillIn(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    expect(find.text('Identifiant ou mot de passe incorrect.'), findsOneWidget);
    expect(find.byType(LoginScreen), findsOneWidget);
  });

  testWidgets('a login used in two schools asks for the school, next to the picker (D16)', (tester) async {
    server.on(
      'POST /auth/login',
      (_) => Reply.error(400, 'auth.school_required', 'Choisissez votre école.', field: 'schoolId'),
    );
    await pumpApp(tester, server: server);

    await fillIn(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    final picker = tester.widget<InputDecorator>(
      find.descendant(of: find.byType(DropdownButtonFormField<String>), matching: find.byType(InputDecorator)),
    );
    expect(picker.decoration.errorText, 'Choisissez votre école.');
    expect(server.sent('POST /auth/login').single.body, containsPair('schoolId', null));
  });

  testWidgets('no network says so in the app\'s words', (tester) async {
    server.on('POST /auth/login', (_) => Reply.unreachable);
    await pumpApp(tester, server: server);

    await fillIn(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    expect(find.text('Pas de connexion au serveur. Vérifiez votre réseau et réessayez.'), findsOneWidget);
  });

  testWidgets('the school used last on this phone is picked already (D16)', (tester) async {
    server.on('POST /auth/login', (_) => Reply.ok(tokensJson('access-1', 'refresh-1')));
    await pumpApp(tester, server: server, store: MemoryDeviceStore({'device.school': 'school-2'}));

    expect(find.text('Collège Fictif de Yaoundé'), findsOneWidget);
    await fillIn(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    expect(server.sent('POST /auth/login').single.body, containsPair('schoolId', 'school-2'));
  });

  testWidgets('the school list failing to load can be retried', (tester) async {
    server.on('GET /auth/schools', (_) => Reply.unreachable);
    await pumpApp(tester, server: server);

    expect(find.text("La liste des écoles n'a pas pu être chargée."), findsOneWidget);
    server.on(
      'GET /auth/schools',
      (_) => const Reply.ok([
        {'id': 'school-1', 'name': 'Lycée Fictif de Douala'},
      ]),
    );
    await tester.tap(find.text('Réessayer'));
    await tester.pumpAndSettle();

    // the only school is picked already
    expect(find.text('Lycée Fictif de Douala'), findsOneWidget);
  });

  testWidgets('a temporary password must be replaced first, then the app opens (D18)', (tester) async {
    server
      ..on(
        'POST /auth/login',
        (_) => Reply.ok(tokensJson('access-1', 'refresh-1', user: userJson(passwordChangeRequired: true))),
      )
      ..on('POST /me/password', (_) => Reply.ok(tokensJson('access-2', 'refresh-2')));
    final store = MemoryDeviceStore();
    await pumpApp(tester, server: server, store: store);

    await fillIn(tester, password: 'Tmp-Pass-1234');
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();
    expect(find.byType(ChangePasswordScreen), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Mot de passe temporaire'), 'Tmp-Pass-1234');
    await tester.enterText(find.widgetWithText(TextFormField, 'Nouveau mot de passe'), 'short');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirmez le nouveau mot de passe'), 'other');
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer le mot de passe'));
    await tester.pumpAndSettle();
    expect(find.text('Utilisez au moins 8 caractères.'), findsOneWidget);
    expect(find.text('Les deux mots de passe ne correspondent pas.'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Nouveau mot de passe'), 'my-own-pass');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirmez le nouveau mot de passe'), 'my-own-pass');
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer le mot de passe'));
    await tester.pumpAndSettle();

    final sent = server.sent('POST /me/password').single;
    expect(sent.body, {'currentPassword': 'Tmp-Pass-1234', 'newPassword': 'my-own-pass'});
    expect(sent.authorization, 'Bearer access-1');
    expect(find.byType(HomeShell), findsOneWidget);
    expect(store.values['session.refresh'], 'refresh-2');
  });

  testWidgets('a wrong temporary password is shown on its field', (tester) async {
    server
      ..on(
        'POST /auth/login',
        (_) => Reply.ok(tokensJson('access-1', 'refresh-1', user: userJson(passwordChangeRequired: true))),
      )
      ..on(
        'POST /me/password',
        (_) =>
            Reply.error(400, 'auth.wrong_password', 'Le mot de passe actuel est incorrect.', field: 'currentPassword'),
      );
    await pumpApp(tester, server: server);
    await fillIn(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Se connecter'));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextFormField, 'Mot de passe temporaire'), 'wrong');
    await tester.enterText(find.widgetWithText(TextFormField, 'Nouveau mot de passe'), 'my-own-pass');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirmez le nouveau mot de passe'), 'my-own-pass');
    await tester.tap(find.widgetWithText(FilledButton, 'Enregistrer le mot de passe'));
    await tester.pumpAndSettle();

    expect(find.text('Le mot de passe actuel est incorrect.'), findsOneWidget);
    expect(find.byType(ChangePasswordScreen), findsOneWidget);
  });
}
