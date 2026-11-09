import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:schoollink/core/storage/device_store.dart';
import 'package:schoollink/features/parent/contacts.dart';

import '../support/fake_server.dart';
import '../support/harness.dart';

/// UC-P5, D39: on a child's page, the Contacts tab lists the class's teachers with their subjects
/// and the block's DMs, with call and SMS buttons that open the phone's own apps. The numbers never
/// go to the server in a URL (NFR-9). Names and numbers are made up.
void main() {
  late FakeServer server;
  late MemoryDeviceStore store;
  late List<Uri> launched;
  late bool canLaunch;
  final parent = userJson(roles: ['PARENT']);
  final now = DateTime(2026, 11, 2, 18);

  final contacts = {
    'className': '6e A',
    'teachers': [
      {'name': 'Paul Enseignant', 'subjects': ['Maths', 'Physique'], 'phone': '+237690000010'},
      {'name': 'Rose Lettres', 'subjects': ['Français'], 'phone': null},
    ],
    'discipline': [
      {'name': 'Marie Surveillante', 'role': 'DM', 'phone': '+237690000020'},
      {'name': 'Jean Adjoint', 'role': 'ASSISTANT_DM', 'phone': null},
    ],
  };

  setUp(() {
    launched = [];
    canLaunch = true;
    store = MemoryDeviceStore(signedIn(parent));
    server = FakeServer()
      ..on('GET /me', (_) => Reply.ok(parent))
      ..on(
        'GET /me/children',
        (_) => const Reply.ok([
          {'id': 'st-1', 'firstName': 'Awa', 'lastName': 'Fictif', 'classId': 'c-1', 'className': '6e A'},
        ]),
      )
      ..on('GET /me/notifications', (_) => const Reply.ok(<Object>[]))
      ..on(
        'GET /children/st-1/history',
        (_) => const Reply.ok({
          'studentId': 'st-1',
          'firstName': 'Awa',
          'from': '2026-08-04',
          'to': '2026-11-02',
          'totals': {'absentPeriods': 0, 'unjustifiedAbsentPeriods': 0, 'lateness': 0, 'unjustifiedLateness': 0},
          'days': <Object>[],
        }),
      )
      ..on('GET /me/children/st-1/contacts', (_) => Reply.ok(contacts));
  });

  Future<void> openContacts(WidgetTester tester, {String language = 'fr'}) async {
    tester.view
      ..physicalSize = const Size(1080, 2340)
      ..devicePixelRatio = 2.75;
    addTearDown(tester.view.reset);
    await pumpApp(
      tester,
      server: server,
      store: store,
      clock: () => now,
      language: language,
      overrides: [
        phoneLauncherProvider.overrideWithValue((uri) async {
          launched.add(uri);
          return canLaunch;
        }),
      ],
    );
    await tester.tap(find.byKey(const ValueKey('child-st-1')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(Tab, 'Contacts'));
    await tester.pumpAndSettle();
  }

  testWidgets('the child page has a Contacts tab with teachers, subjects and the DMs (UC-P5)', (tester) async {
    await openContacts(tester);

    expect(find.text('Paul Enseignant'), findsOneWidget);
    expect(find.text('Maths, Physique'), findsOneWidget);
    expect(find.text('Marie Surveillante'), findsOneWidget);
    expect(find.text('Surveillant général'), findsOneWidget);
    expect(find.text('Surveillant général adjoint'), findsOneWidget);
    // the two people without a number
    expect(find.textContaining('Pas de numéro'), findsNWidgets(2));
    expect(find.byTooltip('Appeler Jean Adjoint'), findsNothing);
    // grouped like the sign-in screen, easier to read aloud or copy by hand
    expect(find.text('+237 690 00 00 20'), findsOneWidget);
  });

  test('displayPhone groups a Cameroon number and leaves any other as it is', () {
    expect(displayPhone('+237690000020'), '+237 690 00 00 20');
    expect(displayPhone('+33612345678'), '+33612345678');
  });

  testWidgets('call and SMS open the phone with the number, never sent to the server (NFR-9)', (tester) async {
    await openContacts(tester);

    await tester.tap(find.byTooltip('Appeler Marie Surveillante'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Envoyer un SMS à Marie Surveillante'));
    await tester.pumpAndSettle();

    expect(launched, [Uri.parse('tel:+237690000020'), Uri.parse('sms:+237690000020')]);
    expect(server.requests.every((r) => !r.path.contains('690000020')), isTrue);
  });

  testWidgets('a block with no DM yet says so', (tester) async {
    server.on(
      'GET /me/children/st-1/contacts',
      (_) => Reply.ok({...contacts, 'discipline': <Object>[]}),
    );
    await openContacts(tester);

    expect(find.text('Aucun surveillant pour cette classe pour le moment.'), findsOneWidget);
    expect(find.text('Paul Enseignant'), findsOneWidget);
  });

  testWidgets('offline, the contacts saved on the phone show', (tester) async {
    await openContacts(tester);
    // the app bar's back arrow (pageBack looks for the English tooltip only)
    await tester.tap(find.byType(BackButton));
    await tester.pumpAndSettle();

    server.offline = true;
    await tester.tap(find.byKey(const ValueKey('child-st-1')));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(Tab, 'Contacts'));
    await tester.pumpAndSettle();

    expect(find.text('Marie Surveillante'), findsOneWidget);
    expect(find.textContaining('Pas de connexion.'), findsOneWidget);
  });

  testWidgets('a phone that cannot call shows a message', (tester) async {
    canLaunch = false;
    await openContacts(tester);

    await tester.tap(find.byTooltip('Appeler Paul Enseignant'));
    await tester.pumpAndSettle();

    expect(find.text("Impossible d'ouvrir l'application Téléphone."), findsOneWidget);
  });

  testWidgets('the contacts read in English (NFR-12)', (tester) async {
    final english = userJson(roles: ['PARENT'], language: 'EN');
    store = MemoryDeviceStore(signedIn(english));
    server.on('GET /me', (_) => Reply.ok(english));
    await openContacts(tester, language: 'en');

    expect(find.text('Teachers'), findsOneWidget);
    expect(find.text('Discipline Master'), findsOneWidget);
    expect(find.text('Assistant Discipline Master'), findsOneWidget);
    expect(find.textContaining('No number'), findsNWidgets(2));
    expect(find.byTooltip('Call Paul Enseignant'), findsOneWidget);
  });
}
