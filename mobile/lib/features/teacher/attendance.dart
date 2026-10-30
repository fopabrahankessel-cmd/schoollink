import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';

import '../../core/api/api_failure.dart';
import '../../core/cache/response_cache.dart';
import '../../core/db/database.dart';
import '../../core/providers.dart';
import '../../core/sync/sync_providers.dart';
import '../../core/sync/sync_queue.dart';
import '../auth/auth_controller.dart';
import 'teacher_sessions.dart';

/// A student as the teacher sees them on the roll (NFR-9: nothing more).
class RosterStudent {
  const RosterStudent({required this.id, required this.matricule, required this.firstName, required this.lastName});

  factory RosterStudent.fromJson(Map<String, dynamic> json) => RosterStudent(
    id: json['id'] as String,
    matricule: json['matricule'] as String,
    firstName: json['firstName'] as String,
    lastName: json['lastName'] as String,
  );

  final String id;
  final String matricule;
  final String firstName;
  final String lastName;

  String get fullName => '$lastName $firstName';
}

/// The students of each class the teacher teaches, by class id.
typedef Rosters = Map<String, List<RosterStudent>>;

/// `GET /me/classes`, kept on the phone so a session can be recorded with no network (NFR-3).
class RostersRepository {
  RostersRepository(this._dio, this._cache);

  static const _key = 'classes';

  final Dio _dio;
  final ResponseCache _cache;

  Future<Rosters> rosters(String userId) async {
    Object? json;
    try {
      json = (await _dio.get<List<dynamic>>('/me/classes')).data;
      await _cache.put(userId, _key, json);
    } on DioException catch (e) {
      final failure = ApiFailure.from(e);
      final cached = failure.isNetwork ? await _cache.get(userId, _key) : null;
      if (cached == null) {
        throw failure;
      }
      json = cached.value;
    }
    return {
      for (final c in (json! as List).cast<Map<String, dynamic>>())
        c['classId'] as String: [
          for (final s in (c['students'] as List).cast<Map<String, dynamic>>()) RosterStudent.fromJson(s),
        ],
    };
  }
}

/// A session confirmed on this phone (UC-T1 step 6): saved and locked at once (BR-4), sent by
/// the sync queue (BR-6). [state] says whether the server has it.
class LocalSession {
  const LocalSession({
    required this.id,
    required this.classId,
    required this.periodId,
    required this.date,
    required this.confirmedAt,
    required this.absentStudentIds,
    required this.state,
    required this.errorMessage,
  });

  factory LocalSession.of(SyncItem item) {
    final body = jsonDecode(item.body) as Map<String, dynamic>;
    return LocalSession(
      id: item.id,
      classId: body['classId'] as String,
      periodId: body['periodId'] as String,
      date: body['date'] as String,
      confirmedAt: DateTime.parse(body['confirmedAt'] as String).toLocal(),
      absentStudentIds: (body['absentStudentIds'] as List).cast<String>(),
      state: item.state,
      errorMessage: item.errorMessage,
    );
  }

  static const kind = 'session';

  final String id;
  final String classId;
  final String periodId;

  /// ISO date, as sent to the server.
  final String date;
  final DateTime confirmedAt;
  final List<String> absentStudentIds;
  final SyncState state;
  final String? errorMessage;

  bool isFor(ExpectedSession session, DateTime day) =>
      classId == session.classId && periodId == session.periodId && date == isoDate(day);
}

String isoDate(DateTime day) =>
    '${day.year}-${day.month.toString().padLeft(2, '0')}-${day.day.toString().padLeft(2, '0')}';

/// UC-T1 step 6: the teacher confirmed twice (BR-3). The session gets its UUID on the phone
/// (BR-6) and goes to the sync queue, which is the saved, locked copy: it is never changed (BR-4).
/// Every student not listed is present (BR-2); an empty list is a session too (BR-5).
class AttendanceRecorder {
  AttendanceRecorder(this._queue, this._now, [Uuid? uuid]) : _uuid = uuid ?? const Uuid();

  final SyncQueue _queue;
  final DateTime Function() _now;
  final Uuid _uuid;

  Future<String> record({
    required String userId,
    required ExpectedSession session,
    required DateTime date,
    required Set<String> absentStudentIds,
  }) async {
    final id = _uuid.v4();
    await _queue.enqueue(
      id: id,
      userId: userId,
      kind: LocalSession.kind,
      path: '/sessions/$id',
      body: {
        'classId': session.classId,
        'date': isoDate(date),
        'periodId': session.periodId,
        'confirmedAt': _now().toUtc().toIso8601String(),
        'absentStudentIds': absentStudentIds.toList()..sort(),
      },
    );
    return id;
  }
}

final rostersRepositoryProvider = Provider<RostersRepository>(
  (ref) => RostersRepository(ref.watch(dioProvider), ref.watch(responseCacheProvider)),
);

/// Read when the sessions list opens, so the rosters are on the phone before the network goes.
final rostersProvider = FutureProvider.autoDispose<Rosters>((ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  if (userId == null) {
    throw StateError('signed out');
  }
  return ref.watch(rostersRepositoryProvider).rosters(userId);
});

/// The sessions confirmed on this phone by the signed-in teacher. Kept for the whole sign-in: the
/// list and the roll both follow it.
final localSessionsProvider = StreamProvider<List<LocalSession>>((ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  if (userId == null) {
    return Stream.value(const []);
  }
  return ref
      .watch(syncQueueProvider)
      .watchKind(userId, LocalSession.kind)
      .map((items) => [for (final item in items) LocalSession.of(item)]);
});

final attendanceRecorderProvider = Provider<AttendanceRecorder>(
  (ref) => AttendanceRecorder(ref.watch(syncQueueProvider), ref.watch(clockProvider)),
);
