import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'contacts.dart';

/// UC-P5, D39: whom a parent can contact about a child: the class's teachers with their subjects,
/// then the block's DMs. Call and SMS open the phone's own apps; the number never goes in a URL to
/// the server (NFR-9).
class ContactsView extends ConsumerWidget {
  const ContactsView({super.key, required this.childId});

  final String childId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return switch (ref.watch(childContactsProvider(childId))) {
      AsyncValue(value: final contacts?) => RefreshIndicator(
        onRefresh: () => ref.refresh(childContactsProvider(childId).future).then((_) {}, onError: (_) {}),
        child: _ContactList(contacts: contacts),
      ),
      AsyncError(:final error) => FailureView(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(childContactsProvider(childId)),
      ),
      _ => const Skeleton(child: SkeletonRows(count: 6)),
    };
  }
}

class _ContactList extends StatelessWidget {
  const _ContactList({required this.contacts});

  final ChildContacts contacts;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: Gap.xl),
      children: [
        if (contacts.fetchedAt case final fetchedAt?)
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, 0),
            child: InfoBanner(
              icon: Icons.cloud_off_rounded,
              text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
              background: theme.colorScheme.secondaryContainer,
              foreground: theme.colorScheme.onSecondaryContainer,
            ),
          ),
        if (contacts.className case final name?)
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, 0),
            child: Text(l.classLabel(name), style: theme.textTheme.titleMedium),
          ),
        _Header(title: l.contactsTeachers),
        if (contacts.teachers.isEmpty) _Empty(text: l.contactsNoTeachers),
        for (final teacher in contacts.teachers)
          _ContactTile(name: teacher.name, detail: teacher.subjects.join(', '), phone: teacher.phone),
        _Header(title: l.contactsDiscipline),
        if (contacts.discipline.isEmpty) _Empty(text: l.contactsNoDiscipline),
        for (final person in contacts.discipline)
          _ContactTile(
            name: person.name,
            detail: person.assistant ? l.roleAssistantDm : l.roleDm,
            phone: person.phone,
          ),
      ],
    );
  }
}

/// A section title lined up with the class label and the tiles' avatars.
class _Header extends StatelessWidget {
  const _Header({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: Gap.l - Gap.xs),
    child: SectionHeader(title: title),
  );
}

class _Empty extends StatelessWidget {
  const _Empty({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: Gap.l, vertical: Gap.s),
      child: Text(text, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
    );
  }
}

/// One person: name, what they do, and the call and SMS buttons when they have a number.
class _ContactTile extends ConsumerWidget {
  const _ContactTile({required this.name, required this.detail, required this.phone});

  final String name;
  final String detail;
  final String? phone;

  Future<void> _open(BuildContext context, WidgetRef ref, Uri uri) async {
    final messenger = ScaffoldMessenger.of(context);
    final text = AppLocalizations.of(context).contactsCannotOpen;
    if (!await ref.read(phoneLauncherProvider)(uri)) {
      messenger.showSnackBar(SnackBar(content: Text(text)));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final phone = this.phone;
    return ListTile(
      contentPadding: const EdgeInsets.only(left: Gap.l, right: Gap.s),
      leading: InitialsAvatar(firstName: name.split(' ').first, lastName: name.split(' ').last),
      title: Text(name),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [Text(detail), Text(phone == null ? l.contactsNoPhone : displayPhone(phone))],
      ),
      isThreeLine: true,
      trailing: phone == null
          ? null
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                IconButton(
                  tooltip: l.contactsCall(name),
                  icon: const Icon(Icons.call_outlined),
                  onPressed: () => _open(context, ref, Uri(scheme: 'tel', path: phone)),
                ),
                IconButton(
                  tooltip: l.contactsSms(name),
                  icon: const Icon(Icons.sms_outlined),
                  onPressed: () => _open(context, ref, Uri(scheme: 'sms', path: phone)),
                ),
              ],
            ),
    );
  }
}
