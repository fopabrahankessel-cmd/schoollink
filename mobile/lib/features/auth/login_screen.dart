import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api/api_failure.dart';
import '../../core/providers.dart';
import '../../l10n/app_localizations.dart';
import 'auth_api.dart';
import 'auth_controller.dart';
import 'failure_text.dart';

/// The schools for the picker (D16) and the one to start on: the last school used on this device,
/// or the only school there is.
final loginSchoolsProvider = FutureProvider.autoDispose<(List<School>, String?)>((ref) async {
  final schools = await ref.watch(authApiProvider).schools();
  final remembered = await ref.watch(sessionStoreProvider).rememberedSchool();
  final initial = schools.any((s) => s.id == remembered)
      ? remembered
      : schools.length == 1
      ? schools.single.id
      : null;
  return (schools, initial);
});

/// Staff sign-in: school, phone or email, password (D15, D16). Parents sign in through activation.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _login = TextEditingController();
  final _password = TextEditingController();
  String? _schoolId;
  bool _schoolTouched = false;
  bool _obscure = true;
  bool _busy = false;
  Object? _error;

  @override
  void dispose() {
    _login.dispose();
    _password.dispose();
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
    final schools = ref.read(loginSchoolsProvider).value;
    try {
      await ref
          .read(authControllerProvider.notifier)
          .signIn(
            schoolId: _schoolTouched ? _schoolId : schools?.$2,
            login: _login.text.trim(),
            password: _password.text,
          );
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
    final schools = ref.watch(loginSchoolsProvider);
    // auth.school_required: the login exists in several schools (D16)
    final schoolError = switch (_error) {
      ApiFailure(field: 'schoolId', :final message) => message,
      _ => null,
    };
    final generalError = _error != null && schoolError == null ? failureText(_error!, l) : null;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: AutofillGroup(
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Align(alignment: Alignment.centerRight, child: LanguageSwitch()),
                      Image.asset('assets/images/SchoolLink_logo_640x640.png', height: 132, semanticLabel: l.appTitle),
                      const SizedBox(height: 16),
                      Text(l.loginTitle, style: theme.textTheme.headlineSmall),
                      const SizedBox(height: 4),
                      Text(l.loginIntro, style: theme.textTheme.bodyMedium),
                      const SizedBox(height: 24),
                      switch (schools) {
                        AsyncData(value: (final list, final initial)) => DropdownButtonFormField<String>(
                          initialValue: _schoolTouched ? _schoolId : initial,
                          isExpanded: true,
                          decoration: InputDecoration(labelText: l.schoolLabel, errorText: schoolError),
                          items: [
                            for (final school in list) DropdownMenuItem(value: school.id, child: Text(school.name)),
                          ],
                          onChanged: (id) => setState(() {
                            _schoolId = id;
                            _schoolTouched = true;
                          }),
                        ),
                        AsyncError() => Row(
                          children: [
                            Expanded(child: Text(l.schoolsLoadFailed, style: theme.textTheme.bodyMedium)),
                            TextButton(onPressed: () => ref.invalidate(loginSchoolsProvider), child: Text(l.retry)),
                          ],
                        ),
                        _ => const LinearProgressIndicator(),
                      },
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _login,
                        decoration: InputDecoration(labelText: l.loginLabel),
                        keyboardType: TextInputType.emailAddress,
                        autocorrect: false,
                        autofillHints: const [AutofillHints.username],
                        textInputAction: TextInputAction.next,
                        validator: (value) => value == null || value.trim().isEmpty ? l.fieldRequired : null,
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _password,
                        decoration: InputDecoration(
                          labelText: l.passwordLabel,
                          suffixIcon: IconButton(
                            tooltip: _obscure ? l.showPassword : l.hidePassword,
                            icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                            onPressed: () => setState(() => _obscure = !_obscure),
                          ),
                        ),
                        obscureText: _obscure,
                        autocorrect: false,
                        enableSuggestions: false,
                        autofillHints: const [AutofillHints.password],
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _submit(),
                        validator: (value) => value == null || value.isEmpty ? l.fieldRequired : null,
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
                            : Text(l.signIn),
                      ),
                      const SizedBox(height: 32),
                      // UC-P1: parents have no password; they activate with the school's SMS code
                      Row(
                        children: [
                          const Expanded(child: Divider()),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            child: Text(
                              l.parentQuestion,
                              style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                            ),
                          ),
                          const Expanded(child: Divider()),
                        ],
                      ),
                      const SizedBox(height: 16),
                      OutlinedButton.icon(
                        onPressed: _busy ? null : () => context.push('/activate'),
                        icon: const Icon(Icons.sms_outlined),
                        label: Text(l.activateWithCode),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// French or English before sign-in (NFR-12); afterwards the account's language applies.
class LanguageSwitch extends ConsumerWidget {
  const LanguageSwitch({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return Semantics(
      label: l.languageLabel,
      child: SegmentedButton<String>(
        segments: const [
          ButtonSegment(value: 'fr', label: Text('FR')),
          ButtonSegment(value: 'en', label: Text('EN')),
        ],
        selected: {ref.watch(preferredLanguageProvider)},
        showSelectedIcon: false,
        onSelectionChanged: (selected) => ref.read(preferredLanguageProvider.notifier).set(selected.single),
      ),
    );
  }
}
