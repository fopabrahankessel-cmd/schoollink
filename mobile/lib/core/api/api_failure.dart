import 'package:dio/dio.dart';

/// An API error in the server's single shape (SPEC §9: `code`, `message`, `field`). The server
/// writes [message] in the user's language (NFR-12); [code] is stable and is what the app tests.
class ApiFailure implements Exception {
  const ApiFailure({required this.status, required this.code, this.message, this.field});

  /// The server could not be reached; the screens say so in their own words.
  const ApiFailure.network() : this(status: 0, code: networkCode);

  static const networkCode = 'network';

  final int status;
  final String code;
  final String? message;
  final String? field;

  bool get isNetwork => code == networkCode;

  static ApiFailure from(DioException e) {
    final response = e.response;
    if (response == null) {
      return const ApiFailure.network();
    }
    final data = response.data;
    if (data is Map && data['code'] is String) {
      return ApiFailure(
        status: response.statusCode ?? 0,
        code: data['code'] as String,
        message: data['message'] as String?,
        field: data['field'] as String?,
      );
    }
    return ApiFailure(status: response.statusCode ?? 0, code: 'http_${response.statusCode}');
  }

  @override
  String toString() => 'ApiFailure($status, $code, field: $field)';
}
