import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/auth/user_info.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/auth/login_screen.dart';
import 'package:schoollink/features/shell/web_only_screen.dart';
import 'package:schoollink/router.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// SPEC §8: one app, the screens chosen by the user's roles.
void main() {
  /// Starts the app with [roles] already signed in on the phone.
  Future<FakeServer> signedInAs(WidgetTester tester, List<String> roles) async {
    final user = userJson(roles: roles);
    final server = FakeServer()..on('GET /me', (_) => Reply.ok(user));
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user)));
    return server;
  }

  List<String> tabLabels(WidgetTester tester) => [
    for (final d in tester.widget<NavigationBar>(find.byType(NavigationBar)).destinations)
      (d as NavigationDestination).label,
  ];

  testWidgets('a teacher has one screen and no tab bar', (tester) async {
    await signedInAs(tester, ['TEACHER']);

    expect(find.widgetWithText(AppBar, 'Appel'), findsOneWidget);
    expect(find.byType(NavigationBar), findsNothing);
  });

  testWidgets('an assistant DM opens on lateness at the gate', (tester) async {
    await signedInAs(tester, ['ASSISTANT_DM']);

    expect(find.widgetWithText(AppBar, 'Retards'), findsOneWidget);
  });

  testWidgets('a parent has their children and their notifications', (tester) async {
    await signedInAs(tester, ['PARENT']);

    expect(tabLabels(tester), ['Enfants', 'Notifications']);
    expect(find.widgetWithText(AppBar, 'Enfants'), findsOneWidget);

    await tester.tap(find.text('Notifications').last);
    await tester.pumpAndSettle();
    expect(find.widgetWithText(AppBar, 'Notifications'), findsOneWidget);
  });

  testWidgets('a DM who teaches and is a parent gets the tabs of each role (D21)', (tester) async {
    await signedInAs(tester, ['DM', 'PARENT', 'TEACHER']);

    expect(tabLabels(tester), ['Appel', 'Alertes', 'Enfants', 'Notifications']);
  });

  testWidgets('the administrator and the researcher are sent to the web dashboard', (tester) async {
    await signedInAs(tester, ['ADMIN']);

    expect(find.byType(WebOnlyScreen), findsOneWidget);
    expect(find.text('Utilisez le tableau de bord web'), findsOneWidget);
  });

  testWidgets('signing out returns to sign-in and tells the server', (tester) async {
    final server = await signedInAs(tester, ['TEACHER']);
    server.on('POST /auth/logout', (_) => const Reply(204));

    await tester.tap(find.byTooltip('Compte'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Se déconnecter'));
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(server.sent('POST /auth/logout').single.body, {'refreshToken': 'refresh-0'});
  });

  testWidgets('with no network at start-up, the user signed in on the phone keeps working (NFR-3)', (tester) async {
    final server = FakeServer()..offline = true;
    await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(userJson(roles: ['TEACHER']))),
    );

    expect(find.widgetWithText(AppBar, 'Appel'), findsOneWidget);
  });

  testWidgets('a session the server ended goes back to sign-in', (tester) async {
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.error(401, 'auth.unauthorized', 'x'))
      ..on('POST /auth/refresh', (_) => Reply.error(401, 'auth.invalid_refresh_token', 'x'))
      ..on('GET /auth/schools', (_) => const Reply.ok([]));
    final store = MemoryDeviceStore(signedIn(userJson()));
    await pumpApp(tester, server: server, store: store);

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(store.values.containsKey('session.refresh'), isFalse);
  });

  group('redirectFor', () {
    UserInfo user(Set<Role> roles, {bool passwordChangeRequired = false}) =>
        UserInfo(id: 'u', schoolId: 's', roles: roles, language: 'FR', passwordChangeRequired: passwordChangeRequired);

    test('waits on the splash while the session is restored', () {
      expect(redirectFor(const AsyncLoading(), '/home/sessions'), '/splash');
      expect(redirectFor(const AsyncLoading(), '/splash'), isNull);
    });

    test('a signed-out user can only sign in', () {
      expect(redirectFor(const AsyncData(null), '/home/sessions'), '/login');
      expect(redirectFor(const AsyncData(null), '/login'), isNull);
    });

    test('a temporary password goes nowhere else (D18)', () {
      final data = AsyncData<UserInfo?>(user({Role.teacher}, passwordChangeRequired: true));
      expect(redirectFor(data, '/home/sessions'), '/change-password');
      expect(redirectFor(data, '/change-password'), isNull);
    });

    test("a tab of another role is not reachable", () {
      final teacher = AsyncData<UserInfo?>(user({Role.teacher}));
      expect(redirectFor(teacher, '/home/alerts'), '/home/sessions');
      expect(redirectFor(teacher, '/home/nonsense'), '/home/sessions');
      expect(redirectFor(teacher, '/home/sessions'), isNull);
      expect(redirectFor(teacher, '/login'), '/home/sessions');
    });

    test('a researcher has no app screens', () {
      expect(redirectFor(AsyncData<UserInfo?>(user({Role.researcher})), '/home/sessions'), '/web-only');
    });
  });
}
