import 'package:animated_splash_screen/animated_splash_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/home_screen.dart';
import 'package:schoollink/splash_screen.dart';

void main() {
  testWidgets('splash screen shows, then navigates to the home screen', (
    WidgetTester tester,
  ) async {
    await tester.pumpWidget(const MaterialApp(home: SplashScreen()));

    expect(find.byType(AnimatedSplashScreen), findsOneWidget);
    expect(find.byType(HomeScreen), findsNothing);

    // AnimatedSplashScreen plays its 800 ms intro, then waits `duration`
    // (3 s), then runs the page transition. The Lottie animation loops,
    // so pumpAndSettle would never return: step frames instead.
    for (
      var i = 0;
      i < 100 && find.byType(HomeScreen).evaluate().isEmpty;
      i++
    ) {
      await tester.pump(const Duration(milliseconds: 100));
    }

    expect(find.byType(HomeScreen), findsOneWidget);
  });
}
