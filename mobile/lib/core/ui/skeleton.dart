import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../theme.dart';

/// A loading placeholder shaped like the content it stands for, so the screen keeps its layout
/// and the content drops into place. The grey shapes ([Bone]) under one [Skeleton] pulse together,
/// from one animation, which stays cheap on low-end phones (NFR-4). With the system's "remove
/// animations" setting the shapes stay still. Screen readers hear "Loading" once (NFR-12).
class Skeleton extends StatefulWidget {
  const Skeleton({super.key, required this.child});

  final Widget child;

  @override
  State<Skeleton> createState() => _SkeletonState();
}

class _SkeletonState extends State<Skeleton> with SingleTickerProviderStateMixin {
  late final _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 900));
  late final _opacity = Tween(
    begin: 1.0,
    end: 0.45,
  ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller.stop();
    } else if (!_controller.isAnimating) {
      _controller.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: AppLocalizations.of(context).loading,
      liveRegion: true,
      child: ExcludeSemantics(
        child: _Pulse(opacity: _opacity, child: widget.child),
      ),
    );
  }
}

class _Pulse extends InheritedWidget {
  const _Pulse({required this.opacity, required super.child});

  final Animation<double> opacity;

  @override
  bool updateShouldNotify(_Pulse old) => old.opacity != opacity;
}

/// One grey shape: a line of text, an avatar, a button. [onDark] for shapes on the navy header.
class Bone extends StatelessWidget {
  const Bone({super.key, this.width, required this.height, this.radius = Radii.s, this.onDark = false});

  /// A circle, for an avatar.
  const Bone.circle({super.key, required double size, this.onDark = false})
    : width = size,
      height = size,
      radius = size / 2;

  final double? width;
  final double height;
  final double radius;
  final bool onDark;

  @override
  Widget build(BuildContext context) {
    final color = onDark ? Colors.white.withValues(alpha: 0.16) : Theme.of(context).colorScheme.surfaceContainerHighest;
    final box = Container(
      width: width,
      height: height,
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(radius)),
    );
    final pulse = context.dependOnInheritedWidgetOfExactType<_Pulse>();
    return pulse == null ? box : FadeTransition(opacity: pulse.opacity, child: box);
  }
}

/// Rows of avatar and two lines in a card: a list of students or of lateness records.
class SkeletonRows extends StatelessWidget {
  const SkeletonRows({super.key, this.count = 5, this.trailing = true});

  final int count;

  /// A short shape at the end of each row, for a time or a status.
  final bool trailing;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Column(
        children: [
          for (var i = 0; i < count; i++) ...[
            if (i > 0) const Divider(indent: 68),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: Gap.l, vertical: Gap.m),
              child: Row(
                children: [
                  const Bone.circle(size: 40),
                  const SizedBox(width: Gap.m),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // the lengths vary a little, as names do
                        FractionallySizedBox(
                          widthFactor: const [0.7, 0.55, 0.8, 0.6][i % 4],
                          child: const Bone(height: 14),
                        ),
                        const SizedBox(height: Gap.s),
                        const FractionallySizedBox(widthFactor: 0.35, child: Bone(height: 10)),
                      ],
                    ),
                  ),
                  if (trailing) ...[const SizedBox(width: Gap.m), const Bone(width: 44, height: 24, radius: Radii.l)],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// A form field still waiting for its choices, such as the list of schools.
class SkeletonField extends StatelessWidget {
  const SkeletonField({super.key});

  @override
  Widget build(BuildContext context) => const Skeleton(child: Bone(height: 56, radius: Radii.m));
}
