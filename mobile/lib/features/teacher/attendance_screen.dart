import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/db/database.dart';
import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
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

    Widget message(String text, IconData icon) => Scaffold(
      appBar: AppBar(),
      body: Center(
        child: SingleChildScrollView(
          child: EmptyState(icon: icon, title: text),
        ),
      ),
    );

    if (day is AsyncError) {
      return message(failureText(day.error!, l), Icons.wifi_off_rounded);
    }
    final teacherDay = day.value;
    final localSessions = local.value;
    if (teacherDay == null || localSessions == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final session = teacherDay.sessions.where((s) => s.classId == classId && s.periodId == periodId).firstOrNull;
    if (session == null) {
      return message(l.sessionNotFound, Icons.event_busy_rounded);
    }
    final mine = localSessions.where((s) => s.isFor(session, teacherDay.date)).firstOrNull;
    final roster = rosters.value?[classId];
    if (mine != null || session.recorded) {
      return _LockedSession(session: session, local: mine, roster: roster);
    }
    if (roster == null) {
      return rosters is AsyncLoading
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : message(l.rosterUnavailable, Icons.cloud_download_outlined);
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
    final status = StatusColors.of(context);
    final times = l.sessionTimes(session.startTime.toString(), session.endTime.toString());
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(session.className),
        Text(
          [?session.subject, times].join(', '),
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted),
        ),
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
  /// Above this many students the roll gets a search field.
  static const _searchFrom = 13;

  final Set<String> _absent = {};
  bool _saving = false;
  String _query = '';

  void _toggle(String id) => setState(() => _absent.contains(id) ? _absent.remove(id) : _absent.add(id));

  /// UC-T1 steps 4 to 6. Either "Back" returns to the roll (steps 7).
  Future<void> _save() async {
    final l = AppLocalizations.of(context);
    final status = StatusColors.of(context);
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
            : ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 320),
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      for (final s in absentees)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: Gap.xs + 2),
                          child: Row(
                            children: [
                              InitialsAvatar(
                                firstName: s.firstName,
                                lastName: s.lastName,
                                size: 32,
                                background: status.absentContainer,
                                foreground: status.absent,
                              ),
                              const SizedBox(width: Gap.m),
                              Expanded(child: Text(s.fullName, style: Theme.of(context).textTheme.bodyLarge)),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
              ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.back)),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(minimumSize: const Size(96, 48)),
            child: Text(l.yes),
          ),
        ],
      ),
    );
    if (correct != true || !mounted) {
      return;
    }
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.lock_outline_rounded),
        title: Text(l.confirmNotifyTitle),
        content: Text(l.confirmNotifyBody),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.back)),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(minimumSize: const Size(96, 48)),
            child: Text(l.confirm),
          ),
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

  bool _matches(RosterStudent s) {
    final q = _query.trim().toLowerCase();
    return q.isEmpty ||
        '${s.lastName} ${s.firstName}'.toLowerCase().contains(q) ||
        '${s.firstName} ${s.lastName}'.toLowerCase().contains(q) ||
        s.matricule.toLowerCase().startsWith(q);
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final present = widget.roster.length - _absent.length;
    final shown = widget.roster.where(_matches).toList();

    return PopScope(
      canPop: _absent.isEmpty || _saving,
      onPopInvokedWithResult: (didPop, _) async {
        if (!didPop && await _confirmLeave() && context.mounted) {
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(title: _SessionTitle(widget.session), toolbarHeight: 68),
        body: Column(
          children: [
            HeaderBand(
              padding: const EdgeInsets.fromLTRB(Gap.l, 0, Gap.l, Gap.l),
              children: [
                Text(l.attendanceHint, style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted)),
                const SizedBox(height: Gap.m),
                Semantics(
                  liveRegion: true,
                  child: Row(
                    children: [
                      Expanded(
                        child: _Count(
                          text: l.presentCount(present),
                          icon: Icons.check_circle_rounded,
                          background: status.presentContainer,
                          foreground: status.present,
                        ),
                      ),
                      const SizedBox(width: Gap.s),
                      Expanded(
                        child: _Count(
                          text: l.absentCount(_absent.length),
                          icon: Icons.cancel_rounded,
                          background: _absent.isEmpty
                              ? status.onHeader.withValues(alpha: 0.12)
                              : status.absentContainer,
                          foreground: _absent.isEmpty ? status.onHeader : status.absent,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (widget.roster.length >= _searchFrom)
              Padding(
                padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, 0),
                child: TextField(
                  onChanged: (value) => setState(() => _query = value),
                  textInputAction: TextInputAction.search,
                  decoration: InputDecoration(
                    hintText: l.findStudent,
                    prefixIcon: const Icon(Icons.search_rounded),
                    isDense: true,
                  ),
                ),
              ),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.l),
                itemCount: shown.length,
                itemBuilder: (context, i) {
                  final student = shown[i];
                  final absent = _absent.contains(student.id);
                  return Padding(
                    padding: const EdgeInsets.only(bottom: Gap.s),
                    child: _StudentRow(
                      key: ValueKey('student-${student.id}'),
                      student: student,
                      absent: absent,
                      onTap: _saving ? null : () => _toggle(student.id),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
        bottomNavigationBar: DecoratedBox(
          decoration: BoxDecoration(
            color: scheme.surfaceContainerLowest,
            border: Border(top: BorderSide(color: scheme.outlineVariant)),
          ),
          child: SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.m),
              child: FilledButton(
                onPressed: _saving ? null : _save,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.done_all_rounded, size: 22),
                    const SizedBox(width: Gap.s),
                    Flexible(child: Text(l.save, overflow: TextOverflow.ellipsis)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A count on the header band, as a word and a figure ("Absents : 2"), with its icon.
class _Count extends StatelessWidget {
  const _Count({required this.text, required this.icon, required this.background, required this.foreground});

  final String text;
  final IconData icon;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      padding: const EdgeInsets.symmetric(horizontal: Gap.m, vertical: Gap.m - 2),
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(Radii.m)),
      child: Row(
        children: [
          Icon(icon, color: foreground, size: 20),
          const SizedBox(width: Gap.s),
          Expanded(
            child: Text(
              text,
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(color: foreground, fontWeight: FontWeight.w700, fontFeatures: tabular),
            ),
          ),
        ],
      ),
    );
  }
}

/// One student on the roll: present (the default, BR-2) or absent, switched by a tap anywhere on
/// the row. Absent is said three ways: the red row, the cross, and the word.
class _StudentRow extends StatelessWidget {
  const _StudentRow({super.key, required this.student, required this.absent, required this.onTap});

  final RosterStudent student;
  final bool absent;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    return Semantics(
      toggled: absent,
      child: Material(
        color: absent ? status.absentContainer : scheme.surfaceContainerLowest,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(Radii.m),
          side: BorderSide(color: absent ? status.absent.withValues(alpha: 0.45) : scheme.outlineVariant),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 64),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: Gap.m, vertical: Gap.s),
              child: Row(
                children: [
                  InitialsAvatar(
                    firstName: student.firstName,
                    lastName: student.lastName,
                    background: absent ? status.absent : scheme.primaryContainer,
                    foreground: absent ? Colors.white : scheme.onPrimaryContainer,
                    icon: absent ? Icons.close_rounded : null,
                  ),
                  const SizedBox(width: Gap.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          student.fullName,
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontSize: 17,
                            color: absent ? const Color(0xFF7A1212) : scheme.onSurface,
                          ),
                        ),
                        Text(
                          student.matricule,
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: scheme.onSurfaceVariant,
                            fontFeatures: tabular,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (absent)
                    StatusPill(label: l.absentLabel, background: status.absent, foreground: Colors.white)
                  else
                    Icon(Icons.check_circle_outline_rounded, color: status.present.withValues(alpha: 0.55)),
                ],
              ),
            ),
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
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final confirmedAt = local?.confirmedAt ?? session.confirmedAt;
    final byId = {for (final s in roster ?? const <RosterStudent>[]) s.id: s};
    final absentees = local == null
        ? null
        : ([for (final id in local!.absentStudentIds) (byId[id], byId[id]?.fullName ?? id)]
            ..sort((a, b) => a.$2.compareTo(b.$2)));

    return Scaffold(
      appBar: AppBar(title: _SessionTitle(session), toolbarHeight: 68),
      body: ListView(
        padding: EdgeInsets.zero,
        children: [
          HeaderBand(
            padding: const EdgeInsets.fromLTRB(Gap.l, 0, Gap.l, Gap.xl),
            children: [
              Row(
                children: [
                  CircleAvatar(
                    radius: 22,
                    backgroundColor: status.onHeader.withValues(alpha: 0.14),
                    child: Icon(Icons.lock_rounded, color: status.onHeader),
                  ),
                  const SizedBox(width: Gap.m),
                  Expanded(
                    child: Text(
                      confirmedAt == null ? '' : l.sessionLockedAt(DateFormat.Hm(locale).format(confirmedAt)),
                      style: theme.textTheme.titleMedium?.copyWith(color: status.onHeader),
                    ),
                  ),
                ],
              ),
              if (local case final local?) ...[const SizedBox(height: Gap.m), _SyncStatus(local: local)],
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(Gap.l),
            child: absentees == null
                ? Card(
                    child: Padding(
                      padding: const EdgeInsets.all(Gap.l),
                      child: Row(
                        children: [
                          Icon(
                            (session.absentCount ?? 0) == 0 ? Icons.check_circle_rounded : Icons.person_off_outlined,
                            color: (session.absentCount ?? 0) == 0 ? status.present : status.absent,
                          ),
                          const SizedBox(width: Gap.m),
                          Expanded(
                            child: Text(l.absentTotal(session.absentCount ?? 0), style: theme.textTheme.bodyLarge),
                          ),
                        ],
                      ),
                    ),
                  )
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      SectionHeader(
                        title: l.absentStudents,
                        trailing: StatusPill(
                          label: '${absentees.length}',
                          background: absentees.isEmpty ? status.presentContainer : status.absentContainer,
                          foreground: absentees.isEmpty ? status.present : status.absent,
                        ),
                      ),
                      if (absentees.isEmpty)
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(Gap.l),
                            child: Row(
                              children: [
                                Icon(Icons.check_circle_rounded, color: status.present),
                                const SizedBox(width: Gap.m),
                                Expanded(child: Text(l.noAbsentStudents, style: theme.textTheme.bodyLarge)),
                              ],
                            ),
                          ),
                        )
                      else
                        Card(
                          child: Column(
                            children: [
                              for (final (i, (student, name)) in absentees.indexed) ...[
                                if (i > 0) const Divider(indent: 64),
                                ListTile(
                                  minTileHeight: 56,
                                  leading: InitialsAvatar(
                                    firstName: student?.firstName ?? name,
                                    lastName: student?.lastName ?? '',
                                    size: 36,
                                    background: status.absentContainer,
                                    foreground: status.absent,
                                  ),
                                  title: Text(name, style: theme.textTheme.bodyLarge),
                                  subtitle: student == null
                                      ? null
                                      : Text(
                                          student.matricule,
                                          style: TextStyle(color: scheme.onSurfaceVariant, fontFeatures: tabular),
                                        ),
                                ),
                              ],
                            ],
                          ),
                        ),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

/// Whether the school has the session yet, on the header band.
class _SyncStatus extends StatelessWidget {
  const _SyncStatus({required this.local});

  final LocalSession local;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final status = StatusColors.of(context);
    final (icon, background, foreground, text) = switch (local.state) {
      SyncState.pending => (Icons.cloud_upload_outlined, status.lateContainer, status.onLateContainer, l.syncWaiting),
      SyncState.synced => (Icons.cloud_done_rounded, status.presentContainer, status.present, l.syncSent),
      SyncState.conflict || SyncState.rejected => (
        Icons.error_outline_rounded,
        status.absentContainer,
        const Color(0xFF7A1212),
        [l.syncNotAccepted, ?local.errorMessage].join(' '),
      ),
    };
    return InfoBanner(icon: icon, text: text, background: background, foreground: foreground);
  }
}
