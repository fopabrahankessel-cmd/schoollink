import 'package:flutter/material.dart';

import '../../core/auth/user_info.dart';
import '../../l10n/app_localizations.dart';

/// The app's main screens, one or two per role (SPEC §8: one app, role-based navigation). A user
/// with several roles (a DM who also teaches, a teacher who is also a parent, D21) gets the tabs
/// of each. The administrator and the researcher work on the web and have none.
enum Destination {
  sessions(Role.teacher, Icons.fact_check_outlined, Icons.fact_check),
  gate(Role.assistantDm, Icons.schedule_outlined, Icons.schedule),
  alerts(Role.dm, Icons.notification_important_outlined, Icons.notification_important),
  cases(Role.counsellor, Icons.support_outlined, Icons.support),
  children(Role.parent, Icons.family_restroom_outlined, Icons.family_restroom),
  inbox(Role.parent, Icons.notifications_outlined, Icons.notifications);

  const Destination(this.role, this.icon, this.selectedIcon);

  final Role role;
  final IconData icon;
  final IconData selectedIcon;

  String get path => name;

  String label(AppLocalizations l) => switch (this) {
    sessions => l.navSessions,
    gate => l.navGate,
    alerts => l.navAlerts,
    cases => l.navCases,
    children => l.navChildren,
    inbox => l.navInbox,
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
