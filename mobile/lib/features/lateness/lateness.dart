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
import '../teacher/attendance.dart' show isoDate;
import '../teacher/teacher_sessions.dart' show DayTime, responseCacheProvider;

/// A student the assistant DM may record at the gate (UC-AD1; NFR-9: nothing more).
class GateStudent {
  const GateStudent({
    required this.id,
    required this.matricule,
    required this.firstName,
    required this.lastName,
    required this.classId,
    required this.className,
  });

  factory GateStudent.fromJson(Map<String, dynamic> json) => GateStudent(
    id: json['id'] as String,
    matricule: json['matricule'] as String,
    firstName: json['firstName'] as String,
    lastName: json['lastName'] as String,
    classId: json['classId'] as String,
    className: json['className'] as String? ?? '',
  );

  final String id;
  final String matricule;
  final String firstName;
  final String lastName;
  final String classId;
  final String className;

  String get fullName => '$lastName $firstName';

  /// UC-AD1: part of a name, in either order, or the start of the matricule; accents and case
  /// ignored, as staff type fast at the gate.
  bool matches(String query) {
    final q = fold(query.trim());
    if (q.isEmpty) {
      return false;
    }
    return fold('$lastName $firstName').contains(q) ||
        fold('$firstName $lastName').contains(q) ||
        fold(matricule).startsWith(q);
  }
}

/// Lower case with the accents of French names removed.
String fold(String text) {
  const from = 'àâäáãåçéèêëíìîïñóòôöõúùûüýÿœæ';
  const to = 'aaaaaaceeeeiiiinooooouuuuyyoa';
  final buffer = StringBuffer();
  for (final char in text.toLowerCase().split('')) {
    final i = from.indexOf(char);
    buffer.write(i < 0 ? char : to[i]);
  }
  return buffer.toString();
}

/// One lateness of the day as the server has it (UC-AD2).
class LatenessEntry {
  const LatenessEntry({
    required this.id,
    required this.studentId,
    required this.firstName,
    required this.lastName,
    required this.className,
    required this.arrivalTime,
    required this.justified,
  });

  factory LatenessEntry.fromJson(Map<String, dynamic> json) => LatenessEntry(
    id: json['id'] as String,
    studentId: json['studentId'] as String,
    firstName: json['firstName'] as String,
    lastName: json['lastName'] as String,
    className: json['className'] as String? ?? '',
    arrivalTime: DayTime.parse(json['arrivalTime'] as String),
    justified: json['justified'] as bool? ?? false,
  );

  final String id;
  final String studentId;
  final String firstName;
  final String lastName;
  final String className;
  final DayTime arrivalTime;
  final bool justified;

  String get fullName => '$lastName $firstName';
}

/// `GET /lateness`: the day's lateness in the user's scope, and the school's start time (BR-7).
/// [fetchedAt] is set when it comes from the phone's copy (no network).
class LatenessDay {
  const LatenessDay({required this.date, required this.startTime, required this.entries, this.fetchedAt});

  factory LatenessDay.fromJson(Map<String, dynamic> json, {DateTime? fetchedAt}) => LatenessDay(
    date: DateTime.parse(json['date'] as String),
    startTime: DayTime.parse(json['startTime'] as String),
    entries: [for (final e in (json['lateness'] as List).cast<Map<String, dynamic>>()) LatenessEntry.fromJson(e)],
    fetchedAt: fetchedAt,
  );

  final DateTime date;
  final DayTime startTime;
  final List<LatenessEntry> entries;
  final DateTime? fetchedAt;
}

/// The gate's data, kept on the phone so the assistant DM can search and record with no network
/// (NFR-3): every student of the scope, and today's list.
class LatenessRepository {
  LatenessRepository(this._dio, this._cache);

  static const _studentsKey = 'gate-students';

  final Dio _dio;
  final ResponseCache _cache;

  static String _dayKey(DateTime day) => 'lateness:${isoDate(day)}';

  /// The server's answer, or the phone's copy and when it was read when the server cannot be
  /// reached. Throws an [ApiFailure] when there is neither.
  Future<(Object?, DateTime?)> _read(String userId, String key, Future<Object?> Function() fetch) async {
    try {
      final json = await fetch();
      await _cache.put(userId, key, json);
      return (json, null);
    } on DioException catch (e) {
      final failure = ApiFailure.from(e);
      final cached = failure.isNetwork ? await _cache.get(userId, key) : null;
      if (cached == null) {
        throw failure;
      }
      return (cached.value, cached.fetchedAt);
    }
  }

  /// Without a query the server gives the whole scope, which is what the phone searches offline.
  Future<List<GateStudent>> students(String userId) async {
    final (json, _) = await _read(
      userId,
      _studentsKey,
      () async => (await _dio.get<List<dynamic>>('/lateness/students')).data,
    );
    return [for (final s in (json! as List).cast<Map<String, dynamic>>()) GateStudent.fromJson(s)];
  }

  Future<LatenessDay> day(String userId, DateTime today) async {
    final (json, fetchedAt) = await _read(
      userId,
      _dayKey(today),
      () async => (await _dio.get<Map<String, dynamic>>('/lateness', queryParameters: {'date': isoDate(today)})).data,
    );
    return LatenessDay.fromJson(json! as Map<String, dynamic>, fetchedAt: fetchedAt);
  }
}

/// A lateness recorded on this phone (UC-AD1), sent by the sync queue (NFR-3). [state] says
/// whether the server has it.
class LocalLateness {
  const LocalLateness({
    required this.id,
    required this.studentId,
    required this.date,
    required this.arrivalTime,
    required this.state,
    required this.errorMessage,
  });

  factory LocalLateness.of(SyncItem item) {
    final body = jsonDecode(item.body) as Map<String, dynamic>;
    return LocalLateness(
      id: item.id,
      studentId: body['studentId'] as String,
      date: body['date'] as String,
      arrivalTime: DayTime.parse(body['arrivalTime'] as String),
      state: item.state,
      errorMessage: item.errorMessage,
    );
  }

  static const kind = 'lateness';

  final String id;
  final String studentId;

  /// ISO date, as sent to the server.
  final String date;
  final DayTime arrivalTime;
  final SyncState state;
  final String? errorMessage;

  /// Refused by the server: it does not count as the student's lateness of the day.
  bool get refused => state == SyncState.conflict || state == SyncState.rejected;
}

/// UC-AD1: the lateness gets its UUID on the phone and goes to the sync queue; the server stores
/// it once whatever the retries (`PUT /lateness/{id}`). It is independent of attendance (BR-8, BR-9).
class LatenessRecorder {
  LatenessRecorder(this._queue, this._now, [Uuid? uuid]) : _uuid = uuid ?? const Uuid();

  final SyncQueue _queue;
  final DateTime Function() _now;
  final Uuid _uuid;

  Future<String> record({required String userId, required GateStudent student, required DayTime arrival}) async {
    final id = _uuid.v4();
    final now = _now();
    await _queue.enqueue(
      id: id,
      userId: userId,
      kind: LocalLateness.kind,
      path: '/lateness/$id',
      body: {
        'studentId': student.id,
        'date': isoDate(now),
        'arrivalTime': arrival.toString(),
        'recordedAt': now.toUtc().toIso8601String(),
      },
    );
    return id;
  }
}

/// One row of the day's list: from the server, or recorded here and not on the server yet.
class GateRow {
  const GateRow({
    required this.id,
    required this.studentId,
    required this.firstName,
    required this.lastName,
    required this.className,
    required this.arrivalTime,
    this.justified = false,
    this.local,
  });

  final String id;
  final String studentId;
  final String firstName;
  final String lastName;
  final String className;
  final DayTime arrivalTime;
  final bool justified;

  /// The copy recorded on this phone, if any: its sync state.
  final LocalLateness? local;

  String get fullName => '$lastName $firstName';
}

/// UC-AD2: the server's list with this phone's records merged in by id (the id is the device's,
/// so a record already sent is not shown twice), latest arrival first.
List<GateRow> mergeDay({
  required List<LatenessEntry> server,
  required List<LocalLateness> local,
  required Map<String, GateStudent> students,
}) {
  final mine = {for (final l in local) l.id: l};
  final rows = [
    for (final e in server)
      GateRow(
        id: e.id,
        studentId: e.studentId,
        firstName: e.firstName,
        lastName: e.lastName,
        className: e.className,
        arrivalTime: e.arrivalTime,
        justified: e.justified,
        local: mine[e.id],
      ),
    for (final l in local)
      if (!server.any((e) => e.id == l.id))
        GateRow(
          id: l.id,
          studentId: l.studentId,
          firstName: students[l.studentId]?.firstName ?? '',
          lastName: students[l.studentId]?.lastName ?? l.studentId,
          className: students[l.studentId]?.className ?? '',
          arrivalTime: l.arrivalTime,
          local: l,
        ),
  ];
  return rows..sort((a, b) => b.arrivalTime.compareTo(a.arrivalTime));
}

final latenessRepositoryProvider = Provider<LatenessRepository>(
  (ref) => LatenessRepository(ref.watch(dioProvider), ref.watch(responseCacheProvider)),
);

String _signedIn(Ref ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  if (userId == null) {
    throw StateError('signed out');
  }
  return userId;
}

/// Every student of the assistant DM's scope, for the search at the gate.
final gateStudentsProvider = FutureProvider.autoDispose<List<GateStudent>>(
  (ref) => ref.watch(latenessRepositoryProvider).students(_signedIn(ref)),
);

/// Today's lateness in the user's scope (assistant DM and DM).
final latenessDayProvider = FutureProvider.autoDispose<LatenessDay>(
  (ref) => ref.watch(latenessRepositoryProvider).day(_signedIn(ref), ref.read(clockProvider)()),
);

/// The lateness recorded on this phone by the signed-in user. Kept for the whole sign-in.
final localLatenessProvider = StreamProvider<List<LocalLateness>>((ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  if (userId == null) {
    return Stream.value(const []);
  }
  return ref
      .watch(syncQueueProvider)
      .watchKind(userId, LocalLateness.kind)
      .map((items) => [for (final item in items) LocalLateness.of(item)]);
});

final latenessRecorderProvider = Provider<LatenessRecorder>(
  (ref) => LatenessRecorder(ref.watch(syncQueueProvider), ref.watch(clockProvider)),
);
