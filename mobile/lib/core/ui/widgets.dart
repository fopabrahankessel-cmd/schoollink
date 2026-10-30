import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../theme.dart';

/// The navy band under the app bar that opens each home screen: the day, and the one figure that
/// matters on that screen. Rounded at the bottom so the cards under it read as the content.
class HeaderBand extends StatelessWidget {
  const HeaderBand({super.key, required this.children, this.padding});

  final List<Widget> children;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: StatusColors.of(context).header,
        borderRadius: const BorderRadius.vertical(bottom: Radius.circular(Radii.xl)),
      ),
      child: Padding(
        padding: padding ?? const EdgeInsets.fromLTRB(Gap.l + 4, Gap.xs, Gap.l + 4, Gap.xl),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children),
      ),
    );
  }
}

/// A student's initials in a circle: staff recognise the row at a glance, with no photo (NFR-10).
class InitialsAvatar extends StatelessWidget {
  const InitialsAvatar({
    super.key,
    required this.firstName,
    required this.lastName,
    this.background,
    this.foreground,
    this.size = 40,
    this.icon,
  });

  final String firstName;
  final String lastName;
  final Color? background;
  final Color? foreground;
  final double size;

  /// Shown instead of the initials (a mark for an absent student).
  final IconData? icon;

  static String initials(String first, String last) {
    String head(String s) => s.trim().isEmpty ? '' : s.trim().characters.first.toUpperCase();
    return '${head(last)}${head(first)}';
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final fg = foreground ?? scheme.onPrimaryContainer;
    return ExcludeSemantics(
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        curve: Curves.easeOut,
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(color: background ?? scheme.primaryContainer, shape: BoxShape.circle),
        child: icon != null
            ? Icon(icon, color: fg, size: size * 0.55)
            : Text(
                initials(firstName, lastName),
                style: TextStyle(color: fg, fontWeight: FontWeight.w700, fontSize: size * 0.36, letterSpacing: 0.3),
              ),
      ),
    );
  }
}

/// A short status in a rounded pill, always a word (and often an icon), never colour alone.
class StatusPill extends StatelessWidget {
  const StatusPill({super.key, required this.label, required this.background, required this.foreground, this.icon});

  final String label;
  final Color background;
  final Color foreground;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(999)),
      child: Padding(
        padding: EdgeInsets.fromLTRB(icon == null ? 10 : 8, 4, 10, 4),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (icon != null) ...[Icon(icon, size: 16, color: foreground), const SizedBox(width: 4)],
            Flexible(
              child: Text(
                label,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.labelLarge
                    ?.copyWith(color: foreground, fontWeight: FontWeight.w700, height: 1.2),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A heading above a group of cards, with an optional figure or action on the right.
class SectionHeader extends StatelessWidget {
  const SectionHeader({super.key, required this.title, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(Gap.xs, Gap.xl, Gap.xs, Gap.s),
      child: Row(
        children: [
          Expanded(
            child: Semantics(
              header: true,
              child: Text(title, style: theme.textTheme.titleMedium?.copyWith(color: theme.colorScheme.onSurface)),
            ),
          ),
          ?trailing,
        ],
      ),
    );
  }
}

/// A notice inside a screen: offline copy, records waiting, a rule to know.
class InfoBanner extends StatelessWidget {
  const InfoBanner({
    super.key,
    required this.icon,
    required this.text,
    required this.background,
    required this.foreground,
    this.action,
  });

  final IconData icon;
  final String text;
  final Color background;
  final Color foreground;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(color: background, borderRadius: BorderRadius.circular(Radii.m)),
      child: Padding(
        padding: EdgeInsets.fromLTRB(Gap.m, action == null ? Gap.m : Gap.xs, Gap.xs, action == null ? Gap.m : Gap.xs),
        child: Row(
          children: [
            Icon(icon, color: foreground, size: 22),
            const SizedBox(width: Gap.m),
            Expanded(
              child: Text(
                text,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: foreground, fontWeight: FontWeight.w500),
              ),
            ),
            if (action != null) action! else const SizedBox(width: Gap.s),
          ],
        ),
      ),
    );
  }
}

/// An empty or finished state: says what is (or is not) here and, if anything, what to do.
class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.title, this.body, this.action});

  final IconData icon;
  final String title;
  final String? body;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: Gap.xl, vertical: Gap.xxl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 72,
            height: 72,
            decoration: BoxDecoration(color: scheme.surfaceContainerHigh, shape: BoxShape.circle),
            child: Icon(icon, size: 34, color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: Gap.l),
          Text(title, style: theme.textTheme.titleMedium, textAlign: TextAlign.center),
          if (body case final body?) ...[
            const SizedBox(height: Gap.s),
            Text(
              body,
              style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
          ],
          if (action case final action?) ...[const SizedBox(height: Gap.l), action],
        ],
      ),
    );
  }
}

/// A screen that could not load, with the way to try again.
class FailureView extends StatelessWidget {
  const FailureView({super.key, required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Center(
      child: SingleChildScrollView(
        child: EmptyState(
          icon: Icons.wifi_off_rounded,
          title: message,
          action: OutlinedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh_rounded),
            label: Text(l.retry),
            style: OutlinedButton.styleFrom(minimumSize: const Size(160, 48)),
          ),
        ),
      ),
    );
  }
}
