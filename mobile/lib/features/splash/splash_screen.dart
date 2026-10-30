import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';

/// Shown while the session is restored at start-up, usually for a moment only.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: Center(child: Lottie.asset('assets/images/school_link.json', width: 280)),
    );
  }
}
