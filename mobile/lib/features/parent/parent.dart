import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_failure.dart';
import '../../core/cache/response_cache.dart';
import '../../core/providers.dart';
import '../auth/auth_controller.dart';
import '../teacher/teacher_sessions.dart' show DayTime, responseCacheProvider;

/// UC-P3: one of the parent's children, from the account's guardian links (NFR-8).
class Child {
  const Child({
    required this.id,
    required this.firstName,
    required this.lastName,
    required this.classId,
    required this.className,
  });

  factory Child.fromJson(Map<String, dynamic> json) => Child(
    id: json['id'] as String,
    firstName: json['firstName'] as String,
    lastName: json['lastName'] as String,
    classId: json['classId'] as String,
    className: json['className'] as String?,
  );

  final String id;
  final String firstName;
  final String lastName;
  final String classId;
  final String? className;
}

/// What a notification is about; it sets the icon and colour of the message.
enum EventType {
  absence,
  lateness,
  correction,
  summons,
  dmMessage,
  announcement,
  other;

  static EventType parse(String? text) => switch (text) {
    'ABSENCE' => absence,
    'LATENESS' => lateness,
    'CORRECTION' => correction,
    'SUMMONS' => summons,
    'DM_MESSAGE' => dmMessage,
    'ANNOUNCEMENT' => announcement,
    _ => other,
  };

  /// About a child's attendance: the message leads to the history (UC-P3).
  bool get aboutAttendance => this == absence || this == lateness || this == correction;
}

/// UC-P2: one message of the inbox, its text in the parent's language from the server.
class InboxEntry {
  const InboxEntry({
    required this.id,
    required this.type,
    required this.studentId,
    required this.title,
    required this.body,
    required this.createdAt,
    required this.openedAt,
  });

  factory InboxEntry.fromJson(Map<String, dynamic> json) => InboxEntry(
    id: json['id'] as String,
    type: EventType.parse(json['eventType'] as String?),
    studentId: json['studentId'] as String?,
    title: json['title'] as String,
    body: json['body'] as String,
    createdAt: DateTime.parse(json['createdAt'] as String).toLocal(),
    openedAt: switch (json['openedAt']) {
      final String at => DateTime.parse(at).toLocal(),
      _ => null,
    },
  );

  final String id;
  final EventType type;
  final String? studentId;
  final String title;
  final String body;
  final DateTime createdAt;

  /// When the parent first opened it (BR-24); null while unread.
  final DateTime? openedAt;
}

/// One absent period of a day; cancelled records are left out by the server (BR-15).
class PeriodAbsence {
  const PeriodAbsence({this.recordId, required this.periodNumber, required this.startTime, required this.justified});

  factory PeriodAbsence.fromJson(Map<String, dynamic> json) => PeriodAbsence(
    recordId: json['recordId'] as String?,
    periodNumber: json['periodNumber'] as int,
    startTime: DayTime.parse(json['startTime'] as String),
    justified: json['justified'] as bool,
  );

  /// The record the DM justifies or cancels (UC-D1, UC-D6).
  final String? recordId;
  final int periodNumber;
  final DayTime startTime;
  final bool justified;
}

/// The day's lateness at the gate, recorded apart from attendance (BR-8, BR-9).
class DayLateness {
  const DayLateness({this.recordId, required this.arrivalTime, required this.justified});

  factory DayLateness.fromJson(Map<String, dynamic> json) => DayLateness(
    recordId: json['recordId'] as String?,
    arrivalTime: DayTime.parse(json['arrivalTime'] as String),
    justified: json['justified'] as bool,
  );

  /// The record the DM justifies or cancels (UC-D1, UC-D6).
  final String? recordId;
  final DayTime arrivalTime;
  final bool justified;
}

/// A day with an absence or a lateness; days with neither are not listed.
class HistoryDay {
  const HistoryDay({required this.date, required this.absences, required this.lateness});

  factory HistoryDay.fromJson(Map<String, dynamic> json) => HistoryDay(
    date: DateTime.parse(json['date'] as String),
    absences: [for (final a in (json['absences'] as List).cast<Map<String, dynamic>>()) PeriodAbsence.fromJson(a)]
      ..sort((a, b) => a.periodNumber.compareTo(b.periodNumber)),
    lateness: switch (json['lateness']) {
      final Map<String, dynamic> late => DayLateness.fromJson(late),
      _ => null,
    },
  );

  final DateTime date;
  final List<PeriodAbsence> absences;
  final DayLateness? lateness;
}

/// UC-P3: a child's attendance and lateness over a period (by default the last 90 days), newest
/// day first. [fetchedAt] is set when it comes from the phone's copy (no network).
class ChildHistory {
  const ChildHistory({
    required this.firstName,
    required this.from,
    required this.to,
    required this.absentPeriods,
    required this.unjustifiedAbsentPeriods,
    required this.lateness,
    required this.unjustifiedLateness,
    required this.days,
    this.fetchedAt,
  });

  factory ChildHistory.fromJson(Map<String, dynamic> json, {DateTime? fetchedAt}) {
    final totals = json['totals'] as Map<String, dynamic>;
    return ChildHistory(
      firstName: json['firstName'] as String,
      from: DateTime.parse(json['from'] as String),
      to: DateTime.parse(json['to'] as String),
      absentPeriods: totals['absentPeriods'] as int,
      unjustifiedAbsentPeriods: totals['unjustifiedAbsentPeriods'] as int,
      lateness: totals['lateness'] as int,
      unjustifiedLateness: totals['unjustifiedLateness'] as int,
      days: [for (final d in (json['days'] as List).cast<Map<String, dynamic>>()) HistoryDay.fromJson(d)],
      fetchedAt: fetchedAt,
    );
  }

  final String firstName;
  final DateTime from;
  final DateTime to;
  final int absentPeriods;
  final int unjustifiedAbsentPeriods;
  final int lateness;
  final int unjustifiedLateness;
  final List<HistoryDay> days;
  final DateTime? fetchedAt;
}

/// A list from the server, or the copy kept on the phone when the network is down.
class Fetched<T> {
  const Fetched(this.value, this.fetchedAt);

  final T value;

  /// Set when [value] is the phone's copy, from that time.
  final DateTime? fetchedAt;
}

/// The parent's reads. Each answer is kept on the phone, so a parent on a weak network still sees
/// the last messages and history (NFR-2).
class ParentRepository {
  ParentRepository(this._dio, this._cache);

  final Dio _dio;
  final ResponseCache _cache;

  static const pageSize = 30;

  static const _pendingKey = 'inbox-opened-pending';

  Future<Fetched<List<Child>>> children(String userId) async {
    final (json, at) = await _get(userId, '/me/children', 'children');
    return Fetched([for (final c in (json as List).cast<Map<String, dynamic>>()) Child.fromJson(c)], at);
  }

  /// The newest messages; with [before], the page older than that message (not kept on the phone).
  Future<Fetched<List<InboxEntry>>> inbox(String userId, {String? before}) async {
    final query = {'limit': pageSize, 'before': ?before};
    final Object? json;
    final DateTime? at;
    if (before == null) {
      (json, at) = await _get(userId, '/me/notifications', 'inbox', query: query);
    } else {
      json = (await _call(() => _dio.get<List<dynamic>>('/me/notifications', queryParameters: query))).data;
      at = null;
    }
    return Fetched([for (final e in (json! as List).cast<Map<String, dynamic>>()) InboxEntry.fromJson(e)], at);
  }

  Future<ChildHistory> history(String userId, String childId) async {
    final (json, at) = await _get(userId, '/children/$childId/history', 'history:$childId');
    return ChildHistory.fromJson(json! as Map<String, dynamic>, fetchedAt: at);
  }

  /// Messages opened on this phone whose receipt the server does not have yet.
  Future<Set<String>> pendingReceipts(String userId) async {
    final cached = await _cache.get(userId, _pendingKey);
    return {...?(cached?.value as List?)?.cast<String>()};
  }

  /// BR-24: tells the server the parent opened [ids] in the app. A receipt that cannot be sent
  /// now waits on the phone and is sent with the next one; one the server refuses is dropped.
  /// Returns the ids still waiting.
  Future<Set<String>> sendReceipts(String userId, Iterable<String> ids) async {
    final waiting = {...await pendingReceipts(userId), ...ids};
    for (final id in [...waiting]) {
      try {
        await _dio.post<void>('/notifications/$id/opened');
        waiting.remove(id);
      } on DioException catch (e) {
        final failure = ApiFailure.from(e);
        if (failure.isNetwork || failure.status >= 500 || failure.status == 429 || failure.status == 401) {
          continue; // tried again with the next receipt or the next time the inbox loads
        }
        waiting.remove(id); // not found or not theirs: there is nothing to retry
      }
    }
    await _cache.put(userId, _pendingKey, [...waiting]);
    return waiting;
  }

  Future<(Object?, DateTime?)> _get(String userId, String path, String key, {Map<String, dynamic>? query}) async {
    try {
      final response = await _dio.get<Object>(path, queryParameters: query);
      await _cache.put(userId, key, response.data);
      return (response.data, null);
    } on DioException catch (e) {
      final failure = ApiFailure.from(e);
      if (!failure.isNetwork) {
        throw failure;
      }
      final cached = await _cache.get(userId, key);
      if (cached == null) {
        throw failure;
      }
      return (cached.value, cached.fetchedAt);
    }
  }

  Future<Response<T>> _call<T>(Future<Response<T>> Function() request) async {
    try {
      return await request();
    } on DioException catch (e) {
      throw ApiFailure.from(e);
    }
  }
}

final parentRepositoryProvider = Provider<ParentRepository>(
  (ref) => ParentRepository(ref.watch(dioProvider), ref.watch(responseCacheProvider)),
);

String _userId(Ref ref) {
  final userId = ref.watch(authControllerProvider.select((auth) => auth.value?.id));
  if (userId == null) {
    throw StateError('signed out');
  }
  return userId;
}

/// UC-P3: the parent's children.
final childrenProvider = FutureProvider.autoDispose<Fetched<List<Child>>>(
  (ref) => ref.watch(parentRepositoryProvider).children(_userId(ref)),
);

/// UC-P3: one child's history over the last 90 days.
final childHistoryProvider = FutureProvider.autoDispose.family<ChildHistory, String>(
  (ref, childId) => ref.watch(parentRepositoryProvider).history(_userId(ref), childId),
);

/// The inbox as shown: the messages loaded so far, newest first.
class Inbox {
  const Inbox({required this.entries, required this.hasMore, required this.opened, this.fetchedAt});

  final List<InboxEntry> entries;

  /// The last page was full: older messages may be on the server.
  final bool hasMore;

  /// Opened on this phone, receipt sent or not: shown as read at once.
  final Set<String> opened;
  final DateTime? fetchedAt;

  bool isOpened(InboxEntry entry) => entry.openedAt != null || opened.contains(entry.id);

  int get unread => entries.where((e) => !isOpened(e)).length;

  InboxEntry? byId(String id) => entries.where((e) => e.id == id).firstOrNull;

  Inbox copyWith({List<InboxEntry>? entries, bool? hasMore, Set<String>? opened}) => Inbox(
    entries: entries ?? this.entries,
    hasMore: hasMore ?? this.hasMore,
    opened: opened ?? this.opened,
    fetchedAt: fetchedAt,
  );
}

/// UC-P2: the parent's messages. Receipts left from a time without network are sent first, so
/// the server's copy says what was read.
class InboxController extends AsyncNotifier<Inbox> {
  bool _loadingMore = false;

  @override
  Future<Inbox> build() async {
    final userId = _userId(ref);
    final repository = ref.watch(parentRepositoryProvider);
    final waiting = await repository.sendReceipts(userId, const []);
    final page = await repository.inbox(userId);
    return Inbox(
      entries: page.value,
      hasMore: page.fetchedAt == null && page.value.length == ParentRepository.pageSize,
      opened: waiting,
      fetchedAt: page.fetchedAt,
    );
  }

  /// The next page of older messages, at the end of the list.
  Future<void> loadMore() async {
    final inbox = state.value;
    if (inbox == null || !inbox.hasMore || inbox.entries.isEmpty || _loadingMore) {
      return;
    }
    _loadingMore = true;
    try {
      final page = await ref.read(parentRepositoryProvider).inbox(_userId(ref), before: inbox.entries.last.id);
      state = AsyncData(
        inbox.copyWith(
          entries: [...inbox.entries, ...page.value],
          hasMore: page.value.length == ParentRepository.pageSize,
        ),
      );
    } finally {
      _loadingMore = false;
    }
  }

  /// BR-24: the parent opened [id]; read at once on the phone, the receipt sent in the background.
  Future<void> markOpened(String id) async {
    if (state.isLoading) {
      // opened from a notification while the inbox loads: shown read once it is there
      try {
        await future;
      } catch (_) {
        // no inbox: the receipt is still sent, or waits on the phone
      }
    }
    final inbox = state.value;
    if (inbox != null) {
      if (inbox.opened.contains(id) || inbox.byId(id)?.openedAt != null) {
        return; // the server has it, or it is on its way
      }
      state = AsyncData(inbox.copyWith(opened: {...inbox.opened, id}));
    }
    await ref.read(parentRepositoryProvider).sendReceipts(_userId(ref), [id]);
  }
}

final inboxProvider = AsyncNotifierProvider.autoDispose<InboxController, Inbox>(InboxController.new);
