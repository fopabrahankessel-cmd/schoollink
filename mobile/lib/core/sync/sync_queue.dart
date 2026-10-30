import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart';

import '../api/api_failure.dart';
import '../db/database.dart';

/// BR-6, NFR-3: the records made offline (attendance sessions, lateness), sent to the server when
/// it can be reached. The device's UUID is the record's identity, and the server's endpoints are
/// idempotent on it, so a record sent twice (the app killed before it saw the answer) is stored
/// once. Records go one at a time, oldest first, by one run at a time.
class SyncQueue {
  SyncQueue(this._db, this._dio, {DateTime Function()? now}) : _now = now ?? DateTime.now;

  /// The wait before attempt n+1 after n failures: 5 s, 10 s, 20 s... at most 15 minutes.
  static Duration backoff(int attempts) {
    final seconds = 5 * pow(2, max(attempts - 1, 0));
    return Duration(seconds: min(seconds, 15 * 60).toInt());
  }

  /// Synced records are kept a while so the screens can show them, then deleted.
  static const keepSynced = Duration(days: 30);

  final AppDatabase _db;
  final Dio _dio;
  final DateTime Function() _now;
  final _enqueued = StreamController<String>.broadcast();
  Future<void>? _running;
  bool _again = false;
  bool _againNow = false;

  /// The users whose records were just queued, for the scheduler to send them.
  Stream<String> get enqueued => _enqueued.stream;

  /// Queues a record. Call it in the same drift transaction that saves the record itself, so
  /// neither exists without the other. A UUID already queued is left as it is: a confirmed
  /// record never changes (BR-4).
  Future<void> enqueue({
    required String id,
    required String userId,
    required String kind,
    required String path,
    required Map<String, dynamic> body,
  }) async {
    final now = _now();
    await _db
        .into(_db.syncItems)
        .insert(
          SyncItemsCompanion.insert(
            id: id,
            userId: userId,
            kind: kind,
            path: path,
            body: jsonEncode(body),
            state: SyncState.pending,
            nextAttemptAt: now,
            createdAt: now,
          ),
          mode: InsertMode.insertOrIgnore,
        );
    _enqueued.add(userId);
  }

  Stream<SyncItem?> watch(String id) => (_db.select(_db.syncItems)..where((i) => i.id.equals(id))).watchSingleOrNull();

  /// The user's records not on the server yet ("Waiting to sync", UC-T2).
  Stream<int> watchPending(String userId) => _pendingCount(userId).watchSingle();

  Future<int> pendingCount(String userId) => _pendingCount(userId).getSingle();

  /// When the user's next waiting record is due, if any.
  Future<DateTime?> nextAttemptAt(String userId) {
    final next = _db.syncItems.nextAttemptAt.min();
    final query = _db.selectOnly(_db.syncItems)
      ..addColumns([next])
      ..where(_db.syncItems.userId.equals(userId) & _db.syncItems.state.equalsValue(SyncState.pending));
    return query.map((row) => row.read(next)).getSingle();
  }

  /// Sends the user's records that are due; with [now], also those waiting out a backoff (the
  /// network just came back). A call during a run makes the run go once more when it ends.
  Future<void> drain(String userId, {bool now = false}) {
    if (_running case final running?) {
      _again = true;
      _againNow |= now;
      return running;
    }
    return _running = _runUntilQuiet(userId, now).whenComplete(() => _running = null);
  }

  /// Deletes records synced long ago.
  Future<void> prune() =>
      (_db.delete(_db.syncItems)..where(
            (i) => i.state.equalsValue(SyncState.synced) & i.syncedAt.isSmallerThanValue(_now().subtract(keepSynced)),
          ))
          .go();

  void dispose() => _enqueued.close();

  Future<void> _runUntilQuiet(String userId, bool now) async {
    var ignoreBackoff = now;
    do {
      _again = false;
      _againNow = false;
      await _run(userId, ignoreBackoff);
      ignoreBackoff = _againNow;
    } while (_again);
  }

  Future<void> _run(String userId, bool ignoreBackoff) async {
    final due =
        await (_db.select(_db.syncItems)
              ..where(
                (i) =>
                    i.userId.equals(userId) &
                    i.state.equalsValue(SyncState.pending) &
                    (ignoreBackoff ? const Constant(true) : i.nextAttemptAt.isSmallerOrEqualValue(_now())),
              )
              ..orderBy([(i) => OrderingTerm(expression: i.createdAt), (i) => OrderingTerm(expression: i.id)]))
            .get();
    for (final item in due) {
      if (!await _send(item)) {
        return;
      }
    }
  }

  /// Sends one record and records the outcome. False when the run must stop: no network, or no
  /// valid session (the records wait for the user to sign in again).
  Future<bool> _send(SyncItem item) async {
    final row = _db.update(_db.syncItems)..where((i) => i.id.equals(item.id));
    try {
      final response = await _dio.put<dynamic>(item.path, data: jsonDecode(item.body));
      await row.write(
        SyncItemsCompanion(
          state: const Value(SyncState.synced),
          attempts: Value(item.attempts + 1),
          syncedAt: Value(_now()),
          httpStatus: Value(response.statusCode),
          errorCode: const Value(null),
          errorMessage: const Value(null),
        ),
      );
      return true;
    } on DioException catch (e) {
      final failure = ApiFailure.from(e);
      final status = failure.status;
      final failed = SyncItemsCompanion(
        attempts: Value(item.attempts + 1),
        httpStatus: Value(status == 0 ? null : status),
        errorCode: Value(failure.code),
        errorMessage: Value(failure.message),
      );
      if (status == 401 || failure.code == 'auth.password_change_required') {
        return false; // the session ended (D18 included): nothing is lost, nothing is counted
      }
      if (status == 409) {
        await row.write(failed.copyWith(state: const Value(SyncState.conflict)));
        return true;
      }
      if (status >= 400 && status < 500 && status != 408 && status != 429) {
        await row.write(failed.copyWith(state: const Value(SyncState.rejected)));
        return true;
      }
      // no network, a timeout, the server busy or failing: try again later
      await row.write(failed.copyWith(nextAttemptAt: Value(_now().add(backoff(item.attempts + 1)))));
      return !failure.isNetwork;
    }
  }

  Selectable<int> _pendingCount(String userId) {
    final count = _db.syncItems.id.count();
    return (_db.selectOnly(_db.syncItems)
          ..addColumns([count])
          ..where(_db.syncItems.userId.equals(userId) & _db.syncItems.state.equalsValue(SyncState.pending)))
        .map((row) => row.read(count)!);
  }
}
