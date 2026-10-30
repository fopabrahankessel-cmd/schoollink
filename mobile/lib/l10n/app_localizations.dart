import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_fr.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale) : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates = <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('en'), Locale('fr')];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'SchoolLink'**
  String get appTitle;

  /// No description provided for @languageLabel.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get languageLabel;

  /// No description provided for @loginTitle.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get loginTitle;

  /// No description provided for @loginIntro.
  ///
  /// In en, this message translates to:
  /// **'Use the phone number or email your school registered.'**
  String get loginIntro;

  /// No description provided for @schoolLabel.
  ///
  /// In en, this message translates to:
  /// **'School'**
  String get schoolLabel;

  /// No description provided for @schoolsLoadFailed.
  ///
  /// In en, this message translates to:
  /// **'The list of schools could not be loaded.'**
  String get schoolsLoadFailed;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @loginLabel.
  ///
  /// In en, this message translates to:
  /// **'Phone number or email'**
  String get loginLabel;

  /// No description provided for @passwordLabel.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get passwordLabel;

  /// No description provided for @showPassword.
  ///
  /// In en, this message translates to:
  /// **'Show password'**
  String get showPassword;

  /// No description provided for @hidePassword.
  ///
  /// In en, this message translates to:
  /// **'Hide password'**
  String get hidePassword;

  /// No description provided for @signIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get signIn;

  /// No description provided for @accountMenu.
  ///
  /// In en, this message translates to:
  /// **'Account'**
  String get accountMenu;

  /// No description provided for @signOut.
  ///
  /// In en, this message translates to:
  /// **'Sign out'**
  String get signOut;

  /// No description provided for @signOutPendingTitle.
  ///
  /// In en, this message translates to:
  /// **'Records not sent yet'**
  String get signOutPendingTitle;

  /// D22: signing out with records saved offline.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 record is waiting to be sent.} other{{count} records are waiting to be sent.}} They will be sent when you sign in again on this phone.'**
  String signOutPendingBody(int count);

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @fieldRequired.
  ///
  /// In en, this message translates to:
  /// **'This field is required.'**
  String get fieldRequired;

  /// No description provided for @networkError.
  ///
  /// In en, this message translates to:
  /// **'No connection to the server. Check your network and try again.'**
  String get networkError;

  /// No description provided for @unexpectedError.
  ///
  /// In en, this message translates to:
  /// **'Something went wrong. Try again.'**
  String get unexpectedError;

  /// No description provided for @changePasswordTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose your password'**
  String get changePasswordTitle;

  /// No description provided for @changePasswordIntro.
  ///
  /// In en, this message translates to:
  /// **'Your current password is temporary. Choose your own password to continue.'**
  String get changePasswordIntro;

  /// No description provided for @temporaryPasswordLabel.
  ///
  /// In en, this message translates to:
  /// **'Temporary password'**
  String get temporaryPasswordLabel;

  /// No description provided for @newPasswordLabel.
  ///
  /// In en, this message translates to:
  /// **'New password'**
  String get newPasswordLabel;

  /// No description provided for @newPasswordHelp.
  ///
  /// In en, this message translates to:
  /// **'At least 8 characters.'**
  String get newPasswordHelp;

  /// No description provided for @confirmPasswordLabel.
  ///
  /// In en, this message translates to:
  /// **'Confirm new password'**
  String get confirmPasswordLabel;

  /// No description provided for @passwordTooShort.
  ///
  /// In en, this message translates to:
  /// **'Use at least 8 characters.'**
  String get passwordTooShort;

  /// No description provided for @passwordTooLong.
  ///
  /// In en, this message translates to:
  /// **'Use at most 72 characters.'**
  String get passwordTooLong;

  /// No description provided for @passwordsDoNotMatch.
  ///
  /// In en, this message translates to:
  /// **'The two passwords do not match.'**
  String get passwordsDoNotMatch;

  /// No description provided for @savePassword.
  ///
  /// In en, this message translates to:
  /// **'Save password'**
  String get savePassword;

  /// No description provided for @webOnlyTitle.
  ///
  /// In en, this message translates to:
  /// **'Use the web dashboard'**
  String get webOnlyTitle;

  /// No description provided for @webOnlyBody.
  ///
  /// In en, this message translates to:
  /// **'Your account works on the SchoolLink web dashboard. Open it on a computer.'**
  String get webOnlyBody;

  /// Teacher tab: today's sessions to record (UC-T1, UC-T3).
  ///
  /// In en, this message translates to:
  /// **'Attendance'**
  String get navSessions;

  /// Assistant DM tab: lateness at the gate (UC-AD1, UC-AD2).
  ///
  /// In en, this message translates to:
  /// **'Lateness'**
  String get navGate;

  /// DM tab: risk alerts to decide (UC-D2).
  ///
  /// In en, this message translates to:
  /// **'Alerts'**
  String get navAlerts;

  /// Counsellor tab: referred students and cases (UC-C1).
  ///
  /// In en, this message translates to:
  /// **'Cases'**
  String get navCases;

  /// Parent tab: their children and attendance history (UC-P3).
  ///
  /// In en, this message translates to:
  /// **'Children'**
  String get navChildren;

  /// Parent tab: absence, lateness and summons notifications (UC-P2).
  ///
  /// In en, this message translates to:
  /// **'Notifications'**
  String get navInbox;

  /// No description provided for @comingSoon.
  ///
  /// In en, this message translates to:
  /// **'This screen arrives in a coming update.'**
  String get comingSoon;

  /// No description provided for @sessionToRecord.
  ///
  /// In en, this message translates to:
  /// **'To record'**
  String get sessionToRecord;

  /// UC-T3: a session already recorded, with its absentees (BR-15: cancelled ones not counted).
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{Recorded, no absences} =1{Recorded, 1 absent} other{Recorded, {count} absent}}'**
  String sessionRecorded(int count);

  /// No description provided for @sessionNow.
  ///
  /// In en, this message translates to:
  /// **'Now'**
  String get sessionNow;

  /// No description provided for @sessionTimes.
  ///
  /// In en, this message translates to:
  /// **'{start} to {end}'**
  String sessionTimes(String start, String end);

  /// No description provided for @notASchoolDay.
  ///
  /// In en, this message translates to:
  /// **'No classes today: it is not a school day.'**
  String get notASchoolDay;

  /// No description provided for @noLessonsToday.
  ///
  /// In en, this message translates to:
  /// **'You have no classes in the timetable today.'**
  String get noLessonsToday;

  /// NFR-3: the list comes from the phone's copy.
  ///
  /// In en, this message translates to:
  /// **'No connection. Shown as last updated at {time}.'**
  String offlineSince(String time);
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>['en', 'fr'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'fr':
      return AppLocalizationsFr();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
