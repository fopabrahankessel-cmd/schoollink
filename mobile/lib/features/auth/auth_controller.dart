import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_failure.dart';
import '../../core/auth/user_info.dart';
import '../../core/providers.dart';
import '../../core/push/push_registration.dart';
import 'auth_api.dart';

/// Who is signed in: loading while the session is restored at start-up, then the user, or null
/// when signed out. The screens follow it through the router.
class AuthController extends AsyncNotifier<UserInfo?> {
  @override
  Future<UserInfo?> build() async {
    final session = ref.watch(sessionStoreProvider);
    final expired = session.expirations.listen((_) => state = const AsyncData(null));
    ref.onDispose(expired.cancel);

    if (await session.refreshToken() == null) {
      return null;
    }
    final cached = await session.user();
    try {
      final user = await ref.read(authApiProvider).me();
      await session.saveUser(user);
      return user;
    } on ApiFailure catch (e) {
      if (e.status == 401) {
        await session.clear();
        return null;
      }
      // NFR-3: no network at start-up; the teacher keeps working with what the device knows
      return cached;
    }
  }

  /// Throws an [ApiFailure] for the screen to show; the state changes only on success.
  Future<void> signIn({String? schoolId, required String login, required String password}) async {
    final tokens = await ref.read(authApiProvider).login(schoolId: schoolId, login: login, password: password);
    await ref.read(sessionStoreProvider).save(tokens);
    state = AsyncData(tokens.user);
  }

  /// D18: replaces the temporary password. Throws an [ApiFailure] for the screen to show.
  Future<void> changePassword({required String currentPassword, required String newPassword}) async {
    final tokens = await ref
        .read(authApiProvider)
        .changePassword(currentPassword: currentPassword, newPassword: newPassword);
    await ref.read(sessionStoreProvider).save(tokens);
    state = AsyncData(tokens.user);
  }

  /// Signs out on the device at once; tells the server when it can (it revokes the refresh token).
  /// The phone's push token is ended at FCM, so no notification of this user reaches it any more
  /// (the server forgets a dead token at its next send); the next sign-in registers a new one.
  Future<void> signOut() async {
    unawaited(ref.read(pushClientProvider)?.deleteToken().catchError((_) {}));
    final session = ref.read(sessionStoreProvider);
    final refreshToken = await session.refreshToken();
    await session.clear();
    state = const AsyncData(null);
    if (refreshToken != null) {
      unawaited(ref.read(authApiProvider).logout(refreshToken).catchError((_) {}));
    }
  }
}

final authControllerProvider = AsyncNotifierProvider<AuthController, UserInfo?>(AuthController.new);
