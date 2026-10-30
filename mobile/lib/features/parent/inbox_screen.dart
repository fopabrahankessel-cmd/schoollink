import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/providers.dart';
import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'parent.dart';

/// The look of each kind of message, the same in the list and the message itself.
class EventStyle {
  const EventStyle(this.icon, this.background, this.foreground, this.label);

  factory EventStyle.of(BuildContext context, EventType type) {
    final l = AppLocalizations.of(context);
    final scheme = Theme.of(context).colorScheme;
    final status = StatusColors.of(context);
    return switch (type) {
      EventType.absence => EventStyle(Icons.event_busy_rounded, status.absentContainer, status.absent, l.eventAbsence),
      EventType.lateness => EventStyle(
        Icons.schedule_rounded,
        status.lateContainer,
        status.onLateContainer,
        l.eventLateness,
      ),
      EventType.correction => EventStyle(
        Icons.published_with_changes_rounded,
        status.presentContainer,
        status.present,
        l.eventCorrection,
      ),
      EventType.summons => EventStyle(
        Icons.event_note_rounded,
        scheme.primaryContainer,
        scheme.onPrimaryContainer,
        l.eventSummons,
      ),
      EventType.dmMessage => EventStyle(
        Icons.mail_outline_rounded,
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
        l.eventMessage,
      ),
      EventType.announcement => EventStyle(
        Icons.campaign_outlined,
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
        l.eventAnnouncement,
      ),
      EventType.other => EventStyle(
        Icons.notifications_outlined,
        scheme.surfaceContainerHigh,
        scheme.onSurfaceVariant,
        l.eventOther,
      ),
    };
  }

  final IconData icon;
  final Color background;
  final Color foreground;
  final String label;
}

/// "Today", "Yesterday", or the date, for a heading.
String dayHeading(DateTime day, DateTime now, AppLocalizations l, String locale) {
  final date = DateTime(day.year, day.month, day.day);
  final today = DateTime(now.year, now.month, now.day);
  final days = today.difference(date).inDays;
  if (days == 0) {
    return l.today;
  }
  if (days == 1) {
    return l.yesterday;
  }
  final text = (date.year == today.year ? DateFormat.MMMMEEEEd(locale) : DateFormat.yMMMMEEEEd(locale)).format(date);
  return text.isEmpty ? text : text[0].toUpperCase() + text.substring(1);
}

/// UC-P2: the parent's messages, newest first, by day. Unread ones stand out; opening one sends
/// the receipt (BR-24).
class InboxScreen extends ConsumerWidget {
  const InboxScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return switch (ref.watch(inboxProvider)) {
      AsyncValue(value: final inbox?) => RefreshIndicator(
        onRefresh: () => ref.refresh(inboxProvider.future).then((_) {}, onError: (_) {}),
        child: _InboxList(inbox: inbox),
      ),
      AsyncError(:final error) => FailureView(
        message: failureText(error, l),
        onRetry: () => ref.invalidate(inboxProvider),
      ),
      _ => const _InboxSkeleton(),
    };
  }
}

class _InboxList extends ConsumerWidget {
  const _InboxList({required this.inbox});

  final Inbox inbox;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final now = ref.watch(clockProvider)();
    final byDay = <DateTime, List<InboxEntry>>{};
    for (final entry in inbox.entries) {
      final at = entry.createdAt;
      byDay.putIfAbsent(DateTime(at.year, at.month, at.day), () => []).add(entry);
    }

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: Gap.xl),
      children: [
        HeaderBand(
          children: [
            Text(
              DateFormat.yMMMMEEEEd(locale).format(now),
              style: theme.textTheme.bodyLarge?.copyWith(color: status.onHeaderMuted),
            ),
            const SizedBox(height: Gap.xs),
            Text(
              l.unreadMessages(inbox.unread),
              style: theme.textTheme.headlineSmall?.copyWith(color: status.onHeader, fontFeatures: tabular),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Gap.l),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (inbox.fetchedAt case final fetchedAt?) ...[
                const SizedBox(height: Gap.l),
                InfoBanner(
                  icon: Icons.cloud_off_rounded,
                  text: l.offlineSince(DateFormat.Hm(locale).format(fetchedAt)),
                  background: theme.colorScheme.secondaryContainer,
                  foreground: theme.colorScheme.onSecondaryContainer,
                ),
              ],
              if (inbox.entries.isEmpty)
                EmptyState(icon: Icons.notifications_none_rounded, title: l.inboxEmpty, body: l.inboxEmptyHint)
              else
                for (final MapEntry(key: day, value: entries) in byDay.entries) ...[
                  SectionHeader(title: dayHeading(day, now, l, locale)),
                  Card(
                    clipBehavior: Clip.antiAlias,
                    child: Column(
                      children: [
                        for (final (i, entry) in entries.indexed) ...[
                          if (i > 0) const Divider(indent: 68),
                          _EntryRow(entry: entry, unread: !inbox.isOpened(entry)),
                        ],
                      ],
                    ),
                  ),
                ],
              if (inbox.hasMore) const _LoadMore(),
            ],
          ),
        ),
      ],
    );
  }
}

/// One message: its kind, title, the start of its text and the time; unread ones in bold, with a
/// dot.
class _EntryRow extends StatelessWidget {
  const _EntryRow({required this.entry, required this.unread});

  final InboxEntry entry;
  final bool unread;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final style = EventStyle.of(context, entry.type);
    final time = DateFormat.Hm(Localizations.localeOf(context).toLanguageTag()).format(entry.createdAt);
    return Semantics(
      button: true,
      label: unread ? l.unread : null,
      child: InkWell(
        key: ValueKey('message-${entry.id}'),
        onTap: () => context.push('/home/inbox/message/${entry.id}'),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(Gap.l, Gap.m, Gap.m, Gap.m),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ExcludeSemantics(
                child: Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(color: style.background, shape: BoxShape.circle),
                  child: Icon(style.icon, size: 22, color: style.foreground),
                ),
              ),
              const SizedBox(width: Gap.m),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            entry.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.titleSmall?.copyWith(
                              fontWeight: unread ? FontWeight.w800 : FontWeight.w500,
                            ),
                          ),
                        ),
                        const SizedBox(width: Gap.s),
                        Text(
                          time,
                          style: theme.textTheme.labelMedium?.copyWith(
                            color: unread ? scheme.primary : scheme.onSurfaceVariant,
                            fontWeight: unread ? FontWeight.w700 : FontWeight.w400,
                            fontFeatures: tabular,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 2),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Text(
                            entry.body,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: unread ? scheme.onSurface : scheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                        if (unread)
                          Padding(
                            padding: const EdgeInsets.only(left: Gap.s, top: 6),
                            child: ExcludeSemantics(
                              child: Container(
                                key: const ValueKey('unread-dot'),
                                width: 10,
                                height: 10,
                                decoration: BoxDecoration(color: scheme.primary, shape: BoxShape.circle),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _LoadMore extends ConsumerStatefulWidget {
  const _LoadMore();

  @override
  ConsumerState<_LoadMore> createState() => _LoadMoreState();
}

class _LoadMoreState extends ConsumerState<_LoadMore> {
  bool _busy = false;
  bool _failed = false;

  Future<void> _load() async {
    setState(() {
      _busy = true;
      _failed = false;
    });
    try {
      await ref.read(inboxProvider.notifier).loadMore();
    } catch (_) {
      _failed = true;
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: Gap.l),
      child: Column(
        children: [
          if (_failed)
            Padding(
              padding: const EdgeInsets.only(bottom: Gap.s),
              child: Text(l.olderMessagesFailed, textAlign: TextAlign.center),
            ),
          if (_busy)
            const Skeleton(child: SkeletonRows(count: 2))
          else
            OutlinedButton.icon(
              onPressed: _load,
              icon: const Icon(Icons.expand_more_rounded),
              label: Text(l.olderMessages),
              style: OutlinedButton.styleFrom(minimumSize: const Size(200, 48)),
            ),
        ],
      ),
    );
  }
}

class _InboxSkeleton extends StatelessWidget {
  const _InboxSkeleton();

  @override
  Widget build(BuildContext context) {
    return Skeleton(
      child: ListView(
        physics: const NeverScrollableScrollPhysics(),
        children: const [
          HeaderBand(
            children: [
              Bone(width: 160, height: 14, onDark: true),
              SizedBox(height: Gap.s),
              Bone(width: 220, height: 28, onDark: true),
            ],
          ),
          Padding(
            padding: EdgeInsets.fromLTRB(Gap.l, Gap.xl, Gap.l, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Bone(width: 110, height: 14),
                SizedBox(height: Gap.m),
                SkeletonRows(count: 5, trailing: false),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// One message in full. Opening it is the receipt the research counts (BR-24); a message about
/// attendance leads to the child's history (UC-P3).
class MessageScreen extends ConsumerStatefulWidget {
  const MessageScreen({super.key, required this.id});

  final String id;

  @override
  ConsumerState<MessageScreen> createState() => _MessageScreenState();
}

class _MessageScreenState extends ConsumerState<MessageScreen> {
  @override
  void initState() {
    super.initState();
    // after the first frame: the inbox may still be loading, and the receipt waits for it
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        ref.read(inboxProvider.notifier).markOpened(widget.id).catchError((_) {});
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final inbox = ref.watch(inboxProvider);
    final entry = inbox.value?.byId(widget.id);
    if (entry == null) {
      return Scaffold(
        appBar: AppBar(),
        body: switch (inbox) {
          AsyncValue(hasValue: true) => EmptyState(
            icon: Icons.mark_email_read_outlined,
            title: l.messageNotFound,
            action: OutlinedButton(onPressed: () => context.go('/home/inbox'), child: Text(l.backToInbox)),
          ),
          AsyncError(:final error) => FailureView(
            message: failureText(error, l),
            onRetry: () => ref.invalidate(inboxProvider),
          ),
          _ => const Skeleton(
            child: Padding(
              padding: EdgeInsets.all(Gap.l),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Bone.circle(size: 56),
                  SizedBox(height: Gap.l),
                  Bone(width: 240, height: 24),
                  SizedBox(height: Gap.s),
                  Bone(width: 180, height: 14),
                  SizedBox(height: Gap.xl),
                  Bone(height: 14),
                  SizedBox(height: Gap.s),
                  Bone(height: 14),
                  SizedBox(height: Gap.s),
                  Bone(width: 200, height: 14),
                ],
              ),
            ),
          ),
        },
      );
    }

    final theme = Theme.of(context);
    final style = EventStyle.of(context, entry.type);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final received = l.receivedAt(
      DateFormat.yMMMMEEEEd(locale).format(entry.createdAt),
      DateFormat.Hm(locale).format(entry.createdAt),
    );
    return Scaffold(
      appBar: AppBar(title: Text(style.label)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(Gap.l + 4, Gap.l, Gap.l + 4, Gap.xl),
        children: [
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: ExcludeSemantics(
              child: Container(
                width: 56,
                height: 56,
                decoration: BoxDecoration(color: style.background, shape: BoxShape.circle),
                child: Icon(style.icon, size: 30, color: style.foreground),
              ),
            ),
          ),
          const SizedBox(height: Gap.l),
          Semantics(header: true, child: Text(entry.title, style: theme.textTheme.headlineSmall)),
          const SizedBox(height: Gap.xs),
          Text(received, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const SizedBox(height: Gap.l),
          const Divider(),
          const SizedBox(height: Gap.l),
          Text(entry.body, style: theme.textTheme.bodyLarge?.copyWith(height: 1.5)),
          if (entry.type.aboutAttendance && entry.studentId != null) ...[
            const SizedBox(height: Gap.xl),
            FilledButton.tonalIcon(
              key: const ValueKey('open-history'),
              onPressed: () => context.push('/home/children/child/${entry.studentId}'),
              icon: const Icon(Icons.history_rounded),
              label: Text(l.seeHistory),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            ),
          ],
        ],
      ),
    );
  }
}
