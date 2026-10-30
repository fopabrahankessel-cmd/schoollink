import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/auth/auth_controller.dart';
import '../db/database.dart';
import '../providers.dart';
import 'sync_queue.dart';
import 'sync_scheduler.dart';

/// Overridden in tests with an in-memory database.
final appDatabaseProvider = Provider<AppDatabase>((ref) {
  final db = AppDatabase.onDevice();
  ref.onDispose(db.close);
  return db;
});

/// True when the phone gets a network connection, false when it loses it. Overridden in tests.
final connectivityProvider = Provider<Stream<bool>>(
  (ref) => Connectivity().onConnectivityChanged.map((results) => results.any((r) => r != ConnectivityResult.none)),
);

final syncQueueProvider = Provider<SyncQueue>((ref) {
  final queue = SyncQueue(ref.watch(appDatabaseProvider), ref.watch(dioProvider));
  ref.onDispose(queue.dispose);
  return queue;
});

/// Runs while a user is signed in with a usable session (not while a temporary password must be
/// replaced, D18). Kept alive by the app.
final syncSchedulerProvider = Provider<SyncScheduler?>((ref) {
  final userId = ref.watch(
    authControllerProvider.select((auth) => auth.value?.passwordChangeRequired == false ? auth.value!.id : null),
  );
  if (userId == null) {
    return null;
  }
  final scheduler = SyncScheduler(
    queue: ref.watch(syncQueueProvider),
    userId: userId,
    online: ref.watch(connectivityProvider),
  )..start();
  ref.onDispose(scheduler.dispose);
  return scheduler;
});

/// The signed-in user's records not on the server yet.
final pendingSyncProvider = StreamProvider<int>((ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  return userId == null ? Stream.value(0) : ref.watch(syncQueueProvider).watchPending(userId);
});
