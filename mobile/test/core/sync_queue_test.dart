import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart' hide isNull;
import 'package:drift/native.dart';
import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/sync/sync_queue.dart';
import 'package:schoollink/core/sync/sync_scheduler.dart';

import '../support/fake_server.dart';

/// BR-6, NFR-3: records made offline reach the server once, in order, whatever the network does.
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  late FakeServer server;
  late AppDatabase db;
  late SyncQueue queue;
  late DateTime now;

  SyncQueue queueOn(AppDatabase database) {
    final dio = Dio(BaseOptions(baseUrl: 'http://test/api/v1'))..httpClientAdapter = server;
    return SyncQueue(database, dio, now: () => now);
  }

  setUp(() {
    server = FakeServer();
    now = DateTime(2026, 11, 2, 7, 40); // drift reads dates back in local time
    db = AppDatabase(NativeDatabase.memory());
    queue = queueOn(db);
  });

  tearDown(() async {
    queue.dispose();
    await db.close();
  });

  Future<void> enqueue(String id, {String userId = 'teacher-1', Map<String, dynamic>? body}) =>
      queue.enqueue(id: id, userId: userId, kind: 'session', path: '/sessions/$id', body: body ?? {'id': id});

  Future<SyncItem> item(String id) => (db.select(db.syncItems)..where((i) => i.id.equals(id))).getSingle();

  void acceptSessions() => server.on('PUT /sessions/s-1', (_) => const Reply(201, {}));

  test('a record syncs exactly once, even when runs are asked for together', () async {
    acceptSessions();
    await enqueue(
      's-1',
      body: {
        'classId': 'c-1',
        'absentees': ['st-1'],
      },
    );

    await Future.wait([queue.drain('teacher-1'), queue.drain('teacher-1'), queue.drain('teacher-1')]);
    await queue.drain('teacher-1');

    final sent = server.sent('PUT /sessions/s-1').single;
    expect(sent.body, {
      'classId': 'c-1',
      'absentees': ['st-1'],
    });
    final synced = await item('s-1');
    expect(synced.state, SyncState.synced);
    expect(synced.syncedAt, now);
    expect(synced.httpStatus, 201);
  });

  test('queuing the same UUID again keeps the first record (BR-4)', () async {
    await enqueue('s-1', body: {'v': 1});
    await enqueue('s-1', body: {'v': 2});

    expect(await db.select(db.syncItems).get(), hasLength(1));
    expect((await item('s-1')).body, '{"v":1}');
  });

  test('the queue survives an app restart', () async {
    final dir = await Directory.systemTemp.createTemp('schoollink');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/schoollink.sqlite');
    acceptSessions();

    final before = AppDatabase(NativeDatabase(file));
    await queueOn(before).enqueue(id: 's-1', userId: 'teacher-1', kind: 'session', path: '/sessions/s-1', body: {});
    await before.close();
    expect(server.requests, isEmpty);

    final after = AppDatabase(NativeDatabase(file));
    addTearDown(after.close);
    final restarted = queueOn(after);
    expect(await restarted.pendingCount('teacher-1'), 1);
    await restarted.drain('teacher-1');

    expect(server.sent('PUT /sessions/s-1'), hasLength(1));
    expect(await restarted.pendingCount('teacher-1'), 0);
  });

  test('a 409 marks a conflict, kept with the server\'s message and never retried', () async {
    server.on(
      'PUT /sessions/s-1',
      (_) => Reply.error(409, 'attendance.session_exists', 'Cette séance est déjà enregistrée.'),
    );
    await enqueue('s-1');

    await queue.drain('teacher-1');
    await queue.drain('teacher-1', now: true);

    final conflict = await item('s-1');
    expect(conflict.state, SyncState.conflict);
    expect(conflict.httpStatus, 409);
    expect(conflict.errorCode, 'attendance.session_exists');
    expect(conflict.errorMessage, 'Cette séance est déjà enregistrée.');
    expect(server.requests, hasLength(1));
    expect(await queue.pendingCount('teacher-1'), 0);
  });

  test('a 403 or a 404 is refused for good, and the run goes on', () async {
    server
      ..on('PUT /sessions/s-1', (_) => Reply.error(403, 'auth.forbidden', 'Accès refusé.'))
      ..on('PUT /sessions/s-2', (_) => Reply.error(404, 'not_found', 'Introuvable.'))
      ..on('PUT /sessions/s-3', (_) => const Reply(201, {}));
    await enqueue('s-1');
    now = now.add(const Duration(seconds: 1));
    await enqueue('s-2');
    now = now.add(const Duration(seconds: 1));
    await enqueue('s-3');

    await queue.drain('teacher-1');
    await queue.drain('teacher-1', now: true);

    expect((await item('s-1')).state, SyncState.rejected);
    expect((await item('s-1')).errorCode, 'auth.forbidden');
    expect((await item('s-2')).state, SyncState.rejected);
    expect((await item('s-3')).state, SyncState.synced);
    expect(server.requests, hasLength(3));
  });

  test('with no network the record waits out a backoff, and the run stops', () async {
    server
      ..on('PUT /sessions/s-1', (_) => Reply.unreachable)
      ..on('PUT /sessions/s-2', (_) => const Reply(201, {}));
    await enqueue('s-1');
    now = now.add(const Duration(seconds: 1));
    await enqueue('s-2');

    await queue.drain('teacher-1');

    final waiting = await item('s-1');
    expect(waiting.state, SyncState.pending);
    expect(waiting.attempts, 1);
    expect(waiting.nextAttemptAt, now.add(const Duration(seconds: 5)));
    expect(server.sent('PUT /sessions/s-2'), isEmpty, reason: 'no point trying the next one offline');

    server.on('PUT /sessions/s-1', (_) => const Reply(201, {}));
    await queue.drain('teacher-1');
    expect(server.sent('PUT /sessions/s-1'), hasLength(1), reason: 'still in its backoff');
    expect(server.sent('PUT /sessions/s-2'), hasLength(1));

    now = now.add(const Duration(seconds: 5));
    await queue.drain('teacher-1');
    expect((await item('s-1')).state, SyncState.synced);
    expect((await item('s-1')).attempts, 2);
  });

  test('a server error is retried later, with a longer wait each time', () async {
    server.on('PUT /sessions/s-1', (_) => const Reply(503));
    await enqueue('s-1');

    await queue.drain('teacher-1');
    expect((await item('s-1')).nextAttemptAt, now.add(const Duration(seconds: 5)));
    await queue.drain('teacher-1', now: true);
    expect((await item('s-1')).nextAttemptAt, now.add(const Duration(seconds: 10)));
    expect((await item('s-1')).state, SyncState.pending);
    expect((await item('s-1')).httpStatus, 503);
  });

  test('the backoff doubles from 5 seconds up to 15 minutes', () {
    expect(
      [for (var n = 1; n <= 10; n++) SyncQueue.backoff(n).inSeconds],
      [5, 10, 20, 40, 80, 160, 320, 640, 900, 900],
    );
  });

  test('when the network comes back, records waiting out a backoff go at once', () async {
    server.on('PUT /sessions/s-1', (_) => Reply.unreachable);
    await enqueue('s-1');
    await queue.drain('teacher-1');
    server.on('PUT /sessions/s-1', (_) => const Reply(201, {}));

    await queue.drain('teacher-1', now: true);

    expect((await item('s-1')).state, SyncState.synced);
  });

  test('a session that ended leaves the records waiting, untouched, for the next sign-in', () async {
    server.on('PUT /sessions/s-1', (_) => Reply.error(401, 'auth.unauthorized', 'x'));
    await enqueue('s-1');

    await queue.drain('teacher-1');

    final waiting = await item('s-1');
    expect(waiting.state, SyncState.pending);
    expect(waiting.attempts, 0);
    expect(waiting.nextAttemptAt, now);
  });

  test('a temporary password to replace is not a refusal (D18)', () async {
    server.on(
      'PUT /sessions/s-1',
      (_) => Reply.error(403, 'auth.password_change_required', 'Choisissez un nouveau mot de passe.'),
    );
    await enqueue('s-1');

    await queue.drain('teacher-1');

    expect((await item('s-1')).state, SyncState.pending);
  });

  test("only the signed-in user's records are sent, oldest first", () async {
    server
      ..on('PUT /sessions/s-1', (_) => const Reply(201, {}))
      ..on('PUT /sessions/s-2', (_) => const Reply(201, {}));
    await enqueue('s-2');
    now = now.add(const Duration(seconds: 1));
    await enqueue('s-1');
    await enqueue('s-other', userId: 'teacher-2');

    await queue.drain('teacher-1');

    expect([for (final r in server.requests) r.path], ['/sessions/s-2', '/sessions/s-1']);
    expect(await queue.pendingCount('teacher-2'), 1);
  });

  test('the count of records waiting follows the queue', () async {
    acceptSessions();
    final counts = <int>[];
    final subscription = queue.watchPending('teacher-1').listen(counts.add);
    addTearDown(subscription.cancel);
    await pumpEventQueue();

    await enqueue('s-1');
    await pumpEventQueue();
    await queue.drain('teacher-1');
    await pumpEventQueue();

    expect(counts, [0, 1, 0]);
  });

  test('records synced over 30 days ago are deleted, the others kept', () async {
    acceptSessions();
    server.on('PUT /sessions/s-2', (_) => Reply.unreachable);
    await enqueue('s-1');
    await enqueue('s-2');
    await queue.drain('teacher-1');

    now = now.add(const Duration(days: 31));
    await queue.prune();

    expect([for (final i in await db.select(db.syncItems).get()) i.id], ['s-2']);
  });

  group('scheduler', () {
    test('sends at start, on each new record, when the network returns and after a backoff', () {
      fakeAsync((async) {
        final online = StreamController<bool>.broadcast();
        final scheduler = SyncScheduler(queue: queue, userId: 'teacher-1', online: online.stream, now: () => now);
        var replies = <Reply>[Reply.unreachable, const Reply(201, {})];
        server.on('PUT /sessions/s-1', (_) => replies.removeAt(0));
        server.on('PUT /sessions/s-2', (_) => const Reply(201, {}));

        unawaited(enqueue('s-1'));
        async.elapse(Duration.zero);
        scheduler.start();
        async.elapse(Duration.zero);
        expect(server.sent('PUT /sessions/s-1'), hasLength(1), reason: 'sent at start, no network');

        // the backoff timer (5 s) sends it again
        now = now.add(const Duration(seconds: 5));
        async.elapse(const Duration(seconds: 5));
        expect(server.sent('PUT /sessions/s-1'), hasLength(2));

        unawaited(enqueue('s-2'));
        async.elapse(Duration.zero);
        expect(server.sent('PUT /sessions/s-2'), hasLength(1), reason: 'sent as soon as queued');

        replies = [const Reply(201, {})];
        online.add(true);
        async.elapse(Duration.zero);
        expect(server.requests, hasLength(3), reason: 'nothing left to send');

        scheduler.dispose();
        unawaited(online.close());
      });
    });

    test('the network coming back skips the backoff', () {
      fakeAsync((async) {
        final online = StreamController<bool>.broadcast();
        final scheduler = SyncScheduler(queue: queue, userId: 'teacher-1', online: online.stream, now: () => now);
        server.on('PUT /sessions/s-1', (_) => Reply.unreachable);
        unawaited(enqueue('s-1'));
        async.elapse(Duration.zero);
        scheduler.start();
        async.elapse(Duration.zero);

        server.on('PUT /sessions/s-1', (_) => const Reply(201, {}));
        online.add(false);
        async.elapse(Duration.zero);
        expect(server.requests, hasLength(1), reason: 'losing the network sends nothing');
        online.add(true);
        async.elapse(Duration.zero);

        expect(server.requests, hasLength(2));
        scheduler.dispose();
        unawaited(online.close());
      });
    });
  });
}
