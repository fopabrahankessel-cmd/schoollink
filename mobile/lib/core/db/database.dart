import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'database.g.dart';

/// Where a queued record stands.
enum SyncState {
  /// Not on the server yet: waiting for the network or for its next attempt.
  pending,

  /// Accepted by the server (201, or 200 for a replay of the same UUID).
  synced,

  /// 409: the server holds a different record for the same thing (another session for this class
  /// and period, the student's lateness already recorded today). Never retried.
  conflict,

  /// Refused for good (403, 404, 400): no longer assigned, outside the scope, invalid. Never retried.
  rejected,
}

/// BR-6, NFR-3: records saved on the device, to be sent to an idempotent `PUT {path}` whose last
/// segment is the device UUID ([id]). A record belongs to the user who made it: only their
/// token can send it.
@DataClassName('SyncItem')
class SyncItems extends Table {
  TextColumn get id => text()();

  TextColumn get userId => text()();

  /// What it is (`session`, `lateness`), for the screens that show it.
  TextColumn get kind => text()();

  /// API path after `/api/v1`, e.g. `/sessions/<id>`.
  TextColumn get path => text()();

  /// JSON request body.
  TextColumn get body => text()();

  TextColumn get state => textEnum<SyncState>()();

  IntColumn get attempts => integer().withDefault(const Constant(0))();

  DateTimeColumn get nextAttemptAt => dateTime()();

  DateTimeColumn get createdAt => dateTime()();

  DateTimeColumn get syncedAt => dateTime().nullable()();

  /// The server's answer to the last attempt that reached it, and its error, if any (SPEC §9).
  IntColumn get httpStatus => integer().nullable()();

  TextColumn get errorCode => text().nullable()();

  TextColumn get errorMessage => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

/// NFR-3: the last answer of a read endpoint, kept so the screen can be shown with no network.
@DataClassName('CachedResponse')
class CachedResponses extends Table {
  TextColumn get userId => text()();

  /// What was read, e.g. `sessions:2026-11-02`.
  TextColumn get key => text()();

  TextColumn get json => text()();

  DateTimeColumn get fetchedAt => dateTime()();

  @override
  Set<Column> get primaryKey => {userId, key};
}

@DriftDatabase(tables: [SyncItems, CachedResponses])
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.executor);

  /// The app's database file on the phone.
  AppDatabase.onDevice() : super(driftDatabase(name: 'schoollink'));

  /// 1: sync queue (task 2.12). 2: response cache (task 2.13).
  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onUpgrade: (m, from, to) async {
      if (from < 2) {
        await m.createTable(cachedResponses);
      }
    },
  );
}
