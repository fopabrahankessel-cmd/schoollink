import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../core/ui/skeleton.dart';
import '../../core/ui/widgets.dart';
import '../../l10n/app_localizations.dart';
import '../lateness/lateness.dart';

/// D35: the DM finds a student of their classes by name or matricule, to open the history and
/// justify, cancel or summon from it (UC-D1, UC-D3, UC-D6, UC-D8). The list is the gate search's:
/// the whole scope, searched on the phone.
class StudentSearchScreen extends ConsumerStatefulWidget {
  const StudentSearchScreen({super.key});

  @override
  ConsumerState<StudentSearchScreen> createState() => _StudentSearchScreenState();
}

class _StudentSearchScreenState extends ConsumerState<StudentSearchScreen> {
  static const _maxResults = 30;

  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final students = ref.watch(gateStudentsProvider);
    final query = _search.text;
    final results = query.trim().isEmpty
        ? const <GateStudent>[]
        : (students.value ?? const <GateStudent>[]).where((s) => s.matches(query)).toList();

    return Scaffold(
      appBar: AppBar(title: Text(l.findStudent)),
      body: ListView(
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.fromLTRB(Gap.l, Gap.l, Gap.l, Gap.xl),
        children: [
          TextField(
            key: const ValueKey('dm-search'),
            controller: _search,
            autofocus: true,
            onChanged: (_) => setState(() {}),
            textInputAction: TextInputAction.search,
            style: theme.textTheme.titleMedium,
            decoration: InputDecoration(
              hintText: l.searchStudentHint,
              prefixIcon: const Icon(Icons.search_rounded),
              filled: true,
              fillColor: scheme.surfaceContainerLowest,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(Radii.m)),
            ),
          ),
          if (students is AsyncError && results.isEmpty)
            EmptyState(
              icon: Icons.cloud_download_outlined,
              title: l.gateStudentsUnavailable,
              action: OutlinedButton.icon(
                onPressed: () => ref.invalidate(gateStudentsProvider),
                icon: const Icon(Icons.refresh_rounded),
                label: Text(l.retry),
                style: OutlinedButton.styleFrom(minimumSize: const Size(160, 48)),
              ),
            )
          else if (students is AsyncLoading && results.isEmpty)
            const Padding(
              padding: EdgeInsets.only(top: Gap.l),
              child: Skeleton(child: SkeletonRows(count: 4, trailing: false)),
            )
          else if (query.trim().isNotEmpty && results.isEmpty)
            EmptyState(icon: Icons.person_search_outlined, title: l.noStudentFound)
          else if (results.isNotEmpty) ...[
            SectionHeader(title: l.searchResults(results.length)),
            Card(
              child: Column(
                children: [
                  for (final (i, s) in results.take(_maxResults).indexed) ...[
                    if (i > 0) const Divider(indent: 68),
                    ListTile(
                      key: ValueKey('student-${s.id}'),
                      minVerticalPadding: Gap.m,
                      leading: InitialsAvatar(firstName: s.firstName, lastName: s.lastName),
                      title: Text(s.fullName, style: theme.textTheme.titleMedium),
                      subtitle: Text(l.classAndMatricule(s.className, s.matricule)),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: () => context.push('/home/alerts/student/${s.id}'),
                    ),
                  ],
                ],
              ),
            ),
            if (results.length > _maxResults)
              Padding(
                padding: const EdgeInsets.all(Gap.m),
                child: Text(
                  l.typeMore,
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
                ),
              ),
          ],
        ],
      ),
    );
  }
}
