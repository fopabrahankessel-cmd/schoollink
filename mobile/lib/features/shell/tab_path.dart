import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';

/// D41: a screen opened from a tab stays under that tab, so the tab stays the user's place and
/// back returns to it. [sub] is the path below the tab: `student/st-1`, `write?studentId=…`.
String underTab(BuildContext context, String sub) {
  // the router's location, so it also works from a sheet or a dialog
  final segments = GoRouter.of(context).routerDelegate.currentConfiguration.uri.pathSegments;
  final tab = segments.length > 1 && segments.first == 'home' ? segments[1] : 'today';
  return '/home/$tab/$sub';
}
