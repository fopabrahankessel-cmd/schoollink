import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';

import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'dm.dart';
import 'messages.dart';

/// The longest text, as the server allows (D38).
const messageMaxLength = 480;

/// UC-D4, D38: the DM writes to the guardians of one student ([studentId]) or of a class they
/// choose. A preview, asked 400 ms after the last change, says who gets it and what the SMS cost;
/// sending asks for confirmation. The id is made once here, so a double tap or a retry sends once.
class MessageComposerScreen extends ConsumerStatefulWidget {
  const MessageComposerScreen({super.key, this.studentId, this.studentName});

  final String? studentId;
  final String? studentName;

  @override
  ConsumerState<MessageComposerScreen> createState() => _MessageComposerScreenState();
}

class _MessageComposerScreenState extends ConsumerState<MessageComposerScreen> {
  final String _id = const Uuid().v4();
  final _body = TextEditingController();
  String? _classId;

  /// D38: on by default for one student, off for a class.
  late bool _sms = widget.studentId != null;
  bool _plain = false;
  Timer? _debounce;
  int _asked = 0;
  MessagePreview? _preview;
  bool _previewing = false;
  Object? _previewError;
  bool _sending = false;

  bool get _hasTarget => widget.studentId != null || _classId != null;
  bool get _hasText => _body.text.trim().isNotEmpty;

  MessageDraft get _draft => MessageDraft(
    id: _id,
    studentId: widget.studentId,
    classId: widget.studentId == null ? _classId : null,
    body: _body.text,
    sms: _sms,
    plainSms: _plain,
  );

  @override
  void dispose() {
    _debounce?.cancel();
    _body.dispose();
    super.dispose();
  }

  /// Any change: the preview is asked again once the DM pauses.
  void _changed() {
    _debounce?.cancel();
    setState(() {
      if (!_hasTarget || !_hasText) {
        _asked++; // an answer still on its way is no longer wanted
        _preview = null;
        _previewing = false;
        _previewError = null;
      }
    });
    if (_hasTarget && _hasText) {
      _debounce = Timer(const Duration(milliseconds: 400), _askPreview);
    }
  }

  Future<void> _askPreview() async {
    final asked = ++_asked;
    setState(() => _previewing = true);
    try {
      final preview = await ref.read(dmRepositoryProvider).previewMessage(_draft);
      if (mounted && asked == _asked) {
        setState(() {
          _preview = preview;
          _previewError = null;
          _previewing = false;
        });
      }
    } catch (e) {
      if (mounted && asked == _asked) {
        setState(() {
          _previewError = e;
          _previewing = false;
        });
      }
    }
  }

  bool get _canSend {
    final preview = _preview;
    return !_sending &&
        _hasTarget &&
        _hasText &&
        preview != null &&
        !preview.overCap &&
        preview.byApp + preview.bySms > 0;
  }

  Future<void> _send() async {
    final l = AppLocalizations.of(context);
    final preview = _preview!;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l.messageConfirmTitle),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(_countsText(preview, l)),
            if (preview.bySms > 0) ...[
              const SizedBox(height: Gap.xs),
              Text(l.messageCost(_money(preview.estimatedCost, locale), _money(preview.smsLeft, locale))),
            ],
            const SizedBox(height: Gap.m),
            Text(l.messageConfirmBody),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l.cancel)),
          FilledButton(
            key: const ValueKey('message-confirm'),
            onPressed: () => Navigator.pop(context, true),
            child: Text(l.messageSend),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted || _sending) {
      return;
    }
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    setState(() => _sending = true);
    try {
      await ref.read(dmRepositoryProvider).sendMessage(_draft);
      ref.invalidate(sentMessagesProvider);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(l.messageSent)));
      navigator.pop();
    } catch (e) {
      if (!mounted) return;
      setState(() => _sending = false);
      messenger
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(failureText(e, l))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
          icon: const Icon(Icons.close_rounded),
          onPressed: () => Navigator.of(context).maybePop(),
        ),
        title: Text(l.writeToParents),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, Gap.xl),
        children: [
          if (widget.studentId != null)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: Chip(avatar: const Icon(Icons.person_outline_rounded), label: Text(widget.studentName ?? '')),
            )
          else
            _ClassField(
              value: _classId,
              onChanged: (id) {
                _classId = id;
                _changed();
              },
            ),
          const SizedBox(height: Gap.l),
          TextField(
            key: const ValueKey('message-body'),
            controller: _body,
            maxLength: messageMaxLength,
            minLines: 4,
            maxLines: 10,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(labelText: l.messageBody, alignLabelWithHint: true),
            onChanged: (_) => _changed(),
          ),
          SwitchListTile(
            key: const ValueKey('message-sms'),
            contentPadding: EdgeInsets.zero,
            value: _sms,
            title: Text(l.messageSms),
            subtitle: Text(l.messageSmsHint),
            onChanged: (value) {
              _sms = value;
              _changed();
            },
          ),
          const SizedBox(height: Gap.s),
          _PreviewCard(
            hasTarget: _hasTarget,
            hasText: _hasText,
            preview: _preview,
            loading: _previewing,
            error: _previewError,
            plain: _plain,
            onPlain: (value) {
              _plain = value;
              _changed();
            },
            onRetry: _askPreview,
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.s, Gap.l, Gap.m),
          child: FilledButton.icon(
            key: const ValueKey('message-send'),
            onPressed: _canSend ? _send : null,
            icon: _sending
                ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.send_rounded),
            label: Text(l.messageSend),
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
          ),
        ),
      ),
    );
  }
}

String _money(int amount, String locale) => NumberFormat.decimalPattern(locale).format(amount);

/// "4 parents : 2 par l'app, 2 sans l'app", or "… 2 par SMS" when SMS reach the others.
String _countsText(MessagePreview preview, AppLocalizations l) => preview.bySms > 0
    ? l.messageCountsSms(preview.recipients, preview.byApp, preview.bySms)
    : l.messageCounts(preview.recipients, preview.byApp, preview.unreached);

/// D38: the classes of the DM's blocks.
class _ClassField extends ConsumerWidget {
  const _ClassField({required this.value, required this.onChanged});

  final String? value;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return switch (ref.watch(messageClassesProvider)) {
      AsyncValue(value: final classes?) => DropdownButtonFormField<String>(
        key: const ValueKey('message-class'),
        initialValue: value,
        isExpanded: true,
        decoration: InputDecoration(labelText: l.messageClass, prefixIcon: const Icon(Icons.groups_outlined)),
        items: [for (final option in classes) DropdownMenuItem(value: option.classId, child: Text(option.className))],
        onChanged: onChanged,
      ),
      AsyncError(:final error) => FailureView(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(messageClassesProvider),
      ),
      _ => const LinearProgressIndicator(),
    };
  }
}

/// Who gets the message and what it costs, before anything is sent.
class _PreviewCard extends StatelessWidget {
  const _PreviewCard({
    required this.hasTarget,
    required this.hasText,
    required this.preview,
    required this.loading,
    required this.error,
    required this.plain,
    required this.onPlain,
    required this.onRetry,
  });

  final bool hasTarget;
  final bool hasText;
  final MessagePreview? preview;
  final bool loading;
  final Object? error;
  final bool plain;
  final ValueChanged<bool> onPlain;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final muted = theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final preview = this.preview;

    final List<Widget> children;
    if (!hasTarget) {
      children = [Text(l.messageChooseClass, style: muted)];
    } else if (!hasText) {
      children = [Text(l.messagePreviewHint, style: muted)];
    } else if (error != null && preview == null) {
      children = [FailureView(message: failureText(error!, l), onRetry: onRetry)];
    } else if (preview == null) {
      children = [const LinearProgressIndicator()];
    } else {
      children = [
        Row(
          children: [
            Icon(Icons.groups_outlined, color: scheme.primary),
            const SizedBox(width: Gap.m),
            Expanded(child: Text(_countsText(preview, l), style: theme.textTheme.titleSmall)),
          ],
        ),
        if (preview.bySms > 0) ...[
          const SizedBox(height: Gap.s),
          Text(
            l.messageCost(_money(preview.estimatedCost, locale), _money(preview.smsLeft, locale)),
            style: theme.textTheme.bodyMedium,
          ),
          Text(l.messageUnits(preview.smsUnits), style: muted),
        ],
        if (preview.overCap) ...[
          const SizedBox(height: Gap.m),
          InfoBanner(
            icon: Icons.block_rounded,
            text: l.messageOverCap(preview.smsLeft),
            background: status.absentContainer,
            foreground: status.absent,
          ),
        ],
        if (preview.bySms > 0 && preview.smsUnicode) ...[
          const SizedBox(height: Gap.m),
          Text(l.messageUnicode, style: muted),
        ],
        if (preview.bySms > 0 && (preview.smsUnicode || plain))
          SwitchListTile(
            key: const ValueKey('message-plain'),
            contentPadding: EdgeInsets.zero,
            value: plain,
            title: Text(l.messagePlain),
            subtitle: Text(l.messagePlainHint),
            onChanged: onPlain,
          ),
        // no parent gets an SMS: nothing to show or pay for
        if (preview.smsSample case final sample? when preview.bySms > 0) ...[
          const SizedBox(height: Gap.s),
          Text(l.messageSmsSample, style: theme.textTheme.labelLarge),
          const SizedBox(height: Gap.xs),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(Gap.m),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(Radii.m),
            ),
            child: Text(sample, style: theme.textTheme.bodyMedium),
          ),
        ],
      ];
    }
    return Card(
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // a newer preview is on its way: the one shown may be a little behind
          SizedBox(height: 3, child: loading && preview != null ? const LinearProgressIndicator() : null),
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.l),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
          ),
        ],
      ),
    );
  }
}
