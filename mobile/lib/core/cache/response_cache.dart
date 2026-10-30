import 'dart:convert';

import 'package:drift/drift.dart';

import '../db/database.dart';

/// A cached answer and when it came from the server.
class Cached<T> {
  const Cached(this.value, this.fetchedAt);

  final T value;
  final DateTime fetchedAt;
}

/// NFR-3: the last answer of each read endpoint, per user, so their screens open with no network.
/// Answers older than [keep] are deleted when a new one is saved.
class ResponseCache {
  ResponseCache(this._db, {DateTime Function()? now}) : _now = now ?? DateTime.now;

  static const keep = Duration(days: 7);

  final AppDatabase _db;
  final DateTime Function() _now;

  Future<void> put(String userId, String key, Object? json) async {
    final now = _now();
    await _db.transaction(() async {
      await _db
          .into(_db.cachedResponses)
          .insertOnConflictUpdate(
            CachedResponsesCompanion.insert(userId: userId, key: key, json: jsonEncode(json), fetchedAt: now),
          );
      await (_db.delete(_db.cachedResponses)..where((c) => c.fetchedAt.isSmallerThanValue(now.subtract(keep)))).go();
    });
  }

  Future<Cached<Object?>?> get(String userId, String key) async {
    final row = await (_db.select(
      _db.cachedResponses,
    )..where((c) => c.userId.equals(userId) & c.key.equals(key))).getSingleOrNull();
    return row == null ? null : Cached(jsonDecode(row.json), row.fetchedAt);
  }
}
