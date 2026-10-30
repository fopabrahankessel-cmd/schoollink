import 'dart:async';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/push/push_client.dart';
import 'package:schoollink/core/push/push_registration.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// Stands in for FCM.
class FakePushClient implements PushClient {
  String? currentToken = 'token-1';
  bool permissionAsked = false;
  bool deleted = false;
  PushMessage? startedBy;
  final refresh = StreamController<String>.broadcast();
  final taps = StreamController<PushMessage>.broadcast();

  @override
  Future<void> requestPermission() async => permissionAsked = true;

  @override
  Future<String?> token() async => currentToken;

  @override
  Stream<String> get tokenRefresh => refresh.stream;

  @override
  Future<void> deleteToken() async => deleted = true;

  @override
  Stream<PushMessage> get received => const Stream.empty();

  @override
  Stream<PushMessage> get opened => taps.stream;

  @override
  Future<PushMessage?> initialMessage() async => startedBy;
}

/// BR-21, task 2.17: the server gets this phone's FCM token while a user is signed in.
void main() {
  late FakeServer server;
  late FakePushClient client;
  late StreamController<bool> network;
  late List<PushMessage> opened;
  PushRegistration? registration;

  setUp(() {
    server = FakeServer()..on('PUT /me/device-token', (_) => const Reply(204));
    client = FakePushClient();
    network = StreamController<bool>.broadcast();
    opened = [];
  });

  tearDown(() async {
    registration?.dispose();
    await network.close();
  });

  Future<void> start() async {
    final dio = Dio(BaseOptions(baseUrl: 'http://server/api/v1'))..httpClientAdapter = server;
    registration = PushRegistration(client: client, dio: dio, online: network.stream, onOpened: opened.add)..start();
    await pumpEventQueue();
  }

  Iterable<Object?> sentTokens() => server.sent('PUT /me/device-token').map((r) => r.body);

  test('asks to show notifications and registers the token for Android', () async {
    await start();

    expect(client.permissionAsked, isTrue);
    expect(sentTokens(), [
      {'token': 'token-1', 'platform': 'ANDROID'},
    ]);
  });

  test('a token FCM replaces is registered again', () async {
    await start();

    client.refresh.add('token-2');
    await pumpEventQueue();

    expect(sentTokens(), [
      {'token': 'token-1', 'platform': 'ANDROID'},
      {'token': 'token-2', 'platform': 'ANDROID'},
    ]);
  });

  test('with no network, the token is registered when the network comes back, and only once', () async {
    server.offline = true;
    await start();
    expect(sentTokens(), hasLength(1), reason: 'tried');

    server.offline = false;
    network.add(true);
    await pumpEventQueue();
    network
      ..add(false)
      ..add(true);
    await pumpEventQueue();

    // the failed attempt, then the one that reached the server; the third time it is registered
    expect(sentTokens(), hasLength(2));
  });

  test('a refused token is not retried as if it were sent', () async {
    server.on('PUT /me/device-token', (_) => Reply.error(400, 'validation.pattern', 'Invalid token'));
    await start();

    network.add(true);
    await pumpEventQueue();

    expect(sentTokens(), hasLength(2), reason: 'the server does not have it, so it is sent again');
  });

  test('no token from FCM sends nothing', () async {
    client.currentToken = null;
    await start();

    expect(sentTokens(), isEmpty);
  });

  test('a tapped notification, or the one that started the app, opens it (UC-P2)', () async {
    client.startedBy = const PushMessage(title: 'A', body: 'B', notificationId: 'n-1');
    await start();
    expect(opened.map((m) => m.notificationId), ['n-1']);

    client.taps.add(const PushMessage(title: 'C', body: 'D', notificationId: 'n-2'));
    await pumpEventQueue();
    expect(opened.map((m) => m.notificationId), ['n-1', 'n-2']);
  });

  test('once signed out, nothing more is registered', () async {
    await start();
    registration!.dispose();

    client.refresh.add('token-2');
    network.add(true);
    await pumpEventQueue();

    expect(sentTokens(), hasLength(1));
  });

  testWidgets('signing in registers the token; signing out ends it, so no push reaches the phone', (tester) async {
    driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;
    final user = userJson(roles: ['PARENT']);
    server
      ..on('GET /me', (_) => Reply.ok(user))
      ..on('POST /auth/logout', (_) => const Reply(204))
      ..on('GET /auth/schools', (_) => const Reply.ok([]));
    await pumpApp(
      tester,
      server: server,
      store: MemoryDeviceStore(signedIn(user)),
      overrides: [pushClientProvider.overrideWithValue(client)],
    );
    await tester.runAsync(pumpEventQueue);
    await tester.pumpAndSettle();
    expect(sentTokens(), hasLength(1));

    await tester.tap(find.byTooltip('Compte'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Se déconnecter'));
    await tester.pumpAndSettle();

    expect(client.deleted, isTrue);
  });
}
