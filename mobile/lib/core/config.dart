/// The API root. Debug builds reach the backend on the development machine through the Android
/// emulator's host alias; release builds pass the HTTPS address (NFR-7):
/// `flutter build apk --dart-define=API_BASE_URL=https://.../api/v1`.
const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080/api/v1');
