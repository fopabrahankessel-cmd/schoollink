import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

/// A canned answer: status and JSON body.
class Reply {
  const Reply(this.status, [this.body]) : contentType = null;

  const Reply.ok([Object? body]) : this(200, body);

  /// The server's error shape (SPEC §9).
  Reply.error(int status, String code, String message, {String? field})
    : this(status, {'code': code, 'message': message, 'field': ?field});

  /// A file (e.g. the school logo): [body] holds the bytes, sent as they are.
  const Reply.bytes(List<int> bytes, String this.contentType) : status = 200, body = bytes;

  /// The request never reaches the server.
  static const unreachable = Reply(-1);

  final int status;
  final Object? body;
  final String? contentType;
}

/// What the app sent.
class Sent {
  const Sent(this.method, this.path, this.headers, this.body, {this.query = const {}});

  final String method;
  final String path;
  final Map<String, dynamic> headers;
  final Object? body;

  /// The query string, kept apart from [path] so routes match without it.
  final Map<String, String> query;

  String? get authorization => headers['Authorization'] as String?;
}

typedef Handler = FutureOr<Reply> Function(Sent request);

/// Stands in for the backend: handlers by "METHOD /path" (path after /api/v1), or "METHOD /path/*"
/// for any last segment. A missing
/// handler fails the test; [offline] makes every request fail as if there were no network.
class FakeServer implements HttpClientAdapter {
  final Map<String, Handler> handlers = {};
  final List<Sent> requests = [];
  bool offline = false;

  void on(String route, Handler handler) => handlers[route] = handler;

  Iterable<Sent> sent(String route) => requests.where((r) => '${r.method} ${r.path}' == route);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final path = options.uri.path.replaceFirst('/api/v1', '');
    final request = Sent(
      options.method,
      path,
      Map.of(options.headers),
      options.data,
      query: options.uri.queryParameters,
    );
    requests.add(request);
    if (offline) {
      throw DioException.connectionError(requestOptions: options, reason: 'offline');
    }
    final route = '${options.method} $path';
    // "PUT /sessions/*" answers any id the phone made up
    final handler = handlers[route] ?? handlers['${route.substring(0, route.lastIndexOf('/'))}/*'];
    if (handler == null) {
      throw StateError('No fake handler for ${options.method} $path');
    }
    final reply = await handler(request);
    if (identical(reply, Reply.unreachable)) {
      throw DioException.connectionError(requestOptions: options, reason: 'unreachable');
    }
    if (reply.contentType case final type?) {
      return ResponseBody.fromBytes(
        reply.body! as List<int>,
        reply.status,
        headers: {
          Headers.contentTypeHeader: [type],
        },
      );
    }
    return ResponseBody.fromString(
      reply.body == null ? '' : jsonEncode(reply.body),
      reply.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

/// A user as `GET /me` returns it.
Map<String, dynamic> userJson({
  List<String> roles = const ['TEACHER'],
  String language = 'FR',
  bool passwordChangeRequired = false,
  String schoolId = 'school-1',
}) => {
  'id': 'user-1',
  'schoolId': schoolId,
  'roles': roles,
  'language': language,
  'passwordChangeRequired': passwordChangeRequired,
};

Map<String, dynamic> tokensJson(String access, String refresh, {Map<String, dynamic>? user}) => {
  'tokenType': 'Bearer',
  'accessToken': access,
  'expiresIn': 900,
  'refreshToken': refresh,
  'user': user ?? userJson(),
};
