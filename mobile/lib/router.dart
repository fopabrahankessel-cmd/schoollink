import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'core/auth/user_info.dart';
import 'features/auth/activation_screen.dart';
import 'features/auth/auth_controller.dart';
import 'features/auth/change_password_screen.dart';
import 'features/auth/login_screen.dart';
import 'features/dm/alert_screen.dart';
import 'features/dm/student_history_screen.dart';
import 'features/dm/student_search_screen.dart';
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
    // UC-P1, D24: parents activate, or sign in again, by SMS instead of a password
    return location == '/activate' || location == '/parent-sign-in' ? null : goTo('/login');
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
      GoRoute(path: '/activate', builder: (context, state) => const ActivationScreen()),
      GoRoute(path: '/parent-sign-in', builder: (context, state) => const ActivationScreen(signIn: true)),
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
          GoRoute(path: 'students', builder: (context, state) => const StudentSearchScreen()),
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
