import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api/api_failure.dart';
import '../../l10n/app_localizations.dart';
import 'auth_controller.dart';
import 'failure_text.dart';

/// D18: after the first sign-in (or a reset) the temporary password must be replaced before
/// anything else. The server allows 8 to 72 characters (NFR-7: BCrypt reads 72 bytes).
class ChangePasswordScreen extends ConsumerStatefulWidget {
  const ChangePasswordScreen({super.key});

  @override
  ConsumerState<ChangePasswordScreen> createState() => _ChangePasswordScreenState();
}

class _ChangePasswordScreenState extends ConsumerState<ChangePasswordScreen> {
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _new = TextEditingController();
  final _confirm = TextEditingController();
  bool _busy = false;
  Object? _error;

  @override
  void dispose() {
    _current.dispose();
    _new.dispose();
    _confirm.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy || !_form.currentState!.validate()) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(authControllerProvider.notifier)
          .changePassword(currentPassword: _current.text, newPassword: _new.text);
    } catch (e) {
      _error = e;
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    String? errorOn(String field) => switch (_error) {
      ApiFailure(field: final f, :final message) when f == field => message,
      _ => null,
    };
    final fieldErrors = errorOn('currentPassword') != null || errorOn('newPassword') != null;
    final generalError = _error != null && !fieldErrors ? failureText(_error!, l) : null;

    return Scaffold(
      appBar: AppBar(
        title: Text(l.changePasswordTitle),
        actions: [
          TextButton(onPressed: () => ref.read(authControllerProvider.notifier).signOut(), child: Text(l.signOut)),
        ],
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Form(
            key: _form,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(l.changePasswordIntro, style: theme.textTheme.bodyLarge),
                const SizedBox(height: 24),
                TextFormField(
                  controller: _current,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: l.temporaryPasswordLabel,
                    errorText: errorOn('currentPassword'),
                  ),
                  textInputAction: TextInputAction.next,
                  validator: (value) => value == null || value.isEmpty ? l.fieldRequired : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _new,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: l.newPasswordLabel,
                    helperText: l.newPasswordHelp,
                    errorText: errorOn('newPassword'),
                  ),
                  autofillHints: const [AutofillHints.newPassword],
                  textInputAction: TextInputAction.next,
                  validator: (value) => switch (value?.length ?? 0) {
                    < 8 => l.passwordTooShort,
                    > 72 => l.passwordTooLong,
                    _ => null,
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _confirm,
                  obscureText: true,
                  decoration: InputDecoration(labelText: l.confirmPasswordLabel),
                  textInputAction: TextInputAction.done,
                  onFieldSubmitted: (_) => _submit(),
                  validator: (value) => value != _new.text ? l.passwordsDoNotMatch : null,
                ),
                if (generalError != null) ...[
                  const SizedBox(height: 16),
                  Semantics(
                    liveRegion: true,
                    child: Text(generalError, style: TextStyle(color: theme.colorScheme.error)),
                  ),
                ],
                const SizedBox(height: 24),
                FilledButton(
                  onPressed: _busy ? null : _submit,
                  child: _busy
                      ? const SizedBox.square(dimension: 22, child: CircularProgressIndicator(strokeWidth: 2.5))
                      : Text(l.savePassword),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
