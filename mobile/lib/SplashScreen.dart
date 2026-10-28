import 'package:animated_splash_screen/animated_splash_screen.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';
import 'package:my_flutter_app/HomeScreen.dart';

class SplashScreen extends StatefulWidget{
  const SplashScreen({super.key});
  @override
  State<SplashScreen> createState() => _SplashScreenState();
}
class _SplashScreenState extends State<SplashScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
    body: Center(
    child: AnimatedSplashScreen(
        splash: Lottie.asset('assets/images/school_link.json'),
        nextScreen: HomeScreen(),
        splashIconSize: 450,
        duration: 3000,)
    ),
    );
  }
}