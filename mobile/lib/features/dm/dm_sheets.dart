import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import '../teacher/teacher_sessions.dart' show DayTime;
import 'dm.dart';

/// Opens one of the DM's action sheets. It sends the request itself and closes with true once the
/// server has accepted it; a refusal stays on the sheet, in the server's words (NFR-12).
Future<bool> showActionSheet(BuildContext context, Widget sheet) async {
  final done = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) => sheet,
  );
  return done == true;
}

/// The frame of every sheet: a title, the fields, the error if the server refused, and one button.
class _SheetFrame extends StatelessWidget {
  const _SheetFrame({
    required this.title,
    required this.children,
    required this.action,
    required this.busy,
    required this.error,
    required this.onSubmit,
    this.destructive = false,
  });

  final String title;
  final List<Widget> children;
  final String action;
  final bool busy;
  final String? error;

  /// Null while the form is not complete.
  final VoidCallback? onSubmit;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return SingleChildScrollView(
      padding: EdgeInsets.fromLTRB(Gap.xl, 0, Gap.xl, Gap.xl + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(header: true, child: Text(title, style: theme.textTheme.titleLarge)),
          const SizedBox(height: Gap.l),
          ...children,
          if (error case final error?) ...[
            const SizedBox(height: Gap.m),
            Text(
              error,
              key: const ValueKey('sheet-error'),
              style: theme.textTheme.bodyMedium?.copyWith(color: scheme.error),
            ),
          ],
          const SizedBox(height: Gap.xl),
          FilledButton(
            key: const ValueKey('sheet-submit'),
            onPressed: busy ? null : onSubmit,
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
              backgroundColor: destructive ? scheme.error : null,
            ),
            child: busy
                ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                : Text(action),
          ),
        ],
      ),
    );
  }
}

/// Shared by the sheets: runs [request], keeps the sheet open with the error if it fails.
mixin _Submitting<T extends StatefulWidget> on State<T> {
  bool busy = false;
  String? error;

  Future<void> submit(Future<void> Function() request) async {
    final l = AppLocalizations.of(context);
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await request();
      if (mounted) {
        Navigator.pop(context, true);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          busy = false;
          error = failureText(e, l);
        });
      }
    }
  }
}

/// D30: the note of a decision on an alert: the reason to dismiss, the issue to record, or what
/// the counsellor should know (optional).
class DecisionSheet extends StatefulWidget {
  const DecisionSheet({super.key, required this.decision, required this.send});

  final Decision decision;
  final Future<void> Function(String note) send;

  @override
  State<DecisionSheet> createState() => _DecisionSheetState();
}

class _DecisionSheetState extends State<DecisionSheet> with _Submitting {
  final _note = TextEditingController();

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final decision = widget.decision;
    final complete = !decision.needsNote || _note.text.trim().isNotEmpty;
    return _SheetFrame(
      title: decisionLabel(decision, l),
      action: decisionLabel(decision, l),
      busy: busy,
      error: error,
      onSubmit: complete ? () => submit(() => widget.send(_note.text)) : null,
      children: [
        TextField(
          key: const ValueKey('decision-note'),
          controller: _note,
          autofocus: true,
          minLines: 3,
          maxLines: 6,
          maxLength: 500,
          textCapitalization: TextCapitalization.sentences,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: switch (decision) {
              Decision.dismiss => l.noteDismissLabel,
              Decision.disciplinary => l.noteDisciplinaryLabel,
              _ => l.noteReferLabel,
            },
            helperText: decision.needsNote ? l.noteRequired : null,
            alignLabelWithHint: true,
          ),
        ),
      ],
    );
  }
}

/// UC-D3, D31: summons the parents. One tap: the next school day when classes start; or a day
/// and time the DM picks. The reason stays in the DM's records; from an alert it may be left out.
class SummonSheet extends StatefulWidget {
  const SummonSheet({
    super.key,
    required this.studentName,
    required this.reasonRequired,
    required this.now,
    required this.send,
  });

  final String studentName;

  /// False from an alert, which says why (D31).
  final bool reasonRequired;
  final DateTime now;
  final Future<void> Function(Meeting meeting, String reason) send;

  @override
  State<SummonSheet> createState() => _SummonSheetState();
}

class _SummonSheetState extends State<SummonSheet> with _Submitting {
  final _reason = TextEditingController();
  Meeting _meeting = const Meeting();

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  /// D31: a school day within 30 days, between 06:00 and 18:00; the server checks again.
  Future<void> _choose() async {
    final today = DateTime(widget.now.year, widget.now.month, widget.now.day);
    var first = today;
    while (first.weekday > DateTime.friday) {
      first = first.add(const Duration(days: 1));
    }
    final date = await showDatePicker(
      context: context,
      initialDate: _meeting.date ?? first,
      firstDate: today,
      lastDate: today.add(const Duration(days: 30)),
      selectableDayPredicate: (day) => day.weekday <= DateTime.friday,
    );
    if (date == null || !mounted) {
      return;
    }
    final time = await showTimePicker(
      context: context,
      initialTime: switch (_meeting.time) {
        final t? => TimeOfDay(hour: t.hour, minute: t.minute),
        null => const TimeOfDay(hour: 8, minute: 0),
      },
      initialEntryMode: TimePickerEntryMode.input,
      builder: (context, child) =>
          MediaQuery(data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true), child: child!),
    );
    if (time == null || !mounted) {
      return;
    }
    setState(() => _meeting = Meeting(date: date, time: DayTime(time.hour, time.minute)));
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final complete = !widget.reasonRequired || _reason.text.trim().isNotEmpty;
    final chosen = switch (_meeting) {
      Meeting(date: final date?, time: final time?) => l.meetingAt(
        DateFormat.MMMMEEEEd(locale).format(date),
        time.toString(),
      ),
      _ => null,
    };

    Widget option({required Key key, required bool selected, required String title, required VoidCallback onTap}) =>
        Card(
          key: key,
          color: selected ? scheme.primaryContainer : null,
          child: ListTile(
            minVerticalPadding: Gap.m,
            leading: Icon(
              selected ? Icons.radio_button_checked_rounded : Icons.radio_button_off_rounded,
              color: selected ? scheme.primary : scheme.onSurfaceVariant,
            ),
            title: Text(title),
            selected: selected,
            onTap: onTap,
          ),
        );

    return _SheetFrame(
      title: l.summonTitle(widget.studentName),
      action: l.sendSummons,
      busy: busy,
      error: error,
      onSubmit: complete ? () => submit(() => widget.send(_meeting, _reason.text)) : null,
      children: [
        Text(l.meetingLabel, style: theme.textTheme.titleSmall?.copyWith(color: scheme.onSurfaceVariant)),
        const SizedBox(height: Gap.s),
        option(
          key: const ValueKey('meeting-default'),
          selected: _meeting.isDefault,
          title: l.meetingNextSchoolDay,
          onTap: () => setState(() => _meeting = const Meeting()),
        ),
        const SizedBox(height: Gap.s),
        option(
          key: const ValueKey('meeting-choose'),
          selected: !_meeting.isDefault,
          title: chosen ?? l.meetingChoose,
          onTap: _choose,
        ),
        const SizedBox(height: Gap.l),
        TextField(
          key: const ValueKey('summons-reason'),
          controller: _reason,
          minLines: 2,
          maxLines: 4,
          maxLength: 300,
          textCapitalization: TextCapitalization.sentences,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(
            labelText: widget.reasonRequired ? l.summonReasonLabel : l.summonReasonOptional,
            helperText: l.summonReasonHelp,
            helperMaxLines: 2,
            alignLabelWithHint: true,
          ),
        ),
      ],
    );
  }
}

/// BR-11, BR-12, D26: one reason for every selected record. An illness takes no note (NFR-10).
class JustifySheet extends StatefulWidget {
  const JustifySheet({super.key, required this.count, required this.send});

  final int count;
  final Future<void> Function(JustificationReason reason, String note) send;

  @override
  State<JustifySheet> createState() => _JustifySheetState();
}

class _JustifySheetState extends State<JustifySheet> with _Submitting {
  final _note = TextEditingController();
  JustificationReason? _reason;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final reason = _reason;
    return _SheetFrame(
      title: l.justifyCount(widget.count),
      action: l.justify,
      busy: busy,
      error: error,
      onSubmit: reason == null ? null : () => submit(() => widget.send(reason, reason.takesNote ? _note.text : '')),
      children: [
        Text(l.justificationReason, style: theme.textTheme.titleSmall?.copyWith(color: scheme.onSurfaceVariant)),
        const SizedBox(height: Gap.s),
        Wrap(
          spacing: Gap.s,
          runSpacing: Gap.s,
          children: [
            for (final r in JustificationReason.values)
              ChoiceChip(
                key: ValueKey('reason-${r.name}'),
                label: Text(reasonLabel(r, l)),
                selected: reason == r,
                padding: const EdgeInsets.symmetric(horizontal: Gap.s, vertical: Gap.s),
                onSelected: (_) => setState(() => _reason = r),
              ),
          ],
        ),
        const SizedBox(height: Gap.l),
        if (reason == JustificationReason.illness)
          Text(
            l.illnessNoNote,
            key: const ValueKey('illness-no-note'),
            style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
          )
        else
          TextField(
            key: const ValueKey('justification-note'),
            controller: _note,
            minLines: 2,
            maxLines: 4,
            maxLength: 300,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(labelText: l.justificationNote, alignLabelWithHint: true),
          ),
      ],
    );
  }
}

/// BR-13, D27: cancels a record entered by mistake, with the reason.
class CancelRecordSheet extends StatefulWidget {
  const CancelRecordSheet({super.key, required this.what, required this.send});

  /// The record, as the history shows it ("Absence, lesson at 08:25").
  final String what;
  final Future<void> Function(String reason) send;

  @override
  State<CancelRecordSheet> createState() => _CancelRecordSheetState();
}

class _CancelRecordSheetState extends State<CancelRecordSheet> with _Submitting {
  final _reason = TextEditingController();

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return _SheetFrame(
      title: l.cancelRecordTitle,
      action: l.confirmCancelRecord,
      destructive: true,
      busy: busy,
      error: error,
      onSubmit: _reason.text.trim().isEmpty ? null : () => submit(() => widget.send(_reason.text)),
      children: [
        Text(widget.what, style: theme.textTheme.titleMedium),
        const SizedBox(height: Gap.s),
        Text(
          l.cancelRecordBody,
          style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
        ),
        const SizedBox(height: Gap.l),
        TextField(
          key: const ValueKey('cancel-reason'),
          controller: _reason,
          minLines: 2,
          maxLines: 4,
          maxLength: 300,
          textCapitalization: TextCapitalization.sentences,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(labelText: l.cancelReason, alignLabelWithHint: true),
        ),
      ],
    );
  }
}

String decisionLabel(Decision decision, AppLocalizations l) => switch (decision) {
  Decision.summon => l.decisionSummon,
  Decision.dismiss => l.decisionDismiss,
  Decision.disciplinary => l.decisionDisciplinary,
  Decision.refer => l.decisionRefer,
};

String reasonLabel(JustificationReason reason, AppLocalizations l) => switch (reason) {
  JustificationReason.illness => l.reasonIllness,
  JustificationReason.family => l.reasonFamily,
  JustificationReason.schoolActivity => l.reasonSchoolActivity,
  JustificationReason.other => l.reasonOther,
};
