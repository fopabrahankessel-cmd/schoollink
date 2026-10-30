import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Small values kept on the device: tokens, the signed-in user, the remembered school (D16) and
/// the language chosen before sign-in. Backed by the Android Keystore (NFR-7).
abstract interface class DeviceStore {
  Future<String?> read(String key);

  Future<void> write(String key, String value);

  Future<void> delete(String key);
}

class SecureDeviceStore implements DeviceStore {
  SecureDeviceStore([this._storage = const FlutterSecureStorage()]);

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) => _storage.write(key: key, value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: key);
}

/// For tests, and nothing else.
class MemoryDeviceStore implements DeviceStore {
  MemoryDeviceStore([Map<String, String>? values]) : values = values ?? {};

  final Map<String, String> values;

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;

  @override
  Future<void> delete(String key) async => values.remove(key);
}
