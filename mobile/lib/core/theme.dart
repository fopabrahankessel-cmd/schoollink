import 'package:flutter/material.dart';

/// The blue and teal of the SchoolLink logo.
const brandBlue = Color(0xFF1D4FA8);
const brandTeal = Color(0xFF14A89A);

/// Light only for the pilot: screens are read outdoors at the gate and on low-end phones (NFR-4),
/// so the theme favours contrast and large touch targets over decoration.
ThemeData buildTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: brandBlue,
    dynamicSchemeVariant: DynamicSchemeVariant.fidelity,
  ).copyWith(tertiary: brandTeal);
  return ThemeData(
    colorScheme: scheme,
    inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(52),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
    ),
  );
}
