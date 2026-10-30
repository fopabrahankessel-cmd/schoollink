import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_failure.dart';
import '../../core/cache/response_cache.dart';
import '../../core/providers.dart';
import '../../core/sync/sync_providers.dart';
import '../auth/auth_controller.dart';

/// A time of day as the API writes it (`07:30:00`), compared in minutes.
class DayTime implements Comparable<DayTime> {
  const DayTime(this.hour, this.minute);

  factory DayTime.parse(String text) {
    final parts = text.split(':');
    return DayTime(int.parse(parts[0]), int.parse(parts[1]));
  }

  factory DayTime.of(DateTime time) => DayTime(time.hour, time.minute);

  final int hour;
  final int minute;

  int get minutes => hour * 60 + minute;

  @override
  int compareTo(DayTime other) => minutes.compareTo(other.minutes);

  @override
  String toString() => '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}';
}

/// UC-T3: one session the teacher is expected to record (D1), and whether it is recorded yet,
/// by anyone (a colleague standing in counts).
class ExpectedSession {
  const ExpectedSession({
    required this.classId,
    required this.className,
    required this.periodId,
    required this.periodNumber,
    required this.startTime,
    required this.endTime,
    required this.subject,
    required this.recorded,
    required this.sessionId,
    required this.absentCount,
  });

  factory ExpectedSession.fromJson(Map<String, dynamic> json) => ExpectedSession(
    classId: json['classId'] as String,
    className: json['className'] as String,
    periodId: json['periodId'] as String,
    periodNumber: json['periodNumber'] as int,
    startTime: DayTime.parse(json['startTime'] as String),
    endTime: DayTime.parse(json['endTime'] as String),
    subject: json['subject'] as String?,
    recorded: json['recorded'] as bool,
    sessionId: json['sessionId'] as String?,
    absentCount: json['absentCount'] as int?,
  );

  final String classId;
  final String className;
  final String periodId;
  final int periodNumber;
  final DayTime startTime;
  final DayTime endTime;
  final String? subject;
  final bool recorded;
  final String? sessionId;

  /// Null until recorded; cancelled absences are not counted (BR-15).
  final int? absentCount;

  /// Under way at [time]; the phone works it out, so a list read hours ago stays right offline.
  bool isCurrentAt(DayTime time) => time.compareTo(startTime) >= 0 && time.compareTo(endTime) < 0;
}

/// The teacher's day. [fetchedAt] is set when it comes from the phone's cache (no network).
class TeacherDay {
  const TeacherDay({required this.date, required this.schoolDay, required this.sessions, this.fetchedAt});

  factory TeacherDay.fromJson(Map<String, dynamic> json, {DateTime? fetchedAt}) => TeacherDay(
    date: DateTime.parse(json['date'] as String),
    schoolDay: json['schoolDay'] as bool,
    sessions: [
      for (final session in (json['sessions'] as List).cast<Map<String, dynamic>>()) ExpectedSession.fromJson(session),
    ],
    fetchedAt: fetchedAt,
  );

  final DateTime date;
  final bool schoolDay;
  final List<ExpectedSession> sessions;
  final DateTime? fetchedAt;

  bool get fromCache => fetchedAt != null;

  /// UC-T1 step 1: the period under way at [time] first (none when [time] is null: not today),
  /// then the others by period.
  List<ExpectedSession> orderedAt(DayTime? time) {
    bool current(ExpectedSession s) => time != null && s.isCurrentAt(time);
    final under = [
      for (final s in sessions)
        if (current(s)) s,
    ];
    final others = [
      for (final s in sessions)
        if (!current(s)) s,
    ]..sort((a, b) => a.startTime.compareTo(b.startTime));
    return [...under, ...others];
  }
}

/// `GET /me/sessions`, kept on the phone so the list opens with no network (NFR-3).
class TeacherSessionsRepository {
  TeacherSessionsRepository(this._dio, this._cache);

  final Dio _dio;
  final ResponseCache _cache;

  static String _key(DateTime day) =>
      'sessions:${day.year}-${day.month.toString().padLeft(2, '0')}-${day.day.toString().padLeft(2, '0')}';

  /// Today's sessions from the server, or the copy saved on the phone when the server cannot be
  /// reached. Throws an [ApiFailure] when there is neither.
  Future<TeacherDay> today(String userId, DateTime now) async {
    try {
      final response = await _dio.get<Map<String, dynamic>>('/me/sessions');
      final day = TeacherDay.fromJson(response.data!);
      await _cache.put(userId, _key(day.date), response.data);
      return day;
    } on DioException catch (e) {
      final failure = ApiFailure.from(e);
      if (!failure.isNetwork) {
        throw failure;
      }
      final cached = await _cache.get(userId, _key(now));
      if (cached == null) {
        throw failure;
      }
      return TeacherDay.fromJson(cached.value! as Map<String, dynamic>, fetchedAt: cached.fetchedAt);
    }
  }
}

final responseCacheProvider = Provider<ResponseCache>((ref) => ResponseCache(ref.watch(appDatabaseProvider)));

final teacherSessionsRepositoryProvider = Provider<TeacherSessionsRepository>(
  (ref) => TeacherSessionsRepository(ref.watch(dioProvider), ref.watch(responseCacheProvider)),
);

/// The signed-in teacher's day; refreshed by pulling the list down.
final teacherDayProvider = FutureProvider.autoDispose<TeacherDay>((ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  if (userId == null) {
    throw StateError('signed out');
  }
  return ref.watch(teacherSessionsRepositoryProvider).today(userId, ref.read(clockProvider)());
});
