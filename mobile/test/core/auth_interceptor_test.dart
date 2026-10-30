import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/api/auth_interceptor.dart';
import 'package:schoollink/core/auth/session_store.dart';
import 'package:schoollink/core/storage/device_store.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// NFR-7: the access token lasts 15 minutes; the app refreshes it once and replays the request.
void main() {
  late FakeServer server;
  late MemoryDeviceStore store;
  late SessionStore session;
  late Dio dio;

  setUp(() {
    server = FakeServer();
    store = MemoryDeviceStore(signedIn(userJson()));
    session = SessionStore(store);
    final options = BaseOptions(baseUrl: 'http://test/api/v1');
    dio = Dio(options)..httpClientAdapter = server;
    final refreshDio = Dio(options)..httpClientAdapter = server;
    dio.interceptors.add(AuthInterceptor(dio: dio, refreshDio: refreshDio, session: session, language: () => 'en'));
  });

  /// `GET /me` accepts only [valid] as the access token.
  void meAccepts(String valid) => server.on(
    'GET /me',
    (r) => r.authorization == 'Bearer $valid' ? Reply.ok(userJson()) : Reply.error(401, 'auth.unauthorized', 'x'),
  );

  test('sends the access token and the language', () async {
    meAccepts('access-0');

    await dio.get<dynamic>('/me');

    final sent = server.requests.single;
    expect(sent.authorization, 'Bearer access-0');
    expect(sent.headers['Accept-Language'], 'en');
  });

  test('public endpoints go without a token', () async {
    server.on('GET /auth/schools', (_) => const Reply.ok([]));

    await dio.get<dynamic>('/auth/schools', options: Options(extra: {AuthInterceptor.skipAuth: true}));

    expect(server.requests.single.authorization, isNull);
  });

  test('an expired access token is refreshed once and the request replayed', () async {
    meAccepts('access-1');
    server.on('POST /auth/refresh', (_) => Reply.ok(tokensJson('access-1', 'refresh-1')));

    final response = await dio.get<dynamic>('/me');

    expect(response.statusCode, 200);
    expect(server.sent('POST /auth/refresh').single.body, {'refreshToken': 'refresh-0'});
    expect([for (final r in server.sent('GET /me')) r.authorization], ['Bearer access-0', 'Bearer access-1']);
    // rotation: the new pair replaces the old one on the device
    expect(store.values['session.access'], 'access-1');
    expect(store.values['session.refresh'], 'refresh-1');
  });

  test('requests that fail together share one refresh (a reused refresh token ends the session)', () async {
    meAccepts('access-1');
    final release = Completer<void>();
    server.on('POST /auth/refresh', (_) async {
      await release.future;
      return Reply.ok(tokensJson('access-1', 'refresh-1'));
    });

    final calls = [dio.get<dynamic>('/me'), dio.get<dynamic>('/me'), dio.get<dynamic>('/me')];
    await pumpEventQueue();
    release.complete();
    final responses = await Future.wait(calls);

    expect(responses.map((r) => r.statusCode), [200, 200, 200]);
    expect(server.sent('POST /auth/refresh'), hasLength(1));
  });

  test('a refused refresh token ends the session on the device', () async {
    meAccepts('access-1');
    server.on('POST /auth/refresh', (_) => Reply.error(401, 'auth.invalid_refresh_token', 'expired'));
    final expirations = <void>[];
    session.expirations.listen(expirations.add);

    final failure = dio.get<dynamic>('/me');

    await expectLater(failure, throwsA(isA<DioException>().having((e) => e.response?.statusCode, 'status', 401)));
    expect(store.values.containsKey('session.refresh'), isFalse);
    expect(store.values.containsKey('session.access'), isFalse);
    expect(expirations, hasLength(1));
  });

  test('no network during the refresh keeps the session', () async {
    meAccepts('access-1');
    server.on('POST /auth/refresh', (_) => Reply.unreachable);

    await expectLater(
      dio.get<dynamic>('/me'),
      throwsA(isA<DioException>().having((e) => e.type, 'type', DioExceptionType.connectionError)),
    );
    expect(store.values['session.refresh'], 'refresh-0');
  });

  test('a request still refused after the refresh is not retried again', () async {
    server.on('GET /me', (_) => Reply.error(401, 'auth.unauthorized', 'x'));
    server.on('POST /auth/refresh', (_) => Reply.ok(tokensJson('access-1', 'refresh-1')));

    await expectLater(dio.get<dynamic>('/me'), throwsA(isA<DioException>()));

    expect(server.sent('GET /me'), hasLength(2));
    expect(server.sent('POST /auth/refresh'), hasLength(1));
  });
}
