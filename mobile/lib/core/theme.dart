import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// The blue and teal of the SchoolLink logo.
const brandBlue = Color(0xFF1D4FA8);
const brandTeal = Color(0xFF14A89A);

/// The navy of the header band that opens each screen, under the app bar.
const _ink = Color(0xFF0F2A55);

/// Colours of meaning beyond the Material scheme: present, absent, late, and the header band.
/// Each comes with a light container for rows and pills, always paired with an icon or a word,
/// never colour alone.
@immutable
class StatusColors extends ThemeExtension<StatusColors> {
  const StatusColors({
    required this.present,
    required this.presentContainer,
    required this.absent,
    required this.absentContainer,
    required this.late,
    required this.lateContainer,
    required this.onLateContainer,
    required this.header,
    required this.onHeader,
    required this.onHeaderMuted,
  });

  static const light = StatusColors(
    present: Color(0xFF16794A),
    presentContainer: Color(0xFFDDF3E6),
    absent: Color(0xFFC62828),
    absentContainer: Color(0xFFFDE8E8),
    late: Color(0xFFB45309),
    lateContainer: Color(0xFFFFF0D9),
    onLateContainer: Color(0xFF6B3300),
    header: _ink,
    onHeader: Colors.white,
    onHeaderMuted: Color(0xFFB8C6E2),
  );

  final Color present;
  final Color presentContainer;
  final Color absent;
  final Color absentContainer;
  final Color late;
  final Color lateContainer;
  final Color onLateContainer;
  final Color header;
  final Color onHeader;
  final Color onHeaderMuted;

  static StatusColors of(BuildContext context) => Theme.of(context).extension<StatusColors>()!;

  @override
  StatusColors copyWith() => this;

  @override
  StatusColors lerp(StatusColors? other, double t) => this;
}

/// Spacing and corner radii, so every screen keeps the same rhythm.
abstract final class Gap {
  static const xs = 4.0;
  static const s = 8.0;
  static const m = 12.0;
  static const l = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;
}

abstract final class Radii {
  static const s = 8.0;
  static const m = 12.0;
  static const l = 16.0;
  static const xl = 24.0;
}

/// Figures that keep their width, so times and counts line up in columns.
const tabular = [FontFeature.tabularFigures()];

/// Light only for the pilot: screens are read outdoors at the gate and on low-end phones (NFR-4),
/// so the theme favours contrast and large touch targets over decoration. One bold element per
/// screen, the navy header band; everything under it is white cards on a cool grey.
ThemeData buildTheme() {
  const status = StatusColors.light;
  final scheme = ColorScheme.fromSeed(seedColor: brandBlue, dynamicSchemeVariant: DynamicSchemeVariant.fidelity)
      .copyWith(
        primary: brandBlue,
        onPrimary: Colors.white,
        primaryContainer: const Color(0xFFDCE6FA),
        onPrimaryContainer: const Color(0xFF0D2A5C),
        secondary: const Color(0xFF0E7C72),
        secondaryContainer: const Color(0xFFD4F1EC),
        onSecondaryContainer: const Color(0xFF053F39),
        tertiary: status.present,
        error: status.absent,
        errorContainer: status.absentContainer,
        onErrorContainer: const Color(0xFF7A1212),
        surface: const Color(0xFFF3F5F9),
        onSurface: const Color(0xFF111A2B),
        onSurfaceVariant: const Color(0xFF4D596C),
        outline: const Color(0xFF8A95A7),
        outlineVariant: const Color(0xFFDDE3EC),
        surfaceContainerLowest: Colors.white,
        surfaceContainerLow: const Color(0xFFF8F9FC),
        surfaceContainer: const Color(0xFFEDF1F6),
        surfaceContainerHigh: const Color(0xFFE6EBF2),
        surfaceContainerHighest: const Color(0xFFDDE4EE),
      );
  final base = ThemeData(colorScheme: scheme, useMaterial3: true);
  final text = base.textTheme.copyWith(
    headlineMedium: base.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.4),
    headlineSmall: base.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.3),
    titleLarge: base.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -0.2),
    titleMedium: base.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
    titleSmall: base.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
    labelLarge: base.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600),
  );
  final rounded = RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.m));

  return base.copyWith(
    textTheme: text,
    extensions: const [status],
    scaffoldBackgroundColor: scheme.surface,
    appBarTheme: AppBarTheme(
      backgroundColor: status.header,
      foregroundColor: status.onHeader,
      elevation: 0,
      scrolledUnderElevation: 0,
      centerTitle: false,
      systemOverlayStyle: SystemUiOverlayStyle.light.copyWith(statusBarColor: Colors.transparent),
      titleTextStyle: text.titleLarge?.copyWith(color: status.onHeader),
    ),
    cardTheme: CardThemeData(
      color: scheme.surfaceContainerLowest,
      elevation: 0,
      margin: EdgeInsets.zero,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(Radii.l),
        side: BorderSide(color: scheme.outlineVariant),
      ),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: scheme.surfaceContainerLowest,
      indicatorColor: scheme.primaryContainer,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      height: 68,
      labelTextStyle: WidgetStateProperty.resolveWith(
        (states) => text.labelMedium?.copyWith(
          fontWeight: states.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500,
          color: states.contains(WidgetState.selected) ? scheme.onPrimaryContainer : scheme.onSurfaceVariant,
        ),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: scheme.surfaceContainerLowest,
      contentPadding: const EdgeInsets.symmetric(horizontal: Gap.l, vertical: Gap.l),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radii.m),
        borderSide: BorderSide(color: scheme.outline),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radii.m),
        borderSide: BorderSide(color: scheme.outline),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(Radii.m),
        borderSide: BorderSide(color: scheme.primary, width: 2),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size.fromHeight(54),
        shape: rounded,
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size.fromHeight(54),
        shape: rounded,
        side: BorderSide(color: scheme.outline),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        minimumSize: const Size(64, 48),
        shape: rounded,
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: scheme.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(Radii.xl)),
      titleTextStyle: text.titleLarge?.copyWith(color: scheme.onSurface),
    ),
    bottomSheetTheme: BottomSheetThemeData(
      backgroundColor: scheme.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(Radii.xl))),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: const Color(0xFF1B2638),
      shape: rounded,
      contentTextStyle: text.bodyMedium?.copyWith(color: Colors.white),
    ),
    dividerTheme: DividerThemeData(color: scheme.outlineVariant, space: 1, thickness: 1),
    listTileTheme: ListTileThemeData(
      contentPadding: const EdgeInsets.symmetric(horizontal: Gap.l),
      iconColor: scheme.onSurfaceVariant,
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: scheme.surfaceContainerLowest,
      surfaceTintColor: Colors.transparent,
      shape: rounded,
    ),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: scheme.primary),
  );
}
