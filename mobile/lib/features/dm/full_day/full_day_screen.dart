import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:printing/printing.dart';

import '../../../core/api/api_failure.dart';
import '../../../core/providers.dart';
import '../../../core/theme.dart';
import '../../../core/ui/skeleton.dart';
import '../../../core/ui/widgets.dart';
import '../../../l10n/app_localizations.dart';
import '../../auth/failure_text.dart';
import '../dm.dart';
import 'full_day_models.dart';
import 'report_document.dart';

/// Hands the PDF to Android's share sheet (WhatsApp, Bluetooth, e-mail…); replaced in tests.
abstract class ReportSharer {
  Future<void> share(Uint8List bytes, String fileName);
}

class PrintingSharer implements ReportSharer {
  const PrintingSharer();

  @override
  Future<void> share(Uint8List bytes, String fileName) => Printing.sharePdf(bytes: bytes, filename: fileName);
}

final reportSharerProvider = Provider<ReportSharer>((ref) => const PrintingSharer());

/// UC-D9: the day the screen opens on (today from 17:00, otherwise the previous school day).
final defaultReportDateProvider = FutureProvider.autoDispose<DateTime>(
  (ref) => ref.watch(dmRepositoryProvider).defaultReportDate(),
);

/// The bundled fonts, read once.
final _fontsProvider = FutureProvider<ReportFonts>((ref) => ReportFonts.load());

/// The school's logo by its version: fetched again only when the admin changes it.
final _logoCache = Provider<Map<String, Uint8List?>>((ref) => {});

sealed class _Outcome {
  const _Outcome();
}

class _Busy extends _Outcome {
  const _Busy();
}

class _Failed extends _Outcome {
  const _Failed(this.error);
  final Object error;
}

/// Nobody absent all day: no document, only the message (and the lessons never recorded).
class _Nobody extends _Outcome {
  const _Nobody(this.day, this.unrecorded);
  final String day;
  final String? unrecorded;
}

class _Ready extends _Outcome {
  const _Ready(this.report, this.bytes);
  final FullDayReport report;
  final Uint8List bytes;
}

/// UC-D9: the DM picks a school day, generates the full-day absence report of their classes,
/// previews the PDF and shares it. The app lists; it decides nothing (BR-16 to BR-19).
class FullDayScreen extends ConsumerStatefulWidget {
  const FullDayScreen({super.key});

  @override
  ConsumerState<FullDayScreen> createState() => _FullDayScreenState();
}

class _FullDayScreenState extends ConsumerState<FullDayScreen> {
  DateTime? _picked;
  _Outcome? _outcome;

  /// Bumped by each new request, so a slow answer for an older day is dropped.
  int _request = 0;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final defaultDate = ref.watch(defaultReportDateProvider);
    final day = _picked ?? defaultDate.value;
    final outcome = _outcome;

    return Scaffold(
      appBar: AppBar(title: Text(l.fullDayEntry)),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, Gap.m),
            child: switch ((day, defaultDate)) {
              (final day?, _) => _DayPanel(
                day: day,
                busy: outcome is _Busy,
                onPick: () => _pick(day),
                onGenerate: () => _generate(day),
              ),
              (null, AsyncError(:final error)) => FailureView(
                message: failureText(error, l),
                onRetry: () => ref.invalidate(defaultReportDateProvider),
              ),
              _ => const Skeleton(child: Bone(width: double.infinity, height: 148)),
            },
          ),
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 220),
              switchInCurve: Curves.easeOutCubic,
              child: switch (outcome) {
                null => const SizedBox.shrink(),
                _Busy() => _Preparing(key: const ValueKey('full-day-busy'), text: l.fullDayPreparing),
                // a day the server refuses (not over, not a school day): retrying cannot help
                _Failed(error: ApiFailure(status: 400) && final refusal) => ListView(
                  key: const ValueKey('full-day-refused'),
                  padding: const EdgeInsets.fromLTRB(Gap.l, 0, Gap.l, Gap.xl),
                  children: [
                    Card(
                      margin: EdgeInsets.zero,
                      child: EmptyState(icon: Icons.event_busy_rounded, title: failureText(refusal, l)),
                    ),
                  ],
                ),
                _Failed(:final error) => Padding(
                  key: const ValueKey('full-day-failed'),
                  padding: const EdgeInsets.all(Gap.l),
                  child: Align(
                    alignment: Alignment.topCenter,
                    child: FailureView(message: failureText(error, l), onRetry: () => _generate(day!)),
                  ),
                ),
                _Nobody(:final day, :final unrecorded) => _NobodyView(
                  key: const ValueKey('full-day-nobody'),
                  message: l.fullDayNobody(day),
                  unrecorded: unrecorded,
                ),
                _Ready(:final report, :final bytes) => _ReadyView(
                  key: ValueKey(bytes),
                  report: report,
                  bytes: bytes,
                  onShare: () => ref.read(reportSharerProvider).share(bytes, reportFileName(report.date)),
                ),
              },
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _pick(DateTime current) async {
    final today = DateUtils.dateOnly(ref.read(clockProvider)());
    final chosen = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(today.year - 1, 9, 1),
      lastDate: today,
      // the server refuses holidays too, with its own message; weekends are never school days
      selectableDayPredicate: (d) => d.weekday != DateTime.saturday && d.weekday != DateTime.sunday,
    );
    if (chosen != null && mounted && chosen != current) {
      setState(() {
        _picked = chosen;
        _outcome = null;
      });
    }
  }

  Future<void> _generate(DateTime day) async {
    final l = AppLocalizations.of(context);
    final request = ++_request;
    setState(() => _outcome = const _Busy());
    final repository = ref.read(dmRepositoryProvider);
    try {
      final report = await repository.fullDayReport(day);
      final generatedAt = ref.read(clockProvider)();
      final texts = ReportTexts.from(l, report: report, generatedAt: generatedAt);
      _Outcome outcome;
      if (report.isEmpty) {
        outcome = _Nobody(DateFormat.yMMMMEEEEd(l.localeName).format(report.date), texts.unrecorded);
      } else {
        final letterhead = await repository.letterhead();
        final bytes = await buildReportPdf(
          report: report,
          letterhead: letterhead,
          logo: await _logo(repository, letterhead),
          texts: texts,
          generatedAt: generatedAt,
          fonts: await ref.read(_fontsProvider.future),
        );
        outcome = _Ready(report, bytes);
      }
      if (mounted && request == _request) setState(() => _outcome = outcome);
    } catch (error) {
      if (mounted && request == _request) setState(() => _outcome = _Failed(error));
    }
  }

  Future<Uint8List?> _logo(DmRepository repository, Letterhead letterhead) async {
    final version = letterhead.logoVersion;
    if (!letterhead.hasLogo || version == null) return null;
    final cache = ref.read(_logoCache);
    if (cache.containsKey(version)) return cache[version];
    return cache[version] = await repository.letterheadLogo();
  }
}

/// The day as a calendar leaf, the field to change it, and the button.
class _DayPanel extends StatelessWidget {
  const _DayPanel({required this.day, required this.busy, required this.onPick, required this.onGenerate});

  final DateTime day;
  final bool busy;
  final VoidCallback onPick;
  final VoidCallback onGenerate;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final locale = l.localeName;
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(Gap.l),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              button: true,
              label: l.fullDayPickDay,
              child: InkWell(
                key: const ValueKey('full-day-date'),
                onTap: busy ? null : onPick,
                borderRadius: BorderRadius.circular(Radii.m),
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: Gap.xs),
                  child: Row(
                    children: [
                      _Leaf(day: day, locale: locale),
                      const SizedBox(width: Gap.l),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(l.fullDayPickDay, style: theme.textTheme.labelLarge?.copyWith(color: scheme.primary)),
                            const SizedBox(height: 2),
                            Text(DateFormat.yMMMMEEEEd(locale).format(day), style: theme.textTheme.titleMedium),
                          ],
                        ),
                      ),
                      Icon(Icons.edit_calendar_rounded, color: scheme.onSurfaceVariant),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: Gap.s),
            Text(l.fullDayDayHint, style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant)),
            const SizedBox(height: Gap.l),
            FilledButton.icon(
              key: const ValueKey('full-day-generate'),
              onPressed: busy ? null : onGenerate,
              icon: const Icon(Icons.description_outlined),
              label: Text(l.fullDayGenerate),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            ),
          ],
        ),
      ),
    );
  }
}

/// A tear-off calendar leaf: the month on a band, the day number large.
class _Leaf extends StatelessWidget {
  const _Leaf({required this.day, required this.locale});

  final DateTime day;
  final String locale;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return ExcludeSemantics(
      child: Container(
        width: 60,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: scheme.surfaceContainerLowest,
          borderRadius: BorderRadius.circular(Radii.s + 2),
          border: Border.all(color: scheme.outlineVariant),
        ),
        child: Column(
          children: [
            Container(
              width: double.infinity,
              color: scheme.primary,
              padding: const EdgeInsets.symmetric(vertical: 3),
              child: Text(
                DateFormat.MMM(locale).format(day).replaceAll('.', '').toUpperCase(),
                textAlign: TextAlign.center,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: scheme.onPrimary,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 1,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 2, bottom: 4),
              child: Text(
                '${day.day}',
                style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w700, fontFeatures: tabular),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Preparing extends StatelessWidget {
  const _Preparing({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Align(
      alignment: const Alignment(0, -0.5),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(width: 28, height: 28, child: CircularProgressIndicator(strokeWidth: 3)),
          const SizedBox(height: Gap.m),
          Text(text, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        ],
      ),
    );
  }
}

class _NobodyView extends StatelessWidget {
  const _NobodyView({super.key, required this.message, required this.unrecorded});

  final String message;
  final String? unrecorded;

  @override
  Widget build(BuildContext context) {
    final status = StatusColors.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(Gap.l, 0, Gap.l, Gap.xl),
      children: [
        Card(
          margin: EdgeInsets.zero,
          child: EmptyState(icon: Icons.verified_outlined, title: message),
        ),
        if (unrecorded case final line?) ...[
          const SizedBox(height: Gap.m),
          InfoBanner(
            icon: Icons.pending_actions_rounded,
            text: line,
            background: status.lateContainer,
            foreground: status.onLateContainer,
          ),
        ],
      ],
    );
  }
}

/// The PDF as it will be shared, under a strip with the count, and the Share button in reach.
class _ReadyView extends StatelessWidget {
  const _ReadyView({super.key, required this.report, required this.bytes, required this.onShare});

  final FullDayReport report;
  final Uint8List bytes;
  final VoidCallback onShare;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.l),
          child: Row(
            children: [
              Icon(Icons.picture_as_pdf_outlined, size: 20, color: scheme.primary),
              const SizedBox(width: Gap.s),
              Expanded(
                child: Text(
                  l.reportSummary(report.studentCount, report.classCount),
                  style: theme.textTheme.titleSmall?.copyWith(fontFeatures: tabular),
                ),
              ),
              Text(
                reportFileName(report.date),
                style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant),
              ),
            ],
          ),
        ),
        const SizedBox(height: Gap.s),
        Expanded(
          child: ColoredBox(
            color: scheme.surfaceContainerHigh,
            child: PdfPreview(
              build: (_) => bytes,
              allowPrinting: false,
              allowSharing: false,
              canChangePageFormat: false,
              canChangeOrientation: false,
              canDebug: false,
              useActions: false,
              pdfFileName: reportFileName(report.date),
              scrollViewDecoration: BoxDecoration(color: scheme.surfaceContainerHigh),
              pdfPreviewPageDecoration: const BoxDecoration(
                color: Colors.white,
                boxShadow: [BoxShadow(color: Color(0x33000000), blurRadius: 12, offset: Offset(0, 4))],
              ),
            ),
          ),
        ),
        SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.l, Gap.m),
            child: FilledButton.icon(
              key: const ValueKey('full-day-share'),
              onPressed: onShare,
              icon: const Icon(Icons.share_rounded),
              label: Text(l.fullDayShare),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            ),
          ),
        ),
      ],
    );
  }
}
