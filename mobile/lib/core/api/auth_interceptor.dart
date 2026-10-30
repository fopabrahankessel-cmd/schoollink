import 'package:dio/dio.dart';

import '../auth/session_store.dart';
import '../auth/user_info.dart';

/// Sends the access token and the user's language with each request (NFR-12). On a 401 it
/// refreshes the tokens once and replays the request (NFR-7: access 15 min, refresh 30 days).
/// A refresh token is single use and reusing one revokes the whole session on the server, so
/// requests that fail together share one refresh.
class AuthInterceptor extends Interceptor {
  AuthInterceptor({required this._dio, required this._refreshDio, required this._session, required this._language});

  /// Set in [Options.extra] for the endpoints used before sign-in (login, schools, logout).
  static const skipAuth = 'skipAuth';

  static const _retried = 'authRetried';

  final Dio _dio;
  final Dio _refreshDio;
  final SessionStore _session;
  final String Function() _language;
  Future<String?>? _refreshing;

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    options.headers['Accept-Language'] = _language();
    if (options.extra[skipAuth] != true) {
      final token = await _session.accessToken();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    }
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final options = err.requestOptions;
    final sent = options.headers['Authorization'];
    if (err.response?.statusCode != 401 || sent == null || options.extra[_retried] == true) {
      handler.next(err);
      return;
    }
    final String? token;
    try {
      final current = await _session.accessToken();
      // another request refreshed while this one was in flight: just replay it
      token = current != null && 'Bearer $current' != sent ? current : await _refresh();
    } on DioException catch (e) {
      handler.next(e); // no network: the session stays, the caller sees a network error
      return;
    }
    if (token == null) {
      handler.next(err);
      return;
    }
    options.extra[_retried] = true;
    try {
      handler.resolve(await _dio.fetch<dynamic>(options));
    } on DioException catch (e) {
      handler.next(e);
    }
  }

  Future<String?> _refresh() => _refreshing ??= _doRefresh().whenComplete(() => _refreshing = null);

  /// The new access token, or null when the server refused the refresh token (session over).
  Future<String?> _doRefresh() async {
    final refreshToken = await _session.refreshToken();
    if (refreshToken == null) {
      await _session.expire();
      return null;
    }
    try {
      final response = await _refreshDio.post<Map<String, dynamic>>(
        '/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final tokens = TokenResponse.fromJson(response.data!);
      await _session.save(tokens);
      return tokens.accessToken;
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) {
        await _session.expire();
        return null;
      }
      rethrow;
    }
  }
}
