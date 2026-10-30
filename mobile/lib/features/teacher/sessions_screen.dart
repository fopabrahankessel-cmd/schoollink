import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'teacher_sessions.dart';

/// UC-T3: the teacher's sessions today, the one under way first, those still to record
/// highlighted. Opens from the phone's copy when there is no network (NFR-3).
class SessionsScreen extends ConsumerStatefulWidget {
  const SessionsScreen({super.key});

  @override
  ConsumerState<SessionsScreen> createState() => _SessionsScreenState();
}

class _SessionsScreenState extends ConsumerState<SessionsScreen> {
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    // the period under way changes with the time, not with the data
    _tick = Timer.periodic(const Duration(minutes: 1), (_) => setState(() {}));
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final day = ref.watch(teacherDayProvider);
    final now = ref.watch(clockProvider)();

    return switch (day) {
      AsyncValue(value: final day?) => RefreshIndicator(
        onRefresh: () => ref.refresh(teacherDayProvider.future),
        child: _DayList(day: day, now: now),
      ),
      AsyncError(:final error) => _Failure(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(teacherDayProvider),
      ),
      _ => const Center(child: CircularProgressIndicator()),
    };
  }
}

class _DayList extends StatelessWidget {
  const _DayList({required this.day, required this.now});

  final TeacherDay day;
  final DateTime now;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final today = day.date.year == now.year && day.date.month == now.month && day.date.day == now.day;
    final time = DayTime.of(now);
    final sessions = day.orderedAt(today ? time : null);
    final empty = !day.schoolDay
        ? l.notASchoolDay
        : sessions.isEmpty
        ? l.noLessonsToday
        : null;

    return ListView(
      // pulling down refreshes even when the list is short
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
      children: [
        Text(DateFormat.yMMMMEEEEd(locale).format(day.date), style: theme.textTheme.titleMedium),
        if (day.fetchedAt case final fetchedAt?) ...[
          const SizedBox(height: 12),
          _OfflineNotice(text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt))),
        ],
        const SizedBox(height: 12),
        if (empty != null)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 48),
            child: Text(empty, style: theme.textTheme.bodyLarge, textAlign: TextAlign.center),
          )
        else
          for (final session in sessions)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: SessionTile(session: session, current: today && session.isCurrentAt(time)),
            ),
      ],
    );
  }
}

/// One session: its times, class and subject, and whether it is still to record.
class SessionTile extends StatelessWidget {
  const SessionTile({super.key, required this.session, required this.current});

  final ExpectedSession session;
  final bool current;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final toRecord = !session.recorded;
    final status = toRecord ? l.sessionToRecord : l.sessionRecorded(session.absentCount ?? 0);

    return Semantics(
      container: true,
      label: [
        if (current) l.sessionNow,
        l.sessionTimes(session.startTime.toString(), session.endTime.toString()),
        session.className,
        ?session.subject,
        status,
      ].join(', '),
      excludeSemantics: true,
      child: Card(
        key: ValueKey('session-${session.classId}-${session.periodId}'),
        margin: EdgeInsets.zero,
        elevation: 0,
        color: toRecord ? scheme.primaryContainer : scheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: current ? BorderSide(color: scheme.primary, width: 2) : BorderSide.none,
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 64,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(session.startTime.toString(), style: theme.textTheme.titleMedium),
                    Text(session.endTime.toString(), style: theme.textTheme.bodySmall),
                  ],
                ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      session.className,
                      style: theme.textTheme.titleLarge?.copyWith(
                        color: toRecord ? scheme.onPrimaryContainer : null,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    if (session.subject case final subject?)
                      Text(
                        subject,
                        style: theme.textTheme.bodyMedium?.copyWith(color: toRecord ? scheme.onPrimaryContainer : null),
                      ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 4,
                      children: [
                        if (current)
                          _Badge(text: l.sessionNow, background: scheme.primary, foreground: scheme.onPrimary),
                        toRecord
                            ? _Badge(text: status, background: scheme.surface, foreground: scheme.primary)
                            : Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Icon(Icons.check_circle, size: 18, color: scheme.tertiary),
                                  const SizedBox(width: 4),
                                  Text(status, style: theme.textTheme.bodyMedium),
                                ],
                              ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  const _Badge({required this.text, required this.background, required this.foreground});

  final String text;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(6)),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        child: Text(
          text,
          style: Theme.of(context).textTheme.labelLarge?.copyWith(color: foreground, fontWeight: FontWeight.w700),
        ),
      ),
    );
  }
}

class _OfflineNotice extends StatelessWidget {
  const _OfflineNotice({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(color: scheme.secondaryContainer, borderRadius: BorderRadius.circular(8)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(Icons.cloud_off_outlined, color: scheme.onSecondaryContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Text(text, style: TextStyle(color: scheme.onSecondaryContainer)),
            ),
          ],
        ),
      ),
    );
  }
}

class _Failure extends StatelessWidget {
  const _Failure({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, style: Theme.of(context).textTheme.bodyLarge, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton(onPressed: onRetry, child: Text(l.retry)),
          ],
        ),
      ),
    );
  }
}
