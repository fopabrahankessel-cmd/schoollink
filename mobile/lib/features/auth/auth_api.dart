import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_failure.dart';
import '../../core/api/auth_interceptor.dart';
import '../../core/auth/user_info.dart';
import '../../core/providers.dart';

/// A school in the picker shown before sign-in (D16).
class School {
  const School({required this.id, required this.name});

  final String id;
  final String name;
}

/// UC-P1 step 1 done: the SMS one-time code is on its way to the phone.
class ActivationStarted {
  const ActivationStarted({required this.activationId, required this.expiresAt});

  final String activationId;

  /// When the one-time code stops working (10 minutes).
  final DateTime expiresAt;
}

/// The auth endpoints of SPEC §9. Every failure is an [ApiFailure].
class AuthApi {
  AuthApi(this._dio);

  final Dio _dio;

  static final _public = Options(extra: {AuthInterceptor.skipAuth: true});

  Future<List<School>> schools() => _call(() async {
    final response = await _dio.get<List<dynamic>>('/auth/schools', options: _public);
    return [
      for (final school in response.data!.cast<Map<String, dynamic>>())
        School(id: school['id'] as String, name: school['name'] as String),
    ];
  });

  /// Staff only; parents sign in through activation (UC-P1). The phone travels in the body,
  /// never in the URL (NFR-9).
  Future<TokenResponse> login({String? schoolId, required String login, required String password}) =>
      _tokens('/auth/login', {'schoolId': schoolId, 'login': login, 'password': password}, _public);

  /// UC-P1 step 1: the invitation code from the school's SMS and the guardian's phone (in the
  /// body, never the URL, NFR-9). The server sends a one-time code by SMS.
  Future<ActivationStarted> activateParent({required String code, required String phone}) => _call(() async {
    final response = await _dio.post<Map<String, dynamic>>(
      '/auth/parent/activate',
      data: {'code': code, 'phone': phone},
      options: _public,
    );
    return ActivationStarted(
      activationId: response.data!['activationId'] as String,
      expiresAt: DateTime.parse(response.data!['expiresAt'] as String).toLocal(),
    );
  });

  /// UC-P1 step 2: the one-time code; the account is made and the parent signed in.
  Future<TokenResponse> verifyParent({required String activationId, required String otp}) =>
      _tokens('/auth/parent/verify', {'activationId': activationId, 'otp': otp}, _public);

  Future<UserInfo> me() => _call(() async {
    final response = await _dio.get<Map<String, dynamic>>('/me');
    return UserInfo.fromJson(response.data!);
  });

  /// D18: replaces the temporary password; the server ends the other sessions and returns new tokens.
  Future<TokenResponse> changePassword({required String currentPassword, required String newPassword}) =>
      _tokens('/me/password', {'currentPassword': currentPassword, 'newPassword': newPassword}, null);

  Future<void> logout(String refreshToken) =>
      _call(() => _dio.post<void>('/auth/logout', data: {'refreshToken': refreshToken}, options: _public));

  Future<TokenResponse> _tokens(String path, Map<String, dynamic> body, Options? options) => _call(() async {
    final response = await _dio.post<Map<String, dynamic>>(path, data: body, options: options);
    return TokenResponse.fromJson(response.data!);
  });

  static Future<T> _call<T>(Future<T> Function() request) async {
    try {
      return await request();
    } on DioException catch (e) {
      throw ApiFailure.from(e);
    }
  }
}

final authApiProvider = Provider<AuthApi>((ref) => AuthApi(ref.watch(dioProvider)));
