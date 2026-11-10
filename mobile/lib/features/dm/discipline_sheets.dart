import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import '../../l10n/app_localizations.dart';
import 'discipline.dart';
import 'dm_sheets.dart';

/// D37: the date of an incident is at most this many days before today.
const incidentDaysBack = 30;

/// UC-D5, D37: the kind of incident, one chip per category, all visible at once so the DM scans
/// them instead of opening a list.
class CategoryChips extends StatelessWidget {
  const CategoryChips({super.key, required this.selected, required this.onSelected});

  final DisciplinaryCategory? selected;
  final ValueChanged<DisciplinaryCategory> onSelected;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l.incidentCategory, style: theme.textTheme.labelLarge),
        const SizedBox(height: Gap.s),
        Wrap(
          spacing: Gap.s,
          runSpacing: Gap.s,
          children: [
            for (final category in DisciplinaryCategory.values)
              ChoiceChip(
                label: Text(categoryLabel(category, l)),
                selected: category == selected,
                onSelected: (_) => onSelected(category),
              ),
          ],
        ),
      ],
    );
  }
}

/// UC-D5, D37: what the DM decided; "None" until they choose.
class MeasureField extends StatelessWidget {
  const MeasureField({super.key, required this.measure, required this.onChanged});

  final DisciplinaryMeasure measure;
  final ValueChanged<DisciplinaryMeasure> onChanged;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return DropdownButtonFormField<DisciplinaryMeasure>(
      key: const ValueKey('incident-measure'),
      initialValue: measure,
      isExpanded: true,
      decoration: InputDecoration(labelText: l.incidentMeasure),
      items: [
        for (final m in DisciplinaryMeasure.values) DropdownMenuItem(value: m, child: Text(measureLabel(m, l))),
      ],
      onChanged: (m) => onChanged(m ?? DisciplinaryMeasure.none),
    );
  }
}

/// UC-D5, D37: records an incident for one student: the kind, the day (today by default, at most
/// 30 days back), the measure and what happened. Nothing is sent to the parents.
class RecordIncidentSheet extends StatefulWidget {
  const RecordIncidentSheet({super.key, required this.today, required this.send});

  /// The school day the sheet opens on (date only).
  final DateTime today;
  final Future<void> Function(
    DisciplinaryCategory category,
    DisciplinaryMeasure measure,
    DateTime occurredOn,
    String description,
  )
  send;

  @override
  State<RecordIncidentSheet> createState() => _RecordIncidentSheetState();
}

class _RecordIncidentSheetState extends State<RecordIncidentSheet> with Submitting {
  final _description = TextEditingController();
  DisciplinaryCategory? _category;
  DisciplinaryMeasure _measure = DisciplinaryMeasure.none;
  late DateTime _day = widget.today;

  @override
  void dispose() {
    _description.dispose();
    super.dispose();
  }

  Future<void> _pickDay() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _day,
      firstDate: widget.today.subtract(const Duration(days: incidentDaysBack)),
      lastDate: widget.today,
    );
    if (picked != null) {
      setState(() => _day = picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final category = _category;
    final complete = category != null && _description.text.trim().isNotEmpty;
    return SheetFrame(
      title: l.recordIncident,
      action: l.recordIncident,
      busy: busy,
      error: error,
      onSubmit: complete ? () => submit(() => widget.send(category, _measure, _day, _description.text)) : null,
      children: [
        CategoryChips(selected: _category, onSelected: (c) => setState(() => _category = c)),
        const SizedBox(height: Gap.l),
        OutlinedButton.icon(
          key: const ValueKey('incident-date'),
          onPressed: _pickDay,
          icon: const Icon(Icons.event_outlined),
          label: Text(l.incidentDateOn(DateFormat.MMMMEEEEd(locale).format(_day))),
          style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48), alignment: Alignment.centerLeft),
        ),
        const SizedBox(height: Gap.l),
        MeasureField(measure: _measure, onChanged: (m) => setState(() => _measure = m)),
        const SizedBox(height: Gap.l),
        TextField(
          key: const ValueKey('incident-description'),
          controller: _description,
          minLines: 3,
          maxLines: 6,
          maxLength: 500,
          textCapitalization: TextCapitalization.sentences,
          onChanged: (_) => setState(() {}),
          decoration: InputDecoration(labelText: l.incidentDescription, alignLabelWithHint: true),
        ),
      ],
    );
  }
}

/// D37: closes an incident once, with an optional note.
class CloseIncidentSheet extends StatefulWidget {
  const CloseIncidentSheet({super.key, required this.what, required this.send});

  /// The incident, as the list shows it.
  final String what;
  final Future<void> Function(String note) send;

  @override
  State<CloseIncidentSheet> createState() => _CloseIncidentSheetState();
}

class _CloseIncidentSheetState extends State<CloseIncidentSheet> with Submitting {
  final _note = TextEditingController();

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return SheetFrame(
      title: l.closeIncident,
      action: l.closeIncident,
      busy: busy,
      error: error,
      onSubmit: () => submit(() => widget.send(_note.text)),
      children: [
        Text(widget.what, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: Gap.l),
        TextField(
          key: const ValueKey('close-note'),
          controller: _note,
          minLines: 2,
          maxLines: 4,
          maxLength: 500,
          textCapitalization: TextCapitalization.sentences,
          decoration: InputDecoration(labelText: l.closeNote, alignLabelWithHint: true),
        ),
      ],
    );
  }
}
