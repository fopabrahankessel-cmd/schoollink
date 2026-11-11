import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/auth/user_info.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/change_password_screen.dart';
import 'features/auth/login_screen.dart';
import 'features/auth/parent_sign_in_screen.dart';
import 'features/dm/alert_screen.dart';
import 'features/dm/alerts_list.dart';
import 'features/dm/closed_alerts_screen.dart';
import 'features/dm/dashboard/scope_dashboard_screen.dart';
import 'features/dm/settings/settings_screen.dart';
import 'features/dm/message_composer_screen.dart';
import 'features/dm/student_history_screen.dart';
import 'features/dm/full_day/full_day_screen.dart';
import 'features/dm/day/day_screen.dart';
import 'features/shell/destinations.dart';
import 'features/shell/home_shell.dart';
import 'features/shell/web_only_screen.dart';
import 'features/splash/splash_screen.dart';
import 'features/teacher/attendance_screen.dart';
import 'features/parent/history_screen.dart';
import 'features/parent/inbox_screen.dart';

/// Where the user must be, given who is signed in; null when [location] is already right.
String? redirectFor(AsyncValue<UserInfo?> auth, String location) {
  String? goTo(String path) => location == path ? null : path;

  if (auth.isLoading && !auth.hasValue) {
    return goTo('/splash');
  }
  final user = auth.value;
  if (user == null) {
    // UC-P1, D24: parents sign in by SMS instead of a password
    return location == '/parent-sign-in' ? null : goTo('/login');
  }
  if (user.passwordChangeRequired) {
    return goTo('/change-password'); // D18
  }
  final destinations = destinationsFor(user.roles);
  if (destinations.isEmpty) {
    return goTo('/web-only');
  }
  // the tab is the first segment: /home/sessions, /home/sessions/attendance/...
  final tab = location.startsWith('/home/') ? Destination.byPath(location.split('/')[2]) : null;
  return destinations.contains(tab) ? null : '/home/${destinations.first.path}';
}

final routerProvider = Provider<GoRouter>((ref) {
  final authChanged = ValueNotifier(0);
  ref.listen(authControllerProvider, (_, _) => authChanged.value++);
  final router = GoRouter(
    initialLocation: '/splash',
    refreshListenable: authChanged,
    redirect: (context, state) => redirectFor(ref.read(authControllerProvider), state.uri.path),
    routes: [
      GoRoute(path: '/splash', builder: (context, state) => const SplashScreen()),
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
      GoRoute(path: '/parent-sign-in', builder: (context, state) => const ParentSignInScreen()),
      GoRoute(path: '/change-password', builder: (context, state) => const ChangePasswordScreen()),
      GoRoute(path: '/web-only', builder: (context, state) => const WebOnlyScreen()),
      GoRoute(
        path: '/home/:tab',
        builder: (context, state) => HomeShell(current: Destination.byPath(state.pathParameters['tab'])!),
        routes: [
          GoRoute(
            path: 'message/:id',
            builder: (context, state) => MessageScreen(id: state.pathParameters['id']!),
          ),
          GoRoute(
            path: 'child/:childId',
            builder: (context, state) => ChildHistoryScreen(childId: state.pathParameters['childId']!),
          ),
          GoRoute(
            path: 'alert/:alertId',
            builder: (context, state) => AlertScreen(alertId: state.pathParameters['alertId']!),
          ),
          GoRoute(
            path: 'student/:studentId',
            builder: (context, state) => StudentHistoryScreen(studentId: state.pathParameters['studentId']!),
          ),
          // D41, UC-D2: the open alerts, from the Today tab's count
          GoRoute(path: 'alerts', builder: (context, state) => const OpenAlertsScreen()),
          // D41: the decided alerts, from More
          GoRoute(path: 'alerts/closed', builder: (context, state) => const ClosedAlertsScreen()),
          // UC-D8, D41: the scope dashboard, from More
          GoRoute(path: 'dashboard', builder: (context, state) => const ScopeDashboardScreen()),
          // UC-D7, D41: thresholds and digest times, from More
          GoRoute(path: 'settings', builder: (context, state) => const SettingsScreen()),
          GoRoute(path: 'full-day', builder: (context, state) => const FullDayScreen()),
          // UC-D10, D40: the DM's day of attendance, period by period
          GoRoute(path: 'day', builder: (context, state) => const DayScreen()),
          // UC-D4, D38: a message to parents; the student's name travels as extra. The sent
          // messages and the search are the DM's Messages and Students tabs (D41).
          GoRoute(
            path: 'write',
            builder: (context, state) => MessageComposerScreen(
              studentId: state.uri.queryParameters['studentId'],
              studentName: state.extra as String?,
            ),
          ),
          GoRoute(
            path: 'attendance/:classId/:periodId',
            builder: (context, state) => AttendanceScreen(
              classId: state.pathParameters['classId']!,
              periodId: state.pathParameters['periodId']!,
            ),
          ),
        ],
      ),
    ],
  );
  ref.onDispose(() {
    router.dispose();
    authChanged.dispose();
  });
  return router;
});
