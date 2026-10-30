import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/auth/login_screen.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// BR-6, NFR-3, D22: the app sends the signed-in user's offline records, and keeps them at sign-out.
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  late AppDatabase db;

  setUp(() => db = AppDatabase(NativeDatabase.memory()));

  /// A record saved on the phone before the app was closed.
  Future<void> leftFromLastTime(String id, {String userId = 'user-1'}) => db
      .into(db.syncItems)
      .insert(
        SyncItemsCompanion.insert(
          id: id,
          userId: userId,
          kind: 'session',
          path: '/sessions/$id',
          body: '{}',
          state: SyncState.pending,
          nextAttemptAt: DateTime(2026, 11, 2, 7, 40),
          createdAt: DateTime(2026, 11, 2, 7, 40),
        ),
      );

  Future<SyncState> stateOf(String id) async =>
      (await (db.select(db.syncItems)..where((i) => i.id.equals(id))).getSingle()).state;

  Future<void> openAccountMenuAndSignOut(WidgetTester tester) async {
    await tester.tap(find.byTooltip('Compte'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Se déconnecter'));
    await tester.pumpAndSettle();
  }

  testWidgets('records left from before a restart are sent when the app opens', (tester) async {
    final user = userJson(roles: ['TEACHER']);
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('PUT /sessions/s-1', (_) => const Reply(201, {}));
    await leftFromLastTime('s-1');
    await leftFromLastTime('s-other', userId: 'someone-else');

    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user)), database: db);
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pumpAndSettle();

    expect(server.sent('PUT /sessions/s-1').single.authorization, 'Bearer access-0');
    expect(await tester.runAsync(() => stateOf('s-1')), SyncState.synced);
    expect(server.sent('PUT /sessions/s-other'), isEmpty, reason: "another user's record waits for them");
  });

  testWidgets('signing out with records waiting asks first, and keeps them for the next sign-in (D22)', (tester) async {
    final user = userJson(roles: ['TEACHER']);
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('PUT /sessions/s-1', (_) => Reply.unreachable)
      ..on('PUT /sessions/s-2', (_) => Reply.unreachable)
      ..on('POST /auth/logout', (_) => const Reply(204))
      ..on('GET /auth/schools', (_) => const Reply.ok([]));
    await leftFromLastTime('s-1');
    await leftFromLastTime('s-2');
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user)), database: db);
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 50)));
    await tester.pumpAndSettle();

    await openAccountMenuAndSignOut(tester);
    expect(find.text('Données pas encore envoyées'), findsOneWidget);
    expect(find.textContaining('2 enregistrements attendent'), findsOneWidget);
    await tester.tap(find.widgetWithText(TextButton, 'Annuler'));
    await tester.pumpAndSettle();
    expect(find.byType(LoginScreen), findsNothing);

    await openAccountMenuAndSignOut(tester);
    await tester.tap(find.widgetWithText(TextButton, 'Se déconnecter'));
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(await tester.runAsync(() => stateOf('s-1')), SyncState.pending);
    expect(await tester.runAsync(() => stateOf('s-2')), SyncState.pending);
  });

  testWidgets('signing out with nothing waiting asks nothing', (tester) async {
    final user = userJson(roles: ['TEACHER']);
    final server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('POST /auth/logout', (_) => const Reply(204))
      ..on('GET /auth/schools', (_) => const Reply.ok([]));
    await pumpApp(tester, server: server, store: MemoryDeviceStore(signedIn(user)), database: db);

    await openAccountMenuAndSignOut(tester);

    expect(find.byType(AlertDialog), findsNothing);
    expect(find.byType(LoginScreen), findsOneWidget);
  });
}
