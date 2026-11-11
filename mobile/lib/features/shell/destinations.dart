import 'package:flutter/material.dart';

import '../../core/auth/user_info.dart';
import '../../l10n/app_localizations.dart';

/// The app's main screens, one or more per role (SPEC §8: one app, role-based navigation). A user
/// with several roles (a DM who also teaches, a teacher who is also a parent, D21) gets the tabs
/// of each, in this order. The DM has four (D41): Today, Students, Messages and More, which stays
/// last. The administrator and the researcher work on the web and have none.
enum Destination {
  sessions(Role.teacher, Icons.fact_check_outlined, Icons.fact_check),
  gate(Role.assistantDm, Icons.schedule_outlined, Icons.schedule),
  today(Role.dm, Icons.today_outlined, Icons.today),
  students(Role.dm, Icons.school_outlined, Icons.school),
  messages(Role.dm, Icons.forum_outlined, Icons.forum),
  cases(Role.counsellor, Icons.support_outlined, Icons.support),
  children(Role.parent, Icons.family_restroom_outlined, Icons.family_restroom),
  inbox(Role.parent, Icons.notifications_outlined, Icons.notifications),
  more(Role.dm, Icons.more_horiz_rounded, Icons.more_horiz_rounded);

  const Destination(this.role, this.icon, this.selectedIcon);

  final Role role;
  final IconData icon;
  final IconData selectedIcon;

  String get path => name;

  String label(AppLocalizations l) => switch (this) {
    sessions => l.navSessions,
    gate => l.navGate,
    today => l.navToday,
    students => l.navStudents,
    messages => l.navMessages,
    cases => l.navCases,
    children => l.navChildren,
    inbox => l.navInbox,
    more => l.navMore,
  };

  static Destination? byPath(String? path) {
    for (final destination in values) {
      if (destination.path == path) {
        return destination;
      }
    }
    return null;
  }
}

List<Destination> destinationsFor(Set<Role> roles) => [
  for (final destination in Destination.values)
    if (roles.contains(destination.role)) destination,
];

/// Material 3 allows five tabs in a bar (D41).
const maxTabs = 5;

/// The tabs in the bar: all of them up to five; past five, the first four and More.
List<Destination> barFor(List<Destination> all) {
  if (all.length <= maxTabs) {
    return all;
  }
  return [...all.where((d) => d != Destination.more).take(maxTabs - 1), Destination.more];
}

/// The tabs that did not fit in the bar, listed at the top of More.
List<Destination> overflowOf(List<Destination> all) {
  final bar = barFor(all);
  return [
    for (final d in all)
      if (!bar.contains(d)) d,
  ];
}
