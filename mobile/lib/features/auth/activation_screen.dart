import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/api/api_failure.dart';
import '../../core/theme.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import 'auth_api.dart';
import 'auth_controller.dart';
import 'failure_text.dart';
import 'login_screen.dart' show loginSchoolsProvider;

/// The nine digits of a Cameroon mobile number, from what the parent typed ("6 99 00 00 01",
/// "+237699000001", "00237…"); null when it is not one (D15). The server checks it again.
String? cameroonDigits(String typed) {
  var digits = typed.replaceAll(RegExp(r'\D'), '');
  if (digits.startsWith('00237')) {
    digits = digits.substring(5);
  } else if (digits.startsWith('237') && digits.length == 12) {
    digits = digits.substring(3);
  }
  return RegExp(r'^\d{9}$').hasMatch(digits) ? digits : null;
}

/// UC-P1: a parent activates their account. Step 1, the invitation code the school sent by SMS
/// and their phone number; step 2, the one-time code sent to that phone. Then they are signed in
/// and land on their children. With [signIn] (D24), a parent who already activated signs in again:
/// step 1 is their school and phone. The phone stays in request bodies, never in a URL (NFR-9).
class ActivationScreen extends ConsumerStatefulWidget {
  const ActivationScreen({super.key, this.signIn = false});

  final bool signIn;

  @override
  ConsumerState<ActivationScreen> createState() => _ActivationScreenState();
}

class _ActivationScreenState extends ConsumerState<ActivationScreen> {
  final _codeForm = GlobalKey<FormState>();
  final _otpForm = GlobalKey<FormState>();
  final _code = TextEditingController();
  final _phone = TextEditingController();
  final _otp = TextEditingController();

  /// Set once the one-time code is sent: step 2.
  ActivationStarted? _started;
  String? _sentTo;
  Object? _error;
  bool _busy = false;
  bool _resent = false;

  /// D24: the school picked, when it differs from the remembered one.
  String? _schoolId;

  @override
  void dispose() {
    _code.dispose();
    _phone.dispose();
    _otp.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
    } on ApiFailure catch (e) {
      if (mounted) {
        setState(() => _error = e);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  /// Step 1, and "Send a new code" in step 2: the server replaces the one-time code.
  Future<void> _sendCode({bool again = false}) async {
    if (!again && !_codeForm.currentState!.validate()) {
      return;
    }
    final digits = cameroonDigits(_phone.text)!;
    final api = ref.read(authApiProvider);
    await _run(() async {
      final started = widget.signIn
          ? await api.parentSignIn(schoolId: _school()!, phone: '+237$digits')
          : await api.activateParent(code: _code.text.trim().toUpperCase(), phone: '+237$digits');
      setState(() {
        _started = started;
        _sentTo =
            '+237 ${digits.substring(0, 3)} ${digits.substring(3, 5)} ${digits.substring(5, 7)} '
            '${digits.substring(7)}';
        _resent = again;
        _otp.clear();
      });
    });
  }

  String? _school() => _schoolId ?? ref.read(loginSchoolsProvider).value?.$2;

  Future<void> _verify() async {
    if (!_otpForm.currentState!.validate()) {
      return;
    }
    // signed in: the router takes the parent to their children
    await _run(
      () => ref
          .read(authControllerProvider.notifier)
          .verifyParent(activationId: _started!.activationId, otp: _otp.text, signIn: widget.signIn),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final step2 = _started != null;

    return PopScope(
      // back from step 2 returns to step 1, not to sign-in
      canPop: !step2,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          setState(() {
            _started = null;
            _error = null;
          });
        }
      },
      child: Scaffold(
        appBar: AppBar(title: Text(widget.signIn ? l.parentSignInTitle : l.activationTitle)),
        body: ListView(
          padding: const EdgeInsets.only(bottom: Gap.xl),
          children: [
            HeaderBand(
              children: [
                Text(
                  l.activationStep(step2 ? 2 : 1),
                  style: theme.textTheme.labelLarge?.copyWith(color: status.onHeaderMuted),
                ),
                const SizedBox(height: Gap.xs),
                Text(
                  step2
                      ? l.otpTitle
                      : widget.signIn
                      ? l.parentSignInHeadline
                      : l.activationHeadline,
                  style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader),
                ),
                const SizedBox(height: Gap.s),
                Text(
                  step2
                      ? (widget.signIn ? l.otpSentIfAccount(_sentTo!) : l.otpSentTo(_sentTo!))
                      : widget.signIn
                      ? l.parentSignInIntro
                      : l.activationIntro,
                  style: theme.textTheme.bodyMedium?.copyWith(color: status.onHeaderMuted),
                ),
                const SizedBox(height: Gap.m),
                _Steps(current: step2 ? 2 : 1),
              ],
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(Gap.l, Gap.xl, Gap.l, 0),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 480),
                child: step2 ? _otpStep(context) : _codeStep(context),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _errorText(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: Gap.l),
      child: InfoBanner(
        icon: Icons.error_outline_rounded,
        text: failureText(_error!, l),
        background: StatusColors.of(context).absentContainer,
        foreground: const Color(0xFF7A1212),
      ),
    );
  }

  Widget _codeStep(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Form(
      key: _codeForm,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (widget.signIn)
            _SchoolPicker(selected: _schoolId, onChanged: (id) => setState(() => _schoolId = id))
          else
            TextFormField(
              key: const ValueKey('invitation-code'),
              controller: _code,
              decoration: InputDecoration(
                labelText: l.invitationCodeLabel,
                helperText: l.invitationCodeHelp,
                prefixIcon: const Icon(Icons.key_rounded),
              ),
              textCapitalization: TextCapitalization.characters,
              autocorrect: false,
              enableSuggestions: false,
              textInputAction: TextInputAction.next,
              style: const TextStyle(fontSize: 18, letterSpacing: 1.5, fontWeight: FontWeight.w600),
              validator: (value) => value == null || value.trim().isEmpty ? l.fieldRequired : null,
            ),
          const SizedBox(height: Gap.l),
          TextFormField(
            key: const ValueKey('phone'),
            controller: _phone,
            decoration: InputDecoration(
              labelText: l.phoneLabel,
              helperText: l.phoneHelp,
              prefixIcon: const Icon(Icons.phone_android_rounded),
              prefixText: '+237 ',
            ),
            keyboardType: TextInputType.phone,
            autofillHints: const [AutofillHints.telephoneNumberNational],
            textInputAction: TextInputAction.done,
            inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9 +]'))],
            style: const TextStyle(fontSize: 18, fontFeatures: tabular),
            onFieldSubmitted: (_) => _busy ? null : _sendCode(),
            validator: (value) => value == null || value.trim().isEmpty
                ? l.fieldRequired
                : cameroonDigits(value) == null
                ? l.phoneInvalid
                : null,
          ),
          if (_error != null) _errorText(context),
          const SizedBox(height: Gap.xl),
          FilledButton(
            onPressed: _busy ? null : _sendCode,
            child: _busy
                ? const SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                : Text(l.sendSmsCode),
          ),
        ],
      ),
    );
  }

  Widget _otpStep(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    return Form(
      key: _otpForm,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            key: const ValueKey('otp'),
            controller: _otp,
            autofocus: true,
            textAlign: TextAlign.center,
            keyboardType: TextInputType.number,
            autofillHints: const [AutofillHints.oneTimeCode],
            inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
            style: theme.textTheme.headlineMedium?.copyWith(letterSpacing: 12, fontFeatures: tabular),
            decoration: InputDecoration(labelText: l.otpLabel, hintText: '000000', counterText: ''),
            onFieldSubmitted: (_) => _busy ? null : _verify(),
            validator: (value) => value == null || value.length != 6 ? l.otpInvalid : null,
          ),
          const SizedBox(height: Gap.s),
          Text(
            _resent
                ? l.otpResent(DateFormat.Hm(locale).format(_started!.expiresAt))
                : l.otpValidUntil(DateFormat.Hm(locale).format(_started!.expiresAt)),
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          if (_error != null) _errorText(context),
          const SizedBox(height: Gap.xl),
          FilledButton(
            onPressed: _busy ? null : _verify,
            child: _busy
                ? const SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                : Text(l.activateAccount),
          ),
          const SizedBox(height: Gap.m),
          Row(
            children: [
              Expanded(
                child: TextButton(
                  onPressed: _busy ? null : () => _sendCode(again: true),
                  child: Text(l.resendCode, textAlign: TextAlign.center),
                ),
              ),
              Expanded(
                child: TextButton(
                  onPressed: _busy
                      ? null
                      : () => setState(() {
                          _started = null;
                          _error = null;
                        }),
                  child: Text(l.changeNumber, textAlign: TextAlign.center),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// Two bars on the header band: where the parent is in the activation.
class _Steps extends StatelessWidget {
  const _Steps({required this.current});

  final int current;

  @override
  Widget build(BuildContext context) {
    final status = StatusColors.of(context);
    return ExcludeSemantics(
      child: Row(
        children: [
          for (var i = 1; i <= 2; i++) ...[
            if (i > 1) const SizedBox(width: Gap.s),
            Expanded(
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                height: 6,
                decoration: BoxDecoration(
                  color: i <= current ? status.onHeader : status.onHeader.withValues(alpha: 0.18),
                  borderRadius: BorderRadius.circular(99),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// D24: the parent's school (D15: a phone may have an account in each school), the one remembered
/// on this phone first.
class _SchoolPicker extends ConsumerWidget {
  const _SchoolPicker({required this.selected, required this.onChanged});

  final String? selected;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return switch (ref.watch(loginSchoolsProvider)) {
      AsyncData(value: (final list, final initial)) => DropdownButtonFormField<String>(
        key: const ValueKey('school'),
        initialValue: selected ?? initial,
        isExpanded: true,
        decoration: InputDecoration(labelText: l.schoolLabel, prefixIcon: const Icon(Icons.school_outlined)),
        items: [for (final school in list) DropdownMenuItem(value: school.id, child: Text(school.name))],
        onChanged: onChanged,
        validator: (value) => value == null ? l.fieldRequired : null,
      ),
      AsyncError() => Row(
        children: [
          Expanded(child: Text(l.schoolsLoadFailed)),
          TextButton(onPressed: () => ref.invalidate(loginSchoolsProvider), child: Text(l.retry)),
        ],
      ),
      _ => const LinearProgressIndicator(),
    };
  }
}
