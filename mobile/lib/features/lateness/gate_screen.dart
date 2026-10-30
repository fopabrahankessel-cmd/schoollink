import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/db/database.dart';
import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/auth_controller.dart';
import '../auth/failure_text.dart';
import '../teacher/attendance.dart' show isoDate;
import '../teacher/teacher_sessions.dart' show DayTime;
import 'lateness.dart';

/// UC-AD1, UC-AD2: the assistant DM at the gate. Search a student by name or matricule, record
/// the arrival time, and see today's lateness. Everything works with no network (NFR-3): the
/// students of the scope are on the phone, and records wait in the sync queue.
class GateScreen extends ConsumerStatefulWidget {
  const GateScreen({super.key});

  @override
  ConsumerState<GateScreen> createState() => _GateScreenState();
}

class _GateScreenState extends ConsumerState<GateScreen> {
  /// More results than this and the assistant DM types a few more letters.
  static const _maxResults = 30;

  final _search = TextEditingController();
  final _focus = FocusNode();

  @override
  void dispose() {
    _search.dispose();
    _focus.dispose();
    super.dispose();
  }

  Future<void> _refresh() async {
    ref.invalidate(gateStudentsProvider);
    await ref.refresh(latenessDayProvider.future).then((_) {}, onError: (_) {});
  }

  Future<void> _record(GateStudent student, DayTime? startTime) async {
    final l = AppLocalizations.of(context);
    final messenger = ScaffoldMessenger.of(context);
    final now = ref.read(clockProvider)();
    final arrival = await showModalBottomSheet<DayTime>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (context) => _RecordSheet(student: student, now: now, startTime: startTime),
    );
    if (arrival == null || !mounted) {
      return;
    }
    await ref
        .read(latenessRecorderProvider)
        .record(userId: ref.read(authControllerProvider).value!.id, student: student, arrival: arrival);
    _search.clear();
    _focus.unfocus();
    setState(() {});
    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(l.latenessSaved(student.fullName, arrival.toString()))));
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    final students = ref.watch(gateStudentsProvider);
    final day = ref.watch(latenessDayProvider);
    final local = ref.watch(localLatenessProvider).value ?? const <LocalLateness>[];

    final byId = {for (final s in students.value ?? const <GateStudent>[]) s.id: s};
    final todayLocal = [
      for (final r in local)
        if (r.date == isoDate(now) && !r.refused) r,
    ];
    final rows = mergeDay(server: day.value?.entries ?? const [], local: todayLocal, students: byId);
    final lateAt = {for (final r in rows) r.studentId: r.arrivalTime};
    final refused = [
      for (final r in local)
        if (r.date == isoDate(now) && r.refused) r,
    ];
    final query = _search.text;
    final searching = query.trim().isNotEmpty;
    final results = searching
        ? (students.value ?? const <GateStudent>[]).where((s) => s.matches(query)).toList()
        : null;

    return RefreshIndicator(
      onRefresh: _refresh,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.only(bottom: Gap.xl),
        children: [
          HeaderBand(
            children: [
              Text(
                DateFormat.yMMMMEEEEd(locale).format(now),
                style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted),
              ),
              const SizedBox(height: Gap.xs),
              Text(
                l.latenessToday(rows.length),
                style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader, fontFeatures: tabular),
              ),
              if (day.value?.startTime case final start?)
                Padding(
                  padding: const EdgeInsets.only(top: Gap.xs),
                  child: Text(
                    l.classesStartAt(start.toString()),
                    style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted),
                  ),
                ),
              const SizedBox(height: Gap.l),
              TextField(
                key: const ValueKey('gate-search'),
                controller: _search,
                focusNode: _focus,
                onChanged: (_) => setState(() {}),
                textInputAction: TextInputAction.search,
                style: theme.textTheme.titleMedium,
                decoration: InputDecoration(
                  hintText: l.searchStudentHint,
                  prefixIcon: const Icon(Icons.search_rounded),
                  suffixIcon: searching
                      ? IconButton(
                          tooltip: l.clearSearch,
                          icon: const Icon(Icons.close_rounded),
                          onPressed: () => setState(_search.clear),
                        )
                      : null,
                  enabledBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(Radii.m),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Gap.l),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (day.value?.fetchedAt case final fetchedAt?) ...[
                  const SizedBox(height: Gap.l),
                  InfoBanner(
                    icon: Icons.cloud_off_rounded,
                    text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                    background: theme.colorScheme.secondaryContainer,
                    foreground: theme.colorScheme.onSecondaryContainer,
                  ),
                ],
                if (results != null)
                  ..._results(context, results, students, lateAt, day.value?.startTime)
                else
                  ..._today(context, rows, refused, day, byId),
              ],
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _results(
    BuildContext context,
    List<GateStudent> results,
    AsyncValue<List<GateStudent>> students,
    Map<String, DayTime> lateAt,
    DayTime? startTime,
  ) {
    final l = AppLocalizations.of(context);
    if (students is AsyncError) {
      return [
        const SizedBox(height: Gap.l),
        EmptyState(
          icon: Icons.cloud_download_outlined,
          title: l.gateStudentsUnavailable,
          action: OutlinedButton.icon(
            onPressed: () => ref.invalidate(gateStudentsProvider),
            icon: const Icon(Icons.refresh_rounded),
            label: Text(l.retry),
            style: OutlinedButton.styleFrom(minimumSize: const Size(160, 48)),
          ),
        ),
      ];
    }
    if (students is AsyncLoading && results.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.all(Gap.xxl),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (results.isEmpty) {
      return [EmptyState(icon: Icons.person_search_outlined, title: l.noStudentFound)];
    }
    return [
      SectionHeader(title: l.searchResults(results.length)),
      Card(
        child: Column(
          children: [
            for (final (i, s) in results.take(_maxResults).indexed) ...[
              if (i > 0) const Divider(indent: 68),
              _StudentResult(
                student: s,
                lateAt: lateAt[s.id],
                onTap: lateAt.containsKey(s.id) ? null : () => _record(s, startTime),
              ),
            ],
          ],
        ),
      ),
      if (results.length > _maxResults)
        Padding(
          padding: const EdgeInsets.all(Gap.m),
          child: Text(
            l.typeMore,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium
                ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
        ),
    ];
  }

  List<Widget> _today(
    BuildContext context,
    List<GateRow> rows,
    List<LocalLateness> refused,
    AsyncValue<LatenessDay> day,
    Map<String, GateStudent> byId,
  ) {
    final l = AppLocalizations.of(context);
    return [
      SectionHeader(title: l.latenessListTitle),
      for (final r in refused)
        Padding(
          padding: const EdgeInsets.only(bottom: Gap.s),
          child: InfoBanner(
            icon: Icons.error_outline_rounded,
            text: [
              l.latenessRefused(byId[r.studentId]?.fullName ?? r.studentId, r.arrivalTime.toString()),
              ?r.errorMessage,
            ].join(' '),
            background: StatusColors.of(context).absentContainer,
            foreground: const Color(0xFF7A1212),
          ),
        ),
      if (day is AsyncError && rows.isEmpty)
        FailureView(message: failureText(day.error!, l), onRetry: () => ref.invalidate(latenessDayProvider))
      else if (day is AsyncLoading && rows.isEmpty)
        const Padding(
          padding: EdgeInsets.all(Gap.xxl),
          child: Center(child: CircularProgressIndicator()),
        )
      else if (rows.isEmpty)
        EmptyState(icon: Icons.door_front_door_outlined, title: l.noLatenessToday, body: l.noLatenessTodayHint)
      else
        LatenessList(rows: rows),
    ];
  }
}

/// A student found by the search: tap to record. One lateness per student per day (BR-7), so a
/// student already recorded today says so and cannot be tapped.
class _StudentResult extends StatelessWidget {
  const _StudentResult({required this.student, required this.lateAt, required this.onTap});

  final GateStudent student;
  final DayTime? lateAt;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    return ListTile(
      key: ValueKey('gate-student-${student.id}'),
      minTileHeight: 64,
      onTap: onTap,
      leading: InitialsAvatar(firstName: student.firstName, lastName: student.lastName),
      title: Text(student.fullName, style: theme.textTheme.titleMedium),
      subtitle: Text(
        l.classAndMatricule(student.className, student.matricule),
        style: TextStyle(color: scheme.onSurfaceVariant, fontFeatures: tabular),
      ),
      trailing: lateAt != null
          ? StatusPill(
              label: l.alreadyLateAt(lateAt.toString()),
              background: status.lateContainer,
              foreground: status.onLateContainer,
            )
          : Icon(Icons.add_circle_rounded, color: scheme.primary, size: 30),
    );
  }
}

/// The day's lateness, latest first: who, which class, at what time, and whether it is sent.
/// Shared by the gate and the DM's home.
class LatenessList extends StatelessWidget {
  const LatenessList({super.key, required this.rows});

  final List<GateRow> rows;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    return Card(
      child: Column(
        children: [
          for (final (i, r) in rows.indexed) ...[
            if (i > 0) const Divider(indent: 68),
            Padding(
              key: ValueKey('lateness-${r.id}'),
              padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.m),
              child: Row(
                children: [
                  InitialsAvatar(
                    firstName: r.firstName,
                    lastName: r.lastName,
                    background: status.lateContainer,
                    foreground: status.onLateContainer,
                  ),
                  const SizedBox(width: Gap.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(r.fullName, style: theme.textTheme.titleMedium),
                        if (r.className.isNotEmpty)
                          Text(
                            r.className,
                            style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                          ),
                        if (r.local?.state == SyncState.pending)
                          Padding(
                            padding: const EdgeInsets.only(top: 2),
                            child: Row(
                              children: [
                                Icon(Icons.cloud_upload_outlined, size: 16, color: status.late),
                                const SizedBox(width: Gap.xs),
                                Flexible(
                                  child: Text(
                                    l.latenessWaiting,
                                    style: theme.textTheme.bodySmall?.copyWith(color: status.onLateContainer),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        if (r.justified)
                          Padding(
                            padding: const EdgeInsets.only(top: Gap.xs),
                            child: StatusPill(
                              label: l.justifiedLabel,
                              icon: Icons.verified_outlined,
                              background: status.presentContainer,
                              foreground: status.present,
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: Gap.s),
                  Text(
                    r.arrivalTime.toString(),
                    style: theme.textTheme.titleLarge?.copyWith(color: status.late, fontFeatures: tabular),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// UC-AD1 step 3: the arrival time, now by default, to the minute. Late means after the school's
/// start time (BR-7), and it cannot be in the future; the server checks both again.
class _RecordSheet extends StatefulWidget {
  const _RecordSheet({required this.student, required this.now, required this.startTime});

  final GateStudent student;
  final DateTime now;
  final DayTime? startTime;

  @override
  State<_RecordSheet> createState() => _RecordSheetState();
}

class _RecordSheetState extends State<_RecordSheet> {
  late DayTime _arrival = DayTime.of(widget.now);

  Future<void> _pickTime() async {
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: _arrival.hour, minute: _arrival.minute),
      initialEntryMode: TimePickerEntryMode.input,
      builder: (context, child) =>
          MediaQuery(data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true), child: child!),
    );
    if (picked != null) {
      setState(() => _arrival = DayTime(picked.hour, picked.minute));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final start = widget.startTime;
    final error = start != null && _arrival.compareTo(start) <= 0
        ? l.arrivalNotLate(start.toString())
        : _arrival.compareTo(DayTime.of(widget.now)) > 0
        ? l.arrivalInFuture
        : null;

    return Padding(
      padding: EdgeInsets.fromLTRB(Gap.xl, 0, Gap.xl, Gap.xl + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              InitialsAvatar(firstName: widget.student.firstName, lastName: widget.student.lastName, size: 52),
              const SizedBox(width: Gap.l),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(widget.student.fullName, style: theme.textTheme.titleLarge),
                    Text(
                      l.classAndMatricule(widget.student.className, widget.student.matricule),
                      style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: Gap.xl),
          Text(l.arrivalTimeLabel, style: theme.textTheme.titleSmall?.copyWith(color: scheme.onSurfaceVariant)),
          const SizedBox(height: Gap.s),
          Material(
            color: error == null ? status.lateContainer : status.absentContainer,
            borderRadius: BorderRadius.circular(Radii.l),
            child: InkWell(
              key: const ValueKey('arrival-time'),
              borderRadius: BorderRadius.circular(Radii.l),
              onTap: _pickTime,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: Gap.l, vertical: Gap.m),
                child: Row(
                  children: [
                    Icon(Icons.schedule_rounded, color: error == null ? status.late : status.absent, size: 30),
                    const SizedBox(width: Gap.m),
                    Expanded(
                      child: Text(
                        _arrival.toString(),
                        style: theme.textTheme.displaySmall?.copyWith(
                          fontWeight: FontWeight.w700,
                          color: error == null ? status.onLateContainer : status.absent,
                          fontFeatures: tabular,
                        ),
                      ),
                    ),
                    Text(l.changeTime, style: theme.textTheme.labelLarge?.copyWith(color: scheme.primary)),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: Gap.s),
          Text(
            error ?? l.latenessIndependent,
            style: theme.textTheme.bodySmall?.copyWith(color: error == null ? scheme.onSurfaceVariant : status.absent),
          ),
          const SizedBox(height: Gap.xl),
          FilledButton(
            onPressed: error == null ? () => Navigator.pop(context, _arrival) : null,
            style: FilledButton.styleFrom(backgroundColor: status.late),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.how_to_reg_rounded),
                const SizedBox(width: Gap.s),
                Flexible(child: Text(l.recordLateness, overflow: TextOverflow.ellipsis)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
