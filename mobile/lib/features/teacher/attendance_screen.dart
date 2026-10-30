import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/db/database.dart';
import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';
import '../auth/failure_text.dart';
import 'attendance.dart';
import 'teacher_sessions.dart';

/// UC-T1: the roll of one session. Every student is present until tapped (BR-2); saving asks
/// twice (BR-3), and either "Back" returns to the roll with the marks kept. Once confirmed the
/// session is locked (BR-4) and the screen only shows it. Works with no network (UC-T2, NFR-3).
class AttendanceScreen extends ConsumerWidget {
  const AttendanceScreen({super.key, required this.classId, required this.periodId});

  final String classId;
  final String periodId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final day = ref.watch(teacherDayProvider);
    final rosters = ref.watch(rostersProvider);
    final local = ref.watch(localSessionsProvider);

    Widget message(String text) => Scaffold(
      appBar: AppBar(),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(text, style: Theme.of(context).textTheme.bodyLarge, textAlign: TextAlign.center),
        ),
      ),
    );

    if (day is AsyncError) {
      return message(failureText(day.error!, l));
    }
    final teacherDay = day.value;
    final localSessions = local.value;
    if (teacherDay == null || localSessions == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final session = teacherDay.sessions.where((s) => s.classId == classId && s.periodId == periodId).firstOrNull;
    if (session == null) {
      return message(l.sessionNotFound);
    }
    final mine = localSessions.where((s) => s.isFor(session, teacherDay.date)).firstOrNull;
    final roster = rosters.value?[classId];
    if (mine != null || session.recorded) {
      return _LockedSession(session: session, local: mine, roster: roster);
    }
    if (roster == null) {
      return rosters is AsyncLoading
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : message(l.rosterUnavailable);
    }
    return _Roll(session: session, date: teacherDay.date, roster: roster);
  }
}

/// The title of both views: class, then subject and times.
class _SessionTitle extends StatelessWidget {
  const _SessionTitle(this.session);

  final ExpectedSession session;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final times = l.sessionTimes(session.startTime.toString(), session.endTime.toString());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(session.className),
        Text([?session.subject, times].join(', '), style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}

class _Roll extends ConsumerStatefulWidget {
  const _Roll({required this.session, required this.date, required this.roster});

  final ExpectedSession session;
  final DateTime date;
  final List<RosterStudent> roster;

  @override
  ConsumerState<_Roll> createState() => _RollState();
}

class _RollState extends ConsumerState<_Roll> {
  final Set<String> _absent = {};
  bool _saving = false;

  void _toggle(String id) => setState(() => _absent.contains(id) ? _absent.remove(id) : _absent.add(id));

  /// UC-T1 steps 4 to 6. Either "Back" returns to the roll (steps 7).
  Future<void> _save() async {
    final l = AppLocalizations.of(context);
    final absentees = [
      for (final s in widget.roster)
        if (_absent.contains(s.id)) s,
    ];
    final correct = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.confirmAbsentees(absentees.length)),
        content: absentees.isEmpty
            ? null
            : SingleChildScrollView(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (final s in absentees)
                      Padding(padding: const EdgeInsets.symmetric(vertical: 4), child: Text(s.fullName)),
                  ],
                ),
              ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.back)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l.yes)),
        ],
      ),
    );
    if (correct != true || !mounted) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.confirmNotifyTitle),
        content: Text(l.confirmNotifyBody),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.back)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(l.confirm)),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    setState(() => _saving = true);
    // the locked view replaces the roll as soon as the queued session is seen
    await ref
        .read(attendanceRecorderProvider)
        .record(
          userId: ref.read(authControllerProvider).value!.id,
          session: widget.session,
          date: widget.date,
          absentStudentIds: Set.of(_absent),
        );
  }

  Future<bool> _confirmLeave() async {
    final l = AppLocalizations.of(context);
    final leave = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.leaveTitle),
        content: Text(l.leaveBody),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.back)),
          TextButton(onPressed: () => Navigator.pop(context, true), child: Text(l.leave)),
        ],
      ),
    );
    return leave == true;
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final present = widget.roster.length - _absent.length;

    return PopScope(
      canPop: _absent.isEmpty || _saving,
      onPopInvokedWithResult: (didPop, _) async {
        if (!didPop && await _confirmLeave() && context.mounted) {
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(title: _SessionTitle(widget.session), toolbarHeight: 72),
        body: Column(
          children: [
            Material(
              color: scheme.surfaceContainer,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                child: Row(
                  children: [
                    Expanded(child: Text(l.attendanceHint, style: theme.textTheme.bodyMedium)),
                    Semantics(
                      liveRegion: true,
                      child: Text(
                        '${l.presentCount(present)}   ${l.absentCount(_absent.length)}',
                        style: theme.textTheme.titleSmall,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            Expanded(
              child: ListView.separated(
                padding: const EdgeInsets.only(bottom: 16),
                itemCount: widget.roster.length,
                separatorBuilder: (_, _) => const Divider(height: 1),
                itemBuilder: (context, i) {
                  final student = widget.roster[i];
                  final absent = _absent.contains(student.id);
                  return ListTile(
                    key: ValueKey('student-${student.id}'),
                    minTileHeight: 60,
                    tileColor: absent ? scheme.errorContainer : null,
                    selected: absent,
                    selectedColor: scheme.onErrorContainer,
                    title: Text(student.fullName, style: const TextStyle(fontSize: 17)),
                    subtitle: Text(student.matricule),
                    trailing: absent
                        ? Text(
                            l.absentLabel,
                            style: TextStyle(color: scheme.error, fontWeight: FontWeight.w700),
                          )
                        : null,
                    onTap: _saving ? null : () => _toggle(student.id),
                  );
                },
              ),
            ),
          ],
        ),
        bottomNavigationBar: SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: FilledButton(onPressed: _saving ? null : _save, child: Text(l.save)),
          ),
        ),
      ),
    );
  }
}

/// BR-4: a confirmed session, shown and never changed. With the copy on this phone, the absent
/// students by name and whether the school has it; otherwise what the server said.
class _LockedSession extends StatelessWidget {
  const _LockedSession({required this.session, required this.local, required this.roster});

  final ExpectedSession session;
  final LocalSession? local;
  final List<RosterStudent>? roster;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final confirmedAt = local?.confirmedAt ?? session.confirmedAt;
    final names = {for (final s in roster ?? const <RosterStudent>[]) s.id: s.fullName};
    final absentees = local == null ? null : ([for (final id in local!.absentStudentIds) names[id] ?? id]..sort());

    return Scaffold(
      appBar: AppBar(title: _SessionTitle(session), toolbarHeight: 72),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            children: [
              Icon(Icons.lock_outline, color: scheme.primary),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  confirmedAt == null ? '' : l.sessionLockedAt(DateFormat.Hm(locale).format(confirmedAt)),
                  style: theme.textTheme.titleMedium,
                ),
              ),
            ],
          ),
          if (local case final local?) ...[const SizedBox(height: 12), _SyncStatus(local: local)],
          const SizedBox(height: 24),
          if (absentees == null)
            Text(l.absentTotal(session.absentCount ?? 0), style: theme.textTheme.bodyLarge)
          else ...[
            Text(l.absentStudents, style: theme.textTheme.titleSmall),
            const SizedBox(height: 8),
            if (absentees.isEmpty)
              Text(l.noAbsentStudents, style: theme.textTheme.bodyLarge)
            else
              for (final name in absentees)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Text(name, style: theme.textTheme.bodyLarge),
                ),
          ],
        ],
      ),
    );
  }
}

class _SyncStatus extends StatelessWidget {
  const _SyncStatus({required this.local});

  final LocalSession local;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    final (icon, color, text) = switch (local.state) {
      SyncState.pending => (Icons.cloud_upload_outlined, scheme.onSurfaceVariant, l.syncWaiting),
      SyncState.synced => (Icons.cloud_done_outlined, scheme.tertiary, l.syncSent),
      SyncState.conflict ||
      SyncState.rejected => (Icons.error_outline, scheme.error, [l.syncNotAccepted, ?local.errorMessage].join(' ')),
    };
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: color),
        const SizedBox(width: 8),
        Expanded(
          child: Text(text, style: TextStyle(color: color)),
        ),
      ],
    );
  }
}
