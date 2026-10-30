import 'dart:ui';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../features/auth/auth_controller.dart';
import 'api/auth_interceptor.dart';
import 'auth/session_store.dart';
import 'config.dart';
import 'storage/device_store.dart';

/// Overridden in `main` with the secure store, and in tests with a memory one.
final deviceStoreProvider = Provider<DeviceStore>((ref) => throw UnimplementedError('set in main'));

/// Overridden in tests with a fake server.
final httpAdapterProvider = Provider<HttpClientAdapter?>((ref) => null);

final sessionStoreProvider = Provider<SessionStore>((ref) {
  final session = SessionStore(ref.watch(deviceStoreProvider));
  ref.onDispose(session.dispose);
  return session;
});

final dioProvider = Provider<Dio>((ref) {
  final options = BaseOptions(
    baseUrl: apiBaseUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 20),
    contentType: Headers.jsonContentType,
  );
  final dio = Dio(options);
  final refreshDio = Dio(options);
  if (ref.watch(httpAdapterProvider) case final adapter?) {
    dio.httpClientAdapter = adapter;
    refreshDio.httpClientAdapter = adapter;
  }
  dio.interceptors.add(
    AuthInterceptor(
      dio: dio,
      refreshDio: refreshDio,
      session: ref.watch(sessionStoreProvider),
      language: () => ref.read(appLocaleProvider).languageCode,
    ),
  );
  ref.onDispose(() {
    dio.close();
    refreshDio.close();
  });
  return dio;
});

/// The language chosen on the sign-in screen, kept on the device. Before the first choice: the
/// phone's language if it is English, French otherwise.
class PreferredLanguage extends Notifier<String> {
  static const key = 'device.language';

  @override
  String build() => ref.watch(initialLanguageProvider);

  Future<void> set(String language) async {
    state = language;
    await ref.read(deviceStoreProvider).write(key, language);
  }
}

/// Read from the device store in `main`, before the first frame.
final initialLanguageProvider = Provider<String>(
  (ref) => PlatformDispatcher.instance.locale.languageCode == 'en' ? 'en' : 'fr',
);

final preferredLanguageProvider = NotifierProvider<PreferredLanguage, String>(PreferredLanguage.new);

/// NFR-12: a signed-in user sees the language stored on their account, anyone else the one
/// chosen on this device.
final appLocaleProvider = Provider<Locale>((ref) {
  final user = ref.watch(authControllerProvider).value;
  final language = user?.language.toLowerCase() ?? ref.watch(preferredLanguageProvider);
  return Locale(language == 'en' ? 'en' : 'fr');
});

/// The phone's clock. Overridden in tests.
final clockProvider = Provider<DateTime Function()>((ref) => DateTime.now);

/// No automatic retry of failed providers: the screens offer their own "Retry", and the sync queue
/// has its own backoff (D22).
Duration? noRetry(int retryCount, Object error) => null;
