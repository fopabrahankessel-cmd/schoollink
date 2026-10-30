import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/auth_controller.dart';
import '../../router.dart';
import '../api/api_failure.dart';
import '../auth/user_info.dart';
import '../providers.dart';
import '../sync/sync_providers.dart';
import 'push_client.dart';

/// BR-21: while a user is signed in, the server knows this phone's FCM token
/// (`PUT /me/device-token`, idempotent): at sign-in and start-up, when FCM replaces the token,
/// and when the network comes back after a failed attempt. A tapped notification opens the app.
class PushRegistration {
  PushRegistration({required this._client, required this._dio, required this._online, required this._onOpened});

  final PushClient _client;
  final Dio _dio;
  final Stream<bool> _online;
  final void Function(PushMessage) _onOpened;
  final List<StreamSubscription<Object?>> _subscriptions = [];

  /// The token the server has from this sign-in; null until it has one.
  String? _registered;
  bool _disposed = false;

  void start() {
    _subscriptions
      ..add(_client.tokenRefresh.listen(_register))
      ..add(_online.where((online) => online).listen((_) => unawaited(register())))
      ..add(_client.opened.listen(_onOpened));
    unawaited(_startUp());
  }

  Future<void> _startUp() async {
    try {
      await _client.requestPermission();
    } catch (_) {
      // refused or not available: the token is still registered, the phone shows nothing
    }
    await register();
    final initial = await _client.initialMessage().catchError((_) => null);
    if (initial != null && !_disposed) {
      _onOpened(initial);
    }
  }

  /// Sends the current token unless the server already has it from this sign-in.
  Future<void> register() async {
    final String? token;
    try {
      token = await _client.token();
    } catch (_) {
      return; // FCM unreachable: tried again when the network comes back
    }
    if (token != null && token != _registered) {
      await _register(token);
    }
  }

  Future<void> _register(String token) async {
    if (_disposed) {
      return;
    }
    try {
      await _dio.put<void>('/me/device-token', data: {'token': token, 'platform': 'ANDROID'});
      _registered = token;
    } on DioException catch (e) {
      // NFR-9: never the token in the log
      final failure = ApiFailure.from(e);
      if (!failure.isNetwork) {
        debugPrint('Device token refused: ${failure.status} ${failure.code}');
      }
    }
  }

  void dispose() {
    _disposed = true;
    for (final subscription in _subscriptions) {
      unawaited(subscription.cancel());
    }
  }
}

/// The phone's FCM client, started in `main`; null when the app runs without push.
final pushClientProvider = Provider<PushClient?>((ref) => null);

/// Runs while a user is signed in with a usable session, like the sync scheduler. Kept alive by
/// the app.
final pushRegistrationProvider = Provider<PushRegistration?>((ref) {
  final client = ref.watch(pushClientProvider);
  final userId = ref.watch(
    authControllerProvider.select((auth) => auth.value?.passwordChangeRequired == false ? auth.value!.id : null),
  );
  if (client == null || userId == null) {
    return null;
  }
  final registration = PushRegistration(
    client: client,
    dio: ref.watch(dioProvider),
    online: ref.watch(connectivityProvider),
    onOpened: (message) {
      // UC-P2: a parent lands on the message, which sends its receipt (BR-24), or in their
      // notifications; staff on their home screen
      final roles = ref.read(authControllerProvider).value?.roles ?? const <Role>{};
      final router = ref.read(routerProvider);
      if (!roles.contains(Role.parent)) {
        router.go('/home');
      } else if (message.notificationId case final id?) {
        router
          ..go('/home/inbox')
          ..push('/home/inbox/message/$id');
      } else {
        router.go('/home/inbox');
      }
    },
  )..start();
  ref.onDispose(registration.dispose);
  return registration;
});
