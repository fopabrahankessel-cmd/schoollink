import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/db/database.dart';
import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'attendance.dart';
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
    // read now, so the rosters are on the phone before the network goes (NFR-3)
    ref.watch(rostersProvider);
    final local = ref.watch(localSessionsProvider).value ?? const <LocalSession>[];

    return switch (day) {
      AsyncValue(value: final day?) => RefreshIndicator(
        onRefresh: () => ref.refresh(teacherDayProvider.future),
        child: _DayList(day: day, now: now, local: local),
      ),
      AsyncError(:final error) => FailureView(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(teacherDayProvider),
      ),
      _ => const Center(child: CircularProgressIndicator()),
    };
  }
}

class _DayList extends StatelessWidget {
  const _DayList({required this.day, required this.now, required this.local});

  final TeacherDay day;
  final DateTime now;

  /// Sessions confirmed on this phone: recorded, even before the server has them.
  final List<LocalSession> local;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final today = day.date.year == now.year && day.date.month == now.month && day.date.day == now.day;
    final time = DayTime.of(now);
    final sessions = day.orderedAt(today ? time : null);
    LocalSession? localOf(ExpectedSession s) => local.where((l) => l.isFor(s, day.date)).firstOrNull;
    final done = sessions.where((s) => s.recorded || localOf(s) != null).length;
    final current = today ? sessions.where((s) => s.isCurrentAt(time)).firstOrNull : null;
    final rest = [
      for (final s in sessions)
        if (!identical(s, current)) s,
    ];

    return ListView(
      // pulling down refreshes even when the list is short
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: Gap.xl),
      children: [
        _DayHeader(
          date: DateFormat.yMMMMEEEEd(locale).format(day.date),
          done: done,
          total: sessions.length,
          schoolDay: day.schoolDay,
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (day.fetchedAt case final fetchedAt?) ...[
                const SizedBox(height: Gap.l),
                InfoBanner(
                  icon: Icons.cloud_off_rounded,
                  text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                  background: Theme.of(context).colorScheme.secondaryContainer,
                  foreground: Theme.of(context).colorScheme.onSecondaryContainer,
                ),
              ],
              if (!day.schoolDay)
                EmptyState(icon: Icons.event_busy_rounded, title: l.notASchoolDay)
              else if (sessions.isEmpty)
                EmptyState(icon: Icons.free_breakfast_outlined, title: l.noLessonsToday)
              else ...[
                if (current != null) ...[
                  const SizedBox(height: Gap.l),
                  SessionTile(session: current, current: true, local: localOf(current)),
                ],
                if (rest.isNotEmpty) SectionHeader(title: l.todaySchedule),
                for (final session in rest)
                  Padding(
                    padding: const EdgeInsets.only(bottom: Gap.m),
                    child: SessionTile(session: session, current: false, local: localOf(session)),
                  ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// The day, and how many rolls are left: the one figure a teacher needs on opening the app.
class _DayHeader extends StatelessWidget {
  const _DayHeader({required this.date, required this.done, required this.total, required this.schoolDay});

  final String date;
  final int done;
  final int total;
  final bool schoolDay;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final left = total - done;
    return HeaderBand(
      children: [
        Text(date, style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted)),
        const SizedBox(height: Gap.xs),
        Text(
          !schoolDay || total == 0 ? l.noRollToday : l.rollsLeft(left),
          style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader),
        ),
        if (schoolDay && total > 0) ...[
          const SizedBox(height: Gap.m),
          ClipRRect(
            borderRadius: BorderRadius.circular(99),
            child: LinearProgressIndicator(
              value: done / total,
              minHeight: 8,
              color: left == 0 ? const Color(0xFF5FD39A) : status.onHeader,
              backgroundColor: status.onHeader.withValues(alpha: 0.16),
            ),
          ),
          const SizedBox(height: Gap.s),
          Text(
            l.rollsProgress(done, total),
            style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted, fontFeatures: tabular),
          ),
        ],
      ],
    );
  }
}

/// One session: its times, class and subject, and whether it is still to record. Opens the roll,
/// or the locked session once recorded (UC-T1). The period under way is a larger card with the
/// call to action.
class SessionTile extends StatelessWidget {
  const SessionTile({super.key, required this.session, required this.current, this.local});

  final ExpectedSession session;
  final bool current;

  /// The copy confirmed on this phone, if any.
  final LocalSession? local;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final toRecord = !session.recorded && local == null;
    final absent = local?.absentStudentIds.length ?? session.absentCount ?? 0;
    final statusText = toRecord ? l.sessionToRecord : l.sessionRecorded(absent);
    final waiting = local?.state == SyncState.pending;
    final times = l.sessionTimes(session.startTime.toString(), session.endTime.toString());
    void open() => context.push('/home/sessions/attendance/${session.classId}/${session.periodId}');

    final pill = toRecord
        ? StatusPill(
            label: statusText,
            icon: Icons.radio_button_unchecked_rounded,
            background: scheme.primaryContainer,
            foreground: scheme.onPrimaryContainer,
          )
        : StatusPill(
            label: statusText,
            icon: Icons.check_circle_rounded,
            background: status.presentContainer,
            foreground: status.present,
          );
    final waitingLine = waiting
        ? Padding(
            padding: const EdgeInsets.only(top: Gap.s),
            child: Row(
              children: [
                Icon(Icons.cloud_upload_outlined, size: 18, color: status.late),
                const SizedBox(width: Gap.xs + 2),
                Expanded(
                  child: Text(l.syncWaiting, style: theme.textTheme.bodySmall?.copyWith(color: status.onLateContainer)),
                ),
              ],
            ),
          )
        : null;

    return Semantics(
      container: true,
      label: [
        if (current) l.sessionNow,
        times,
        session.className,
        ?session.subject,
        statusText,
        if (waiting) l.syncWaiting,
      ].join(', '),
      button: true,
      onTap: open,
      excludeSemantics: true,
      child: Card(
        clipBehavior: Clip.antiAlias,
        key: ValueKey('session-${session.classId}-${session.periodId}'),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Radii.l),
          side: current
              ? BorderSide(color: scheme.primary, width: 2)
              : BorderSide(color: toRecord ? scheme.outline.withValues(alpha: 0.5) : scheme.outlineVariant),
        ),
        child: InkWell(
          onTap: open,
          child: current
              ? Padding(
                  padding: const EdgeInsets.all(Gap.l + 2),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          StatusPill(
                            label: l.sessionNow,
                            icon: Icons.schedule_rounded,
                            background: scheme.primary,
                            foreground: scheme.onPrimary,
                          ),
                          const SizedBox(width: Gap.s),
                          Flexible(child: pill),
                        ],
                      ),
                      const SizedBox(height: Gap.m),
                      Text(session.className, style: theme.textTheme.headlineSmall),
                      Text(
                        [?session.subject, times].join(', '),
                        style: theme.textTheme.bodyLarge?.copyWith(
                          color: scheme.onSurfaceVariant,
                          fontFeatures: tabular,
                        ),
                      ),
                      ?waitingLine,
                      if (toRecord) ...[
                        const SizedBox(height: Gap.l),
                        FilledButton.icon(
                          onPressed: open,
                          icon: const Icon(Icons.fact_check_outlined),
                          label: Text(l.takeRoll),
                        ),
                      ],
                    ],
                  ),
                )
              : IntrinsicHeight(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Container(width: 5, color: toRecord ? scheme.primary : status.present),
                      SizedBox(
                        width: 76,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(Gap.m, Gap.l, 0, Gap.l),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                session.startTime.toString(),
                                style: theme.textTheme.titleMedium?.copyWith(
                                  fontWeight: FontWeight.w700,
                                  fontFeatures: tabular,
                                ),
                              ),
                              Text(
                                session.endTime.toString(),
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: scheme.onSurfaceVariant,
                                  fontFeatures: tabular,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(Gap.xs, Gap.l, Gap.m, Gap.l),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                session.className,
                                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                              ),
                              if (session.subject case final subject?)
                                Text(
                                  subject,
                                  style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                                ),
                              const SizedBox(height: Gap.s),
                              Align(alignment: Alignment.centerLeft, child: pill),
                              ?waitingLine,
                            ],
                          ),
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.only(right: Gap.s),
                        child: Icon(Icons.chevron_right_rounded, color: scheme.outline),
                      ),
                    ],
                  ),
                ),
        ),
      ),
    );
  }
}
