import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import 'parent.dart';

/// UC-P5, D39: one teacher of the child's class, with the subjects they teach it.
class TeacherContact {
  const TeacherContact({required this.name, required this.subjects, required this.phone});

  factory TeacherContact.fromJson(Map<String, dynamic> json) => TeacherContact(
    name: json['name'] as String,
    subjects: (json['subjects'] as List).cast<String>(),
    phone: json['phone'] as String?,
  );

  final String name;
  final List<String> subjects;

  /// Null when the account signs in with an email only.
  final String? phone;
}

/// UC-P5, D39: a DM or an assistant DM of the block of the child's class.
class DisciplineContact {
  const DisciplineContact({required this.name, required this.assistant, required this.phone});

  factory DisciplineContact.fromJson(Map<String, dynamic> json) => DisciplineContact(
    name: json['name'] as String,
    assistant: json['role'] == 'ASSISTANT_DM',
    phone: json['phone'] as String?,
  );

  final String name;
  final bool assistant;
  final String? phone;
}

/// UC-P5, D39: who a parent can contact about one child.
class ChildContacts {
  const ChildContacts({required this.className, required this.teachers, required this.discipline, this.fetchedAt});

  /// [fetchedAt]: set when this is the copy saved on the phone, shown without a network (NFR-2).
  factory ChildContacts.fromJson(Map<String, dynamic> json, {DateTime? fetchedAt}) => ChildContacts(
    className: json['className'] as String?,
    teachers: [for (final t in (json['teachers'] as List).cast<Map<String, dynamic>>()) TeacherContact.fromJson(t)],
    discipline: [
      for (final d in (json['discipline'] as List).cast<Map<String, dynamic>>()) DisciplineContact.fromJson(d),
    ],
    fetchedAt: fetchedAt,
  );

  final String? className;
  final List<TeacherContact> teachers;
  final List<DisciplineContact> discipline;
  final DateTime? fetchedAt;
}

/// A Cameroon number as +237 6XX XX XX XX, like the sign-in screen shows it; any other number as
/// stored. Display only: `tel:` and `sms:` links keep the plain number.
String displayPhone(String phone) {
  final match = RegExp(r'^\+237(\d{9})$').firstMatch(phone);
  if (match == null) return phone;
  final d = match.group(1)!;
  return '+237 ${d.substring(0, 3)} ${d.substring(3, 5)} ${d.substring(5, 7)} ${d.substring(7)}';
}

final childContactsProvider = FutureProvider.autoDispose.family<ChildContacts, String>(
  (ref, childId) => ref.watch(parentRepositoryProvider).contacts(parentUserId(ref), childId),
);

/// Opens a `tel:` or `sms:` link in the phone's own app; false when the phone has none. SchoolLink
/// sends nothing itself. Tests replace it.
final phoneLauncherProvider = Provider<Future<bool> Function(Uri)>(
  (ref) => (uri) => launchUrl(uri, mode: LaunchMode.externalApplication),
);
