import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../auth/failure_text.dart';
import 'messages.dart';

/// UC-D4, D38: the messages the DM sent to parents, newest first, with what became of them. A new
/// one is written from the button; none can be edited or taken back.
class MessagesScreen extends ConsumerWidget {
  const MessagesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(l.messagesEntry)),
      body: switch (ref.watch(sentMessagesProvider)) {
        AsyncValue(value: final messages?) => RefreshIndicator(
          onRefresh: () => ref.refresh(sentMessagesProvider.future).then((_) {}, onError: (_) {}),
          child: messages.isEmpty
              ? ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: [EmptyState(icon: Icons.forum_outlined, title: l.noMessages, body: l.noMessagesBody)],
                )
              : ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  // room for the floating button over the last card
                  padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, 96),
                  children: [
                    for (final message in messages)
                      Padding(
                        padding: const EdgeInsets.only(bottom: Gap.m),
                        child: SentMessageCard(message: message),
                      ),
                  ],
                ),
        ),
        AsyncError(:final error) => FailureView(
          message: failureText(error, l),
          onRetry: () => ref.invalidate(sentMessagesProvider),
        ),
        _ => const Skeleton(child: SkeletonRows(count: 4)),
      },
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/home/alerts/write'),
        icon: const Icon(Icons.edit_outlined),
        label: Text(l.newMessage),
      ),
    );
  }
}

/// Who it went to, when, the text, and the delivery counts. No phone numbers (NFR-9).
class SentMessageCard extends StatelessWidget {
  const SentMessageCard({super.key, required this.message});

  final SentMessage message;

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final status = StatusColors.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final delivery = message.delivery;
    final (icon, audience) = switch (message.audience) {
      MessageAudience.student => (Icons.person_outline_rounded, message.studentName ?? ''),
      MessageAudience.schoolClass => (Icons.groups_outlined, message.className ?? ''),
      MessageAudience.school => (Icons.domain_rounded, l.messageToSchool),
    };
    final received = delivery.delivered;
    final inProgress = delivery.pending + delivery.sent;
    return Card(
      key: ValueKey('sent-${message.id}'),
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(Gap.l),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(icon, size: 20, color: scheme.primary),
                const SizedBox(width: Gap.s),
                Expanded(child: Text(audience, style: theme.textTheme.titleMedium)),
                Text(
                  '${DateFormat.MMMd(locale).format(message.createdAt)} ${DateFormat.Hm(locale).format(message.createdAt)}',
                  style: theme.textTheme.bodySmall?.copyWith(color: scheme.onSurfaceVariant, fontFeatures: tabular),
                ),
              ],
            ),
            const SizedBox(height: Gap.s),
            Text(message.body, maxLines: 3, overflow: TextOverflow.ellipsis, style: theme.textTheme.bodyLarge),
            const SizedBox(height: Gap.m),
            Wrap(
              spacing: Gap.s,
              runSpacing: Gap.xs,
              children: [
                StatusPill(
                  label: l.deliveryRecipients(delivery.recipients),
                  background: scheme.surfaceContainerHighest,
                  foreground: scheme.onSurface,
                ),
                if (delivery.opened > 0)
                  StatusPill(
                    label: l.deliveryOpened(delivery.opened),
                    background: status.presentContainer,
                    foreground: status.present,
                    icon: Icons.done_all_rounded,
                  ),
                if (received > 0)
                  StatusPill(
                    label: l.deliveryReceived(received),
                    background: scheme.secondaryContainer,
                    foreground: scheme.onSecondaryContainer,
                    icon: Icons.done_rounded,
                  ),
                if (inProgress > 0)
                  StatusPill(
                    label: l.deliveryPending(inProgress),
                    background: scheme.surfaceContainerHighest,
                    foreground: scheme.onSurfaceVariant,
                    icon: Icons.schedule_rounded,
                  ),
                if (delivery.failed > 0)
                  StatusPill(
                    label: l.deliveryFailed(delivery.failed),
                    background: status.absentContainer,
                    foreground: status.absent,
                    icon: Icons.error_outline_rounded,
                  ),
                if (message.sms)
                  StatusPill(
                    label: l.messageWithSms,
                    background: scheme.surfaceContainerHighest,
                    foreground: scheme.onSurfaceVariant,
                    icon: Icons.sms_outlined,
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
