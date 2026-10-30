import 'dart:async';

import 'sync_queue.dart';

/// Decides when the signed-in user's queue is sent (BR-6, NFR-3): at sign-in and start-up and when
/// the network comes back (waiting backoffs skipped), as soon as a record is queued, and when the
/// next backoff is over.
class SyncScheduler {
  SyncScheduler({required this._queue, required this._userId, required this._online, DateTime Function()? now})
    : _now = now ?? DateTime.now;

  final SyncQueue _queue;
  final String _userId;
  final Stream<bool> _online;
  final DateTime Function() _now;
  final List<StreamSubscription<Object?>> _subscriptions = [];
  Timer? _timer;
  bool _disposed = false;

  void start() {
    _subscriptions
      ..add(_online.where((online) => online).listen((_) => sync(now: true)))
      ..add(_queue.enqueued.where((user) => user == _userId).listen((_) => sync()));
    unawaited(_queue.prune());
    // everything waiting goes now: a phone clock set back must not hold records until "later"
    unawaited(sync(now: true));
  }

  /// Sends what is due, then sets a timer for the next record waiting out its backoff.
  Future<void> sync({bool now = false}) async {
    await _queue.drain(_userId, now: now);
    if (_disposed) {
      return;
    }
    final next = await _queue.nextAttemptAt(_userId);
    _timer?.cancel();
    if (next != null && !_disposed) {
      final wait = next.difference(_now());
      _timer = Timer(wait < const Duration(seconds: 1) ? const Duration(seconds: 1) : wait, sync);
    }
  }

  void dispose() {
    _disposed = true;
    _timer?.cancel();
    for (final subscription in _subscriptions) {
      unawaited(subscription.cancel());
    }
  }
}
