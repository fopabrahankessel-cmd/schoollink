import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/theme.dart';
import '../../../core/ui/skeleton.dart';
import '../../../core/ui/widgets.dart';
import '../../../l10n/app_localizations.dart';
import '../../auth/failure_text.dart';
import '../dm.dart';
import 'settings_models.dart';

/// UC-D7, D28, D41: the alert rules, the start of school and the SMS digest times, as on the web.
/// They apply to the whole school. A time out of bounds or a second digest not after the first
/// blocks saving, with the reason under the field.
class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.settingsTitle)),
      body: switch (ref.watch(settingsProvider)) {
        AsyncValue(value: final draft?) => _Form(initial: draft),
        AsyncError(:final error) => FailureView(
          message: failureText(error, l),
          onRetry: () => ref.invalidate(settingsProvider),
        ),
        _ => const Padding(
          padding: EdgeInsets.all(Gap.l),
          child: Skeleton(child: SkeletonRows(count: 6)),
        ),
      },
    );
  }
}

class _Form extends ConsumerStatefulWidget {
  const _Form({required this.initial});

  final SettingsDraft initial;

  @override
  ConsumerState<_Form> createState() => _FormState();
}

class _FormState extends ConsumerState<_Form> {
  late SettingsDraft _draft = widget.initial;
  bool _saving = false;
  String? _error;

  Future<void> _pickTime(String current, void Function(String) onPicked) async {
    final parts = current.split(':');
    final picked = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: int.parse(parts[0]), minute: int.parse(parts[1])),
      builder: (context, child) =>
          MediaQuery(data: MediaQuery.of(context).copyWith(alwaysUse24HourFormat: true), child: child!),
    );
    if (picked != null && mounted) {
      final hm = '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
      setState(() => onPicked(hm));
    }
  }

  void _setRule(int index, RuleSetting rule) =>
      setState(() => _draft = _draft.copyWith(rules: [...?_rulesWith(index, rule)]));

  List<RuleSetting>? _rulesWith(int index, RuleSetting rule) => [
    for (final (i, r) in _draft.rules.indexed) i == index ? rule : r,
  ];

  void _setDigests(List<String> digests) => _draft = _draft.copyWith(digestTimes: digests);

  Future<void> _save() async {
    final l = AppLocalizations.of(context);
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(dmRepositoryProvider).saveSettings(_draft);
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(l.settingsSaved)));
    } catch (error) {
      if (mounted) {
        setState(() => _error = failureText(error, l));
      }
    } finally {
      if (mounted) {
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final draft = _draft;
    String range(({String min, String max}) bounds) => l.settingsTimeRange(bounds.min, bounds.max);

    // a short form: built whole, so a field below the fold keeps its state
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l.settingsIntro, style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant)),
          if (_error case final error?) ...[
            const SizedBox(height: Gap.m),
            InfoBanner(
              icon: Icons.error_outline_rounded,
              text: error,
              background: scheme.errorContainer,
              foreground: scheme.onErrorContainer,
            ),
          ],
          SectionHeader(title: l.settingsRulesTitle),
          Text(l.settingsRulesIntro, style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
          const SizedBox(height: Gap.s),
          for (final (i, rule) in draft.rules.indexed)
            Padding(
              padding: const EdgeInsets.only(bottom: Gap.m),
              child: Card(
                margin: EdgeInsets.zero,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.s, Gap.m),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              _ruleName(rule.rule, l).isEmpty ? rule.type : _ruleName(rule.rule, l),
                              style: theme.textTheme.titleMedium,
                            ),
                          ),
                          Switch(
                            key: ValueKey('rule-active-${rule.type}'),
                            value: rule.active,
                            onChanged: (on) => _setRule(i, rule.copyWith(active: on)),
                          ),
                        ],
                      ),
                      Padding(
                        padding: const EdgeInsets.only(right: Gap.s),
                        child: Text(_ruleHelp(rule.rule, rule.count, l), style: theme.textTheme.bodyMedium),
                      ),
                      const SizedBox(height: Gap.s),
                      Row(
                        children: [
                          // a count the server holds out of its bounds blocks Save: say so, here
                          if (rule.count < rule.min || rule.count > rule.max) ...[
                            Icon(
                              Icons.error_outline_rounded,
                              key: ValueKey('rule-out-of-range-${rule.type}'),
                              size: 18,
                              color: scheme.error,
                            ),
                            const SizedBox(width: Gap.xs),
                          ],
                          Expanded(
                            child: Text(
                              '${_ruleCountLabel(rule.rule, l)} · ${l.settingsCountRange(rule.min, rule.max)}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: rule.count < rule.min || rule.count > rule.max
                                    ? scheme.error
                                    : scheme.onSurfaceVariant,
                              ),
                            ),
                          ),
                          IconButton.outlined(
                            key: ValueKey('rule-minus-${rule.type}'),
                            tooltip: l.settingsLess,
                            // an inactive rule's count moves only back into its bounds
                            onPressed: (rule.active || rule.count > rule.max) && rule.count > rule.min
                                ? () => _setRule(i, rule.copyWith(count: rule.count - 1))
                                : null,
                            icon: const Icon(Icons.remove_rounded),
                          ),
                          SizedBox(
                            width: 40,
                            child: Text(
                              '${rule.count}',
                              textAlign: TextAlign.center,
                              style: theme.textTheme.titleMedium?.copyWith(fontFeatures: tabular),
                            ),
                          ),
                          IconButton.outlined(
                            key: ValueKey('rule-plus-${rule.type}'),
                            tooltip: l.settingsMore,
                            onPressed: (rule.active || rule.count < rule.min) && rule.count < rule.max
                                ? () => _setRule(i, rule.copyWith(count: rule.count + 1))
                                : null,
                            icon: const Icon(Icons.add_rounded),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
          SectionHeader(title: l.settingsTimesTitle),
          Text(l.settingsTimesIntro, style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
          const SizedBox(height: Gap.s),
          Card(
            margin: EdgeInsets.zero,
            child: Column(
              children: [
                _TimeRow(
                  key: const ValueKey('settings-start'),
                  label: l.settingsStartTime,
                  time: draft.startTime,
                  help: draft.startOutOfBounds ? range(SettingsDraft.startBounds) : l.settingsStartTimeHelp,
                  error: draft.startOutOfBounds,
                  onTap: () => _pickTime(draft.startTime, (t) => _draft = _draft.copyWith(startTime: t)),
                ),
                for (final (i, time) in draft.digestTimes.indexed) ...[
                  const Divider(height: 1),
                  _TimeRow(
                    key: ValueKey('settings-digest-$i'),
                    label: l.settingsDigest(i + 1),
                    time: time,
                    help: draft.digestOutOfBounds(i)
                        ? range(SettingsDraft.digestBounds)
                        : draft.digestOutOfOrder(i)
                        ? l.settingsDigestOrder
                        : null,
                    error: draft.digestOutOfBounds(i) || draft.digestOutOfOrder(i),
                    onTap: () => _pickTime(
                      time,
                      (t) => _setDigests([for (final (j, d) in _draft.digestTimes.indexed) j == i ? t : d]),
                    ),
                    trailing: i == 1
                        ? TextButton(
                            key: const ValueKey('settings-remove-digest'),
                            onPressed: () => setState(() => _setDigests(_draft.digestTimes.take(1).toList())),
                            child: Text(l.settingsRemoveDigest),
                          )
                        : null,
                  ),
                ],
              ],
            ),
          ),
          if (draft.digestTimes.length == 1)
            Padding(
              padding: const EdgeInsets.only(top: Gap.s),
              child: Align(
                alignment: Alignment.centerLeft,
                child: OutlinedButton.icon(
                  key: const ValueKey('settings-add-digest'),
                  onPressed: () => setState(() => _setDigests([..._draft.digestTimes, SettingsDraft.secondDigest])),
                  icon: const Icon(Icons.add_rounded),
                  label: Text(l.settingsAddDigest),
                  style: OutlinedButton.styleFrom(minimumSize: const Size(0, 48)),
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.only(top: Gap.s),
            child: Text(
              l.settingsDigestHelp,
              style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
            ),
          ),
          const SizedBox(height: Gap.l),
          FilledButton(
            key: const ValueKey('settings-save'),
            onPressed: draft.valid && !_saving ? _save : null,
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            child: _saving
                ? const SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                : Text(l.save),
          ),
        ],
      ),
    );
  }
}

class _TimeRow extends StatelessWidget {
  const _TimeRow({
    super.key,
    required this.label,
    required this.time,
    required this.onTap,
    this.help,
    this.error = false,
    this.trailing,
  });

  final String label;
  final String time;
  final String? help;
  final bool error;
  final VoidCallback onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return ListTile(
      minTileHeight: 64,
      onTap: onTap,
      leading: Icon(Icons.schedule_rounded, color: error ? scheme.error : scheme.primary),
      title: Text(label),
      subtitle: help == null
          ? null
          : Text(help!, style: TextStyle(color: error ? scheme.error : scheme.onSurfaceVariant)),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          ?trailing,
          Text(
            time,
            style: theme.textTheme.titleLarge?.copyWith(
              fontFeatures: tabular,
              color: error ? scheme.error : scheme.onSurface,
            ),
          ),
        ],
      ),
    );
  }
}

String _ruleName(AlertRule rule, AppLocalizations l) => switch (rule) {
  AlertRule.fullDayAbsence => l.settingsRuleFullDay,
  AlertRule.consecutiveLateness => l.settingsRuleLateness,
  AlertRule.unjustifiedAbsences => l.settingsRuleUnjustified,
  AlertRule.other => '',
};

String _ruleHelp(AlertRule rule, int count, AppLocalizations l) => switch (rule) {
  AlertRule.fullDayAbsence => l.settingsHelpFullDay(count),
  AlertRule.consecutiveLateness => l.settingsHelpLateness(count),
  AlertRule.unjustifiedAbsences => l.settingsHelpUnjustified(count),
  // a rule this version does not know: its switch and count still work, unexplained
  AlertRule.other => '',
};

String _ruleCountLabel(AlertRule rule, AppLocalizations l) => switch (rule) {
  AlertRule.fullDayAbsence => l.settingsCountFullDay,
  AlertRule.consecutiveLateness => l.settingsCountLateness,
  AlertRule.unjustifiedAbsences => l.settingsCountUnjustified,
  AlertRule.other => '',
};
