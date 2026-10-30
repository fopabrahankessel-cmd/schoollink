import 'dart:io';

import 'package:dio/dio.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/cache/response_cache.dart';
import 'package:schoollink/core/db/database.dart';
import 'package:schoollink/core/sync/sync_queue.dart';

/// A phone with the version 1 database (task 2.12) keeps its waiting records when the app updates.
void main() {
  setUpAll(() => driftRuntimeOptions.dontWarnAboutMultipleDatabases = true);

  test('version 1 to 2 adds the response cache and keeps the sync queue', () async {
    final dir = await Directory.systemTemp.createTemp('schoollink');
    addTearDown(() => dir.delete(recursive: true));
    final file = File('${dir.path}/schoollink.sqlite');

    // a version 1 database: the queue only, with a record waiting
    final v1 = AppDatabase(NativeDatabase(file));
    await SyncQueue(v1, Dio()).enqueue(id: 's-1', userId: 'u', kind: 'session', path: '/sessions/s-1', body: {});
    await v1.customStatement('DROP TABLE cached_responses');
    await v1.customStatement('PRAGMA user_version = 1');
    await v1.close();

    final v2 = AppDatabase(NativeDatabase(file));
    addTearDown(v2.close);
    final cache = ResponseCache(v2);
    await cache.put('u', 'sessions:2026-11-02', {'ok': true});

    expect((await cache.get('u', 'sessions:2026-11-02'))!.value, {'ok': true});
    expect([for (final i in await v2.select(v2.syncItems).get()) i.id], ['s-1']);
    expect(await v2.customSelect('PRAGMA user_version').map((r) => r.read<int>('user_version')).getSingle(), 2);
  });
}
