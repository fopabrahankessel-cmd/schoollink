// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'SchoolLink';

  @override
  String get languageLabel => 'Language';

  @override
  String get loginTitle => 'Sign in';

  @override
  String get loginIntro => 'Use the phone number or email your school registered.';

  @override
  String get schoolLabel => 'School';

  @override
  String get schoolsLoadFailed => 'The list of schools could not be loaded.';

  @override
  String get retry => 'Retry';

  @override
  String get loginLabel => 'Phone number or email';

  @override
  String get passwordLabel => 'Password';

  @override
  String get showPassword => 'Show password';

  @override
  String get hidePassword => 'Hide password';

  @override
  String get signIn => 'Sign in';

  @override
  String get accountMenu => 'Account';

  @override
  String get signOut => 'Sign out';

  @override
  String get signOutPendingTitle => 'Records not sent yet';

  @override
  String signOutPendingBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count records are waiting to be sent.',
      one: '1 record is waiting to be sent.',
    );
    return '$_temp0 They will be sent when you sign in again on this phone.';
  }

  @override
  String get cancel => 'Cancel';

  @override
  String get fieldRequired => 'This field is required.';

  @override
  String get networkError => 'No connection to the server. Check your network and try again.';

  @override
  String get unexpectedError => 'Something went wrong. Try again.';

  @override
  String get changePasswordTitle => 'Choose your password';

  @override
  String get changePasswordIntro => 'Your current password is temporary. Choose your own password to continue.';

  @override
  String get temporaryPasswordLabel => 'Temporary password';

  @override
  String get newPasswordLabel => 'New password';

  @override
  String get newPasswordHelp => 'At least 8 characters.';

  @override
  String get confirmPasswordLabel => 'Confirm new password';

  @override
  String get passwordTooShort => 'Use at least 8 characters.';

  @override
  String get passwordTooLong => 'Use at most 72 characters.';

  @override
  String get passwordsDoNotMatch => 'The two passwords do not match.';

  @override
  String get savePassword => 'Save password';

  @override
  String get webOnlyTitle => 'Use the web dashboard';

  @override
  String get webOnlyBody => 'Your account works on the SchoolLink web dashboard. Open it on a computer.';

  @override
  String get navSessions => 'Attendance';

  @override
  String get navGate => 'Lateness';

  @override
  String get navAlerts => 'Alerts';

  @override
  String get navCases => 'Cases';

  @override
  String get navChildren => 'Children';

  @override
  String get navInbox => 'Notifications';

  @override
  String get comingSoon => 'This screen arrives in a coming update.';
}
