import 'dart:async';
import 'dart:convert';

import '../storage/device_store.dart';
import 'user_info.dart';

/// The session kept on the device: both tokens and the signed-in user, so a teacher can open the
/// app with no network (NFR-3). The access token is also held in memory, as every request reads it.
class SessionStore {
  SessionStore(this._store);

  static const _accessKey = 'session.access';
  static const _refreshKey = 'session.refresh';
  static const _userKey = 'session.user';
  static const _schoolKey = 'device.school';

  final DeviceStore _store;
  final _expirations = StreamController<void>.broadcast();
  String? _access;
  bool _accessLoaded = false;

  /// Fires when the server refuses the refresh token: the user must sign in again.
  Stream<void> get expirations => _expirations.stream;

  Future<String?> accessToken() async {
    if (!_accessLoaded) {
      _access = await _store.read(_accessKey);
      _accessLoaded = true;
    }
    return _access;
  }

  Future<String?> refreshToken() => _store.read(_refreshKey);

  Future<UserInfo?> user() async {
    final json = await _store.read(_userKey);
    return json == null ? null : UserInfo.fromJson(jsonDecode(json) as Map<String, dynamic>);
  }

  Future<void> save(TokenResponse tokens) async {
    _access = tokens.accessToken;
    _accessLoaded = true;
    await _store.write(_accessKey, tokens.accessToken);
    await _store.write(_refreshKey, tokens.refreshToken);
    await saveUser(tokens.user);
  }

  Future<void> saveUser(UserInfo user) async {
    await _store.write(_userKey, jsonEncode(user.toJson()));
    if (user.schoolId case final school?) {
      await _store.write(_schoolKey, school);
    }
  }

  /// D16: the school picker starts on the school of the last user who signed in on this device.
  Future<String?> rememberedSchool() => _store.read(_schoolKey);

  /// Sign-out. The remembered school stays.
  Future<void> clear() async {
    _access = null;
    _accessLoaded = true;
    await _store.delete(_accessKey);
    await _store.delete(_refreshKey);
    await _store.delete(_userKey);
  }

  /// The refresh token was refused: clears the session and tells whoever listens.
  Future<void> expire() async {
    await clear();
    _expirations.add(null);
  }

  void dispose() => _expirations.close();
}
