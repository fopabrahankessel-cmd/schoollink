import 'dart:async';
import 'dart:ui';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import '../../l10n/app_localizations.dart';
import '../theme.dart';

/// A push as the server sends it (BR-21): the title and body already in the user's language, and
/// the id of the notification, which the inbox opens (BR-24).
class PushMessage {
  const PushMessage({required this.title, required this.body, required this.notificationId});

  final String? title;
  final String? body;
  final String? notificationId;
}

/// What the app needs from FCM. Faked in tests; [FirebasePushClient] on the phone.
abstract interface class PushClient {
  /// Asks to show notifications (Android 13+ asks the user once).
  Future<void> requestPermission();

  /// This installation's FCM token; null when FCM cannot give one (no Google Play services).
  Future<String?> token();

  /// A new token, when FCM replaces the old one.
  Stream<String> get tokenRefresh;

  /// Ends this installation's token at FCM: the server finds it dead at its next send and
  /// forgets it, so a signed-out phone receives nothing more.
  Future<void> deleteToken();

  /// Pushes received while the app is open (Android does not show those by itself).
  Stream<PushMessage> get received;

  /// Notifications the user tapped, the app having been closed or in the background.
  Stream<PushMessage> get opened;

  /// The notification that started the app, if one did.
  Future<PushMessage?> initialMessage();
}

/// The Firebase project of the app, given at build time so no key is committed:
/// `--dart-define=FIREBASE_API_KEY=… --dart-define=FIREBASE_APP_ID=…`
/// `--dart-define=FIREBASE_SENDER_ID=… --dart-define=FIREBASE_PROJECT_ID=…`.
/// Without them the app works fully, without push.
class FirebaseConfig {
  static const apiKey = String.fromEnvironment('FIREBASE_API_KEY');
  static const appId = String.fromEnvironment('FIREBASE_APP_ID');
  static const senderId = String.fromEnvironment('FIREBASE_SENDER_ID');
  static const projectId = String.fromEnvironment('FIREBASE_PROJECT_ID');

  static bool get present => apiKey.isNotEmpty && appId.isNotEmpty && senderId.isNotEmpty && projectId.isNotEmpty;

  static FirebaseOptions get options =>
      const FirebaseOptions(apiKey: apiKey, appId: appId, messagingSenderId: senderId, projectId: projectId);
}

/// The Android channel of SchoolLink's notifications: high importance, so they sound and show at
/// once (BR-21). Its name, seen in the phone's settings, is in the user's language (NFR-12).
const alertsChannelId = 'schoollink_alerts';

/// BR-21: the SchoolLink mark in the status bar (android/app/src/main/res/drawable), the same one FCM uses
/// for pushes that arrive while the app is closed (AndroidManifest.xml).
const notificationIcon = 'ic_stat_schoollink';

class FirebasePushClient implements PushClient {
  FirebasePushClient._(this._messaging, this._local);

  final FirebaseMessaging _messaging;
  final FlutterLocalNotificationsPlugin _local;
  final _tapped = StreamController<PushMessage>.broadcast();

  /// Starts Firebase and the notification channel. Null when the app was built without a
  /// Firebase project, or Firebase cannot start: the app then runs without push.
  static Future<FirebasePushClient?> start(Locale locale) async {
    if (!FirebaseConfig.present) {
      return null;
    }
    try {
      await Firebase.initializeApp(options: FirebaseConfig.options);
      final local = FlutterLocalNotificationsPlugin();
      final client = FirebasePushClient._(FirebaseMessaging.instance, local);
      await local.initialize(
        settings: const InitializationSettings(android: AndroidInitializationSettings(notificationIcon)),
        // a notification the app showed itself, while open, and the user tapped
        onDidReceiveNotificationResponse: (response) =>
            client._tapped.add(PushMessage(title: null, body: null, notificationId: response.payload)),
      );
      final l = lookupAppLocalizations(locale);
      await local
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(
            AndroidNotificationChannel(
              alertsChannelId,
              l.notificationChannelName,
              description: l.notificationChannelDescription,
              importance: Importance.high,
            ),
          );
      // shows pushes received while the app is open, which Android leaves to the app
      FirebaseMessaging.onMessage.listen((message) => client._show(message, l));
      return client;
    } catch (e) {
      // NFR-9: no token or message content in the log, only that push is off
      debugPrint('Push notifications are off: ${e.runtimeType}');
      return null;
    }
  }

  static PushMessage _of(RemoteMessage m) => PushMessage(
    title: m.notification?.title,
    body: m.notification?.body,
    notificationId: m.data['notificationId'] as String?,
  );

  Future<void> _show(RemoteMessage message, AppLocalizations l) async {
    final push = _of(message);
    await _local.show(
      id: (push.notificationId ?? message.messageId ?? '').hashCode,
      title: push.title ?? l.appTitle,
      body: push.body,
      payload: push.notificationId,
      notificationDetails: NotificationDetails(
        android: AndroidNotificationDetails(
          alertsChannelId,
          l.notificationChannelName,
          channelDescription: l.notificationChannelDescription,
          importance: Importance.high,
          priority: Priority.high,
          color: brandBlue,
          styleInformation: BigTextStyleInformation(push.body ?? ''),
        ),
      ),
    );
  }

  @override
  Future<void> requestPermission() async {
    await _messaging.requestPermission();
  }

  // The classic FCM registration token, which the server's Firebase Admin SDK sends to
  // (`addAllTokens`). firebase_messaging 17 deprecates it for installation ids (register());
  // move both sides together when the server supports those.
  @override
  // ignore: deprecated_member_use
  Future<String?> token() => _messaging.getToken();

  @override
  // ignore: deprecated_member_use
  Stream<String> get tokenRefresh => _messaging.onTokenRefresh;

  @override
  // ignore: deprecated_member_use
  Future<void> deleteToken() => _messaging.deleteToken();

  @override
  Stream<PushMessage> get received => FirebaseMessaging.onMessage.map(_of);

  @override
  Stream<PushMessage> get opened => _merge([FirebaseMessaging.onMessageOpenedApp.map(_of), _tapped.stream]);

  @override
  Future<PushMessage?> initialMessage() async {
    final message = await _messaging.getInitialMessage();
    return message == null ? null : _of(message);
  }
}

/// The events of several broadcast streams in one.
Stream<T> _merge<T>(List<Stream<T>> streams) {
  final subscriptions = <StreamSubscription<T>>[];
  late final StreamController<T> controller;
  controller = StreamController<T>.broadcast(
    onListen: () {
      for (final s in streams) {
        subscriptions.add(s.listen(controller.add, onError: controller.addError));
      }
    },
    onCancel: () async {
      for (final s in subscriptions) {
        await s.cancel();
      }
      subscriptions.clear();
    },
  );
  return controller.stream;
}
