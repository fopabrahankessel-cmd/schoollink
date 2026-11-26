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

  /// DM tab: the day's counts, alerts to decide, missing roll calls, absences (D41).
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get navToday;

  /// DM tab: find a student and open the student page (D41).
  ///
  /// In en, this message translates to:
  /// **'Students'**
  String get navStudents;

  /// DM tab: messages sent to parents (D41).
  ///
  /// In en, this message translates to:
  /// **'Messages'**
  String get navMessages;

  /// DM tab: decided alerts, dashboard, settings, sign out, and tabs that did not fit (D41).
  ///
  /// In en, this message translates to:
  /// **'More'**
  String get navMore;

  /// Today tab: open alerts of the DM's classes (D41, UC-D2).
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No alert to decide} =1{1 alert to decide} other{{count} alerts to decide}}'**
  String todayAlerts(int count);

  /// Today tab: timetabled periods started today with no roll call yet (D41, UC-D10).
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{Every period recorded} =1{1 period not recorded} other{{count} periods not recorded}}'**
  String todayNotRecorded(int count);

  /// Today tab: students absent today and lateness today (D41, UC-D10).
  ///
  /// In en, this message translates to:
  /// **'{absent} absent · {late} late'**
  String todayAbsences(int absent, int late);

  /// Screen title: the open alerts (UC-D2).
  ///
  /// In en, this message translates to:
  /// **'Alerts to decide'**
  String get openAlertsTitle;

  /// Today tab: heading above the full-day report entry (UC-D9).
  ///
  /// In en, this message translates to:
  /// **'Day report'**
  String get reportSectionTitle;

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

  /// No description provided for @attendanceHint.
  ///
  /// In en, this message translates to:
  /// **'Tap the students who are absent.'**
  String get attendanceHint;

  /// No description provided for @presentCount.
  ///
  /// In en, this message translates to:
  /// **'Present: {count}'**
  String presentCount(int count);

  /// No description provided for @absentCount.
  ///
  /// In en, this message translates to:
  /// **'Absent: {count}'**
  String absentCount(int count);

  /// No description provided for @absentLabel.
  ///
  /// In en, this message translates to:
  /// **'Absent'**
  String get absentLabel;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// UC-T1 step 4, first confirmation (BR-3).
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No student is marked absent. Correct?} =1{This student is marked absent. Correct?} other{These {count} students are marked absent. Correct?}}'**
  String confirmAbsentees(int count);

  /// No description provided for @back.
  ///
  /// In en, this message translates to:
  /// **'Back'**
  String get back;

  /// No description provided for @yes.
  ///
  /// In en, this message translates to:
  /// **'Yes'**
  String get yes;

  /// UC-T1 step 5, second confirmation (BR-3).
  ///
  /// In en, this message translates to:
  /// **'Confirm and notify parents?'**
  String get confirmNotifyTitle;

  /// No description provided for @confirmNotifyBody.
  ///
  /// In en, this message translates to:
  /// **'Once confirmed, the session is locked and can no longer be changed.'**
  String get confirmNotifyBody;

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// BR-4: a confirmed session never changes.
  ///
  /// In en, this message translates to:
  /// **'Locked: recorded at {time}.'**
  String sessionLockedAt(String time);

  /// No description provided for @absentStudents.
  ///
  /// In en, this message translates to:
  /// **'Absent students'**
  String get absentStudents;

  /// No description provided for @noAbsentStudents.
  ///
  /// In en, this message translates to:
  /// **'No student was absent.'**
  String get noAbsentStudents;

  /// No description provided for @absentTotal.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No student was absent.} =1{1 student was absent.} other{{count} students were absent.}}'**
  String absentTotal(int count);

  /// No description provided for @syncWaiting.
  ///
  /// In en, this message translates to:
  /// **'Saved on this phone, waiting to be sent.'**
  String get syncWaiting;

  /// No description provided for @syncSent.
  ///
  /// In en, this message translates to:
  /// **'Sent to the school.'**
  String get syncSent;

  /// No description provided for @syncNotAccepted.
  ///
  /// In en, this message translates to:
  /// **'Not accepted by the school.'**
  String get syncNotAccepted;

  /// No description provided for @leaveTitle.
  ///
  /// In en, this message translates to:
  /// **'Leave without saving?'**
  String get leaveTitle;

  /// No description provided for @leaveBody.
  ///
  /// In en, this message translates to:
  /// **'The absences you marked will be lost.'**
  String get leaveBody;

  /// No description provided for @leave.
  ///
  /// In en, this message translates to:
  /// **'Leave'**
  String get leave;

  /// No description provided for @rosterUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The class list is not on this phone yet. Connect to the network once to load it.'**
  String get rosterUnavailable;

  /// No description provided for @sessionNotFound.
  ///
  /// In en, this message translates to:
  /// **'This session is not in today\'s list.'**
  String get sessionNotFound;

  /// No description provided for @signedInAs.
  ///
  /// In en, this message translates to:
  /// **'Signed in as'**
  String get signedInAs;

  /// No description provided for @roleAdmin.
  ///
  /// In en, this message translates to:
  /// **'Administrator'**
  String get roleAdmin;

  /// No description provided for @roleTeacher.
  ///
  /// In en, this message translates to:
  /// **'Teacher'**
  String get roleTeacher;

  /// No description provided for @roleAssistantDm.
  ///
  /// In en, this message translates to:
  /// **'Assistant discipline master'**
  String get roleAssistantDm;

  /// No description provided for @roleDm.
  ///
  /// In en, this message translates to:
  /// **'Discipline master'**
  String get roleDm;

  /// No description provided for @roleCounsellor.
  ///
  /// In en, this message translates to:
  /// **'Counsellor'**
  String get roleCounsellor;

  /// No description provided for @roleParent.
  ///
  /// In en, this message translates to:
  /// **'Parent'**
  String get roleParent;

  /// No description provided for @roleResearcher.
  ///
  /// In en, this message translates to:
  /// **'Researcher'**
  String get roleResearcher;

  /// BR-6, UC-T2: records saved on the phone, network available.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 record waiting to be sent.} other{{count} records waiting to be sent.}}'**
  String pendingOnline(int count);

  /// BR-6, UC-T2: records saved on the phone, no network.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{No network: 1 record will be sent when it is back.} other{No network: {count} records will be sent when it is back.}}'**
  String pendingOffline(int count);

  /// No description provided for @sendNow.
  ///
  /// In en, this message translates to:
  /// **'Send now'**
  String get sendNow;

  /// No description provided for @todaySchedule.
  ///
  /// In en, this message translates to:
  /// **'Today\'s timetable'**
  String get todaySchedule;

  /// No description provided for @noRollToday.
  ///
  /// In en, this message translates to:
  /// **'No roll call today'**
  String get noRollToday;

  /// UC-T3: sessions of the day still to record.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{All rolls are done} =1{1 roll call to do} other{{count} roll calls to do}}'**
  String rollsLeft(int count);

  /// No description provided for @rollsProgress.
  ///
  /// In en, this message translates to:
  /// **'{done} of {total} recorded'**
  String rollsProgress(int done, int total);

  /// No description provided for @takeRoll.
  ///
  /// In en, this message translates to:
  /// **'Take the roll'**
  String get takeRoll;

  /// No description provided for @findStudent.
  ///
  /// In en, this message translates to:
  /// **'Find a student'**
  String get findStudent;

  /// UC-AD2: lateness of the day in scope.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No lateness today} =1{1 late arrival today} other{{count} late arrivals today}}'**
  String latenessToday(int count);

  /// No description provided for @classesStartAt.
  ///
  /// In en, this message translates to:
  /// **'Classes start at {time}'**
  String classesStartAt(String time);

  /// No description provided for @searchStudentHint.
  ///
  /// In en, this message translates to:
  /// **'Name or matricule'**
  String get searchStudentHint;

  /// No description provided for @clearSearch.
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get clearSearch;

  /// No description provided for @gateStudentsUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The student list is not on this phone yet. Connect to the network once to load it.'**
  String get gateStudentsUnavailable;

  /// No description provided for @noStudentFound.
  ///
  /// In en, this message translates to:
  /// **'No student matches this search.'**
  String get noStudentFound;

  /// No description provided for @searchResults.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 student} other{{count} students}}'**
  String searchResults(int count);

  /// No description provided for @typeMore.
  ///
  /// In en, this message translates to:
  /// **'Type more letters to narrow the list.'**
  String get typeMore;

  /// No description provided for @latenessListTitle.
  ///
  /// In en, this message translates to:
  /// **'Today\'s lateness'**
  String get latenessListTitle;

  /// No description provided for @noLatenessToday.
  ///
  /// In en, this message translates to:
  /// **'No lateness recorded today.'**
  String get noLatenessToday;

  /// No description provided for @noLatenessTodayHint.
  ///
  /// In en, this message translates to:
  /// **'Search a student above to record their arrival.'**
  String get noLatenessTodayHint;

  /// No description provided for @classAndMatricule.
  ///
  /// In en, this message translates to:
  /// **'{className}, no. {matricule}'**
  String classAndMatricule(String className, String matricule);

  /// BR-7: one lateness per student per day.
  ///
  /// In en, this message translates to:
  /// **'Late at {time}'**
  String alreadyLateAt(String time);

  /// No description provided for @arrivalTimeLabel.
  ///
  /// In en, this message translates to:
  /// **'Arrival time'**
  String get arrivalTimeLabel;

  /// No description provided for @changeTime.
  ///
  /// In en, this message translates to:
  /// **'Change'**
  String get changeTime;

  /// BR-7.
  ///
  /// In en, this message translates to:
  /// **'Classes start at {start}: arriving at that time or before is not late.'**
  String arrivalNotLate(String start);

  /// No description provided for @arrivalInFuture.
  ///
  /// In en, this message translates to:
  /// **'The arrival time cannot be later than now.'**
  String get arrivalInFuture;

  /// BR-8, BR-9.
  ///
  /// In en, this message translates to:
  /// **'Recorded apart from the class roll: it does not change it.'**
  String get latenessIndependent;

  /// No description provided for @recordLateness.
  ///
  /// In en, this message translates to:
  /// **'Record lateness'**
  String get recordLateness;

  /// No description provided for @latenessSaved.
  ///
  /// In en, this message translates to:
  /// **'Lateness recorded: {name} at {time}.'**
  String latenessSaved(String name, String time);

  /// No description provided for @latenessWaiting.
  ///
  /// In en, this message translates to:
  /// **'On this phone, waiting to be sent'**
  String get latenessWaiting;

  /// No description provided for @latenessRefused.
  ///
  /// In en, this message translates to:
  /// **'Not accepted by the school: {name} at {time}.'**
  String latenessRefused(String name, String time);

  /// No description provided for @justifiedLabel.
  ///
  /// In en, this message translates to:
  /// **'Justified'**
  String get justifiedLabel;

  /// No description provided for @inYourClasses.
  ///
  /// In en, this message translates to:
  /// **'In the classes you are in charge of'**
  String get inYourClasses;

  /// No description provided for @alertsTitle.
  ///
  /// In en, this message translates to:
  /// **'Alerts to decide'**
  String get alertsTitle;

  /// BR-21: Android notification channel, shown in the phone's settings.
  ///
  /// In en, this message translates to:
  /// **'Absences and lateness'**
  String get notificationChannelName;

  /// No description provided for @notificationChannelDescription.
  ///
  /// In en, this message translates to:
  /// **'Absences, lateness and summons of your children, and school alerts.'**
  String get notificationChannelDescription;

  /// No description provided for @parentQuestion.
  ///
  /// In en, this message translates to:
  /// **'Parent?'**
  String get parentQuestion;

  /// No description provided for @parentSignInStep.
  ///
  /// In en, this message translates to:
  /// **'Step {step} of 2'**
  String parentSignInStep(int step);

  /// No description provided for @phoneLabel.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get phoneLabel;

  /// No description provided for @phoneHelp.
  ///
  /// In en, this message translates to:
  /// **'The number you gave the school'**
  String get phoneHelp;

  /// No description provided for @phoneInvalid.
  ///
  /// In en, this message translates to:
  /// **'Enter a Cameroon number with 9 digits, for example 6 99 00 00 01.'**
  String get phoneInvalid;

  /// No description provided for @sendSmsCode.
  ///
  /// In en, this message translates to:
  /// **'Receive a code by SMS'**
  String get sendSmsCode;

  /// No description provided for @otpTitle.
  ///
  /// In en, this message translates to:
  /// **'Enter the code and choose your password'**
  String get otpTitle;

  /// No description provided for @otpLabel.
  ///
  /// In en, this message translates to:
  /// **'Verification code'**
  String get otpLabel;

  /// No description provided for @otpInvalid.
  ///
  /// In en, this message translates to:
  /// **'Enter the 6 digits of the code.'**
  String get otpInvalid;

  /// No description provided for @otpValidUntil.
  ///
  /// In en, this message translates to:
  /// **'The code works until {time}.'**
  String otpValidUntil(String time);

  /// No description provided for @otpResent.
  ///
  /// In en, this message translates to:
  /// **'A new code was sent. It works until {time}.'**
  String otpResent(String time);

  /// No description provided for @resendCode.
  ///
  /// In en, this message translates to:
  /// **'Send a new code'**
  String get resendCode;

  /// No description provided for @changeNumber.
  ///
  /// In en, this message translates to:
  /// **'Change the number'**
  String get changeNumber;

  /// UC-P1, D45: a parent's first sign-in or forgotten password, by SMS code; otherwise phone and password.
  ///
  /// In en, this message translates to:
  /// **'First sign-in or forgotten password'**
  String get parentSignInWithSms;

  /// No description provided for @parentSignInTitle.
  ///
  /// In en, this message translates to:
  /// **'Parent sign-in'**
  String get parentSignInTitle;

  /// No description provided for @parentSignInHeadline.
  ///
  /// In en, this message translates to:
  /// **'Your phone number'**
  String get parentSignInHeadline;

  /// No description provided for @parentSignInIntro.
  ///
  /// In en, this message translates to:
  /// **'Choose your school and enter the number you gave it. We will send you a code by SMS to choose your password.'**
  String get parentSignInIntro;

  /// D24, NFR-7: the server does not say whether the school knows the number.
  ///
  /// In en, this message translates to:
  /// **'If the school knows {phone}, a 6-digit code was sent to it by SMS.'**
  String otpSentIfKnown(String phone);

  /// Read by screen readers while a skeleton stands in for content.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loading;

  /// No description provided for @today.
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get today;

  /// No description provided for @yesterday.
  ///
  /// In en, this message translates to:
  /// **'Yesterday'**
  String get yesterday;

  /// UC-P2: the inbox header.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{All read} =1{1 unread message} other{{count} unread messages}}'**
  String unreadMessages(int count);

  /// Read by screen readers on an unread message.
  ///
  /// In en, this message translates to:
  /// **'Unread'**
  String get unread;

  /// No description provided for @inboxEmpty.
  ///
  /// In en, this message translates to:
  /// **'No messages yet'**
  String get inboxEmpty;

  /// No description provided for @inboxEmptyHint.
  ///
  /// In en, this message translates to:
  /// **'Your children\'s absences and lateness will appear here, the same day.'**
  String get inboxEmptyHint;

  /// No description provided for @olderMessages.
  ///
  /// In en, this message translates to:
  /// **'Show older messages'**
  String get olderMessages;

  /// No description provided for @olderMessagesFailed.
  ///
  /// In en, this message translates to:
  /// **'Older messages could not be loaded. Check your connection and try again.'**
  String get olderMessagesFailed;

  /// No description provided for @messageNotFound.
  ///
  /// In en, this message translates to:
  /// **'This message is no longer in your notifications.'**
  String get messageNotFound;

  /// No description provided for @backToInbox.
  ///
  /// In en, this message translates to:
  /// **'Back to notifications'**
  String get backToInbox;

  /// No description provided for @receivedAt.
  ///
  /// In en, this message translates to:
  /// **'Received {date} at {time}'**
  String receivedAt(String date, String time);

  /// No description provided for @seeHistory.
  ///
  /// In en, this message translates to:
  /// **'See absences and lateness'**
  String get seeHistory;

  /// No description provided for @eventAbsence.
  ///
  /// In en, this message translates to:
  /// **'Absence'**
  String get eventAbsence;

  /// No description provided for @eventLateness.
  ///
  /// In en, this message translates to:
  /// **'Lateness'**
  String get eventLateness;

  /// An absence cancelled after the parent was told (BR-14).
  ///
  /// In en, this message translates to:
  /// **'Correction'**
  String get eventCorrection;

  /// No description provided for @eventSummons.
  ///
  /// In en, this message translates to:
  /// **'Summons'**
  String get eventSummons;

  /// No description provided for @eventMessage.
  ///
  /// In en, this message translates to:
  /// **'Message'**
  String get eventMessage;

  /// No description provided for @eventAnnouncement.
  ///
  /// In en, this message translates to:
  /// **'Announcement'**
  String get eventAnnouncement;

  /// No description provided for @eventOther.
  ///
  /// In en, this message translates to:
  /// **'Notification'**
  String get eventOther;

  /// UC-P3: the children header.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No child} =1{Your child} other{Your {count} children}}'**
  String yourChildren(int count);

  /// No description provided for @childrenHint.
  ///
  /// In en, this message translates to:
  /// **'Tap a child to see their absences and lateness.'**
  String get childrenHint;

  /// No description provided for @noChildren.
  ///
  /// In en, this message translates to:
  /// **'No child is linked to your account'**
  String get noChildren;

  /// No description provided for @noChildrenHint.
  ///
  /// In en, this message translates to:
  /// **'Contact the school to check the phone number it has for you.'**
  String get noChildrenHint;

  /// No description provided for @classLabel.
  ///
  /// In en, this message translates to:
  /// **'Class: {name}'**
  String classLabel(String name);

  /// No description provided for @childLeftOn.
  ///
  /// In en, this message translates to:
  /// **'Left the school on {date}'**
  String childLeftOn(String date);

  /// No description provided for @historyTitle.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get historyTitle;

  /// UC-P3: the period shown, last 90 days.
  ///
  /// In en, this message translates to:
  /// **'From {from} to {to}'**
  String periodFromTo(String from, String to);

  /// Under the number of absent periods.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{lesson missed} other{lessons missed}}'**
  String absentPeriodsLabel(int count);

  /// No description provided for @unjustifiedAbsences.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{none unjustified} =1{1 not justified} other{{count} not justified}}'**
  String unjustifiedAbsences(int count);

  /// Under the number of times late.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{time late} other{times late}}'**
  String latenessLabel(int count);

  /// No description provided for @unjustifiedLateness.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{none unjustified} =1{1 not justified} other{{count} not justified}}'**
  String unjustifiedLateness(int count);

  /// No description provided for @dayByDay.
  ///
  /// In en, this message translates to:
  /// **'Day by day'**
  String get dayByDay;

  /// No description provided for @historyEmpty.
  ///
  /// In en, this message translates to:
  /// **'No absence or lateness for {name}'**
  String historyEmpty(String name);

  /// No description provided for @absentFromLesson.
  ///
  /// In en, this message translates to:
  /// **'Lesson at {time}'**
  String absentFromLesson(String time);

  /// No description provided for @lateArrivedAt.
  ///
  /// In en, this message translates to:
  /// **'Arrived at {time}'**
  String lateArrivedAt(String time);

  /// No description provided for @absenceJustified.
  ///
  /// In en, this message translates to:
  /// **'Justified'**
  String get absenceJustified;

  /// No description provided for @absenceUnjustified.
  ///
  /// In en, this message translates to:
  /// **'Not justified'**
  String get absenceUnjustified;

  /// No description provided for @latenessJustified.
  ///
  /// In en, this message translates to:
  /// **'Justified'**
  String get latenessJustified;

  /// No description provided for @latenessUnjustified.
  ///
  /// In en, this message translates to:
  /// **'Not justified'**
  String get latenessUnjustified;

  /// DM home header: open risk alerts of their classes (UC-D2).
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{No alert to decide} =1{1 alert to decide} other{{count} alerts to decide}}'**
  String alertsToDecide(int count);

  /// No description provided for @noAlertsTitle.
  ///
  /// In en, this message translates to:
  /// **'No alert to decide'**
  String get noAlertsTitle;

  /// BR-16 to BR-19: rules only raise alerts; the DM decides.
  ///
  /// In en, this message translates to:
  /// **'Repeated absences and lateness in your classes show here for your decision. The app never decides for you.'**
  String get noAlertsBody;

  /// No description provided for @ruleFullDayAbsence.
  ///
  /// In en, this message translates to:
  /// **'Absent all day'**
  String get ruleFullDayAbsence;

  /// No description provided for @ruleConsecutiveLateness.
  ///
  /// In en, this message translates to:
  /// **'Late day after day'**
  String get ruleConsecutiveLateness;

  /// No description provided for @ruleUnjustifiedAbsences.
  ///
  /// In en, this message translates to:
  /// **'Unjustified absences'**
  String get ruleUnjustifiedAbsences;

  /// No description provided for @ruleOther.
  ///
  /// In en, this message translates to:
  /// **'Risk alert'**
  String get ruleOther;

  /// D2: what the full-day absence rule found.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{Absent from every lesson on {dates}} other{Absent from every lesson {count} days in a row: {dates}}}'**
  String evidenceFullDay(int count, String dates);

  /// D3: what the consecutive lateness rule found.
  ///
  /// In en, this message translates to:
  /// **'Late {count} school days in a row: {dates}'**
  String evidenceLateness(int count, String dates);

  /// What the unjustified absences rule found in the term.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 unjustified absent lesson from {from} to {to}} other{{count} unjustified absent lessons from {from} to {to}}}'**
  String evidenceUnjustified(int count, String from, String to);

  /// No description provided for @alertRaisedOn.
  ///
  /// In en, this message translates to:
  /// **'Raised on {date}'**
  String alertRaisedOn(String date);

  /// No description provided for @alertTitle.
  ///
  /// In en, this message translates to:
  /// **'Risk alert'**
  String get alertTitle;

  /// No description provided for @whatTheRuleFound.
  ///
  /// In en, this message translates to:
  /// **'What the rule found'**
  String get whatTheRuleFound;

  /// No description provided for @yourDecision.
  ///
  /// In en, this message translates to:
  /// **'Your decision'**
  String get yourDecision;

  /// No description provided for @decisionHint.
  ///
  /// In en, this message translates to:
  /// **'SchoolLink only raises the alert. You decide what happens next.'**
  String get decisionHint;

  /// No description provided for @decisionSummon.
  ///
  /// In en, this message translates to:
  /// **'Summon the parents'**
  String get decisionSummon;

  /// No description provided for @decisionSummonDetail.
  ///
  /// In en, this message translates to:
  /// **'They are told at once, by the app or by SMS'**
  String get decisionSummonDetail;

  /// No description provided for @decisionDismiss.
  ///
  /// In en, this message translates to:
  /// **'Dismiss'**
  String get decisionDismiss;

  /// No description provided for @decisionDismissDetail.
  ///
  /// In en, this message translates to:
  /// **'Close the alert, with the reason'**
  String get decisionDismissDetail;

  /// No description provided for @decisionDisciplinary.
  ///
  /// In en, this message translates to:
  /// **'Record a disciplinary issue'**
  String get decisionDisciplinary;

  /// No description provided for @decisionDisciplinaryDetail.
  ///
  /// In en, this message translates to:
  /// **'Describe the issue to record'**
  String get decisionDisciplinaryDetail;

  /// No description provided for @decisionRefer.
  ///
  /// In en, this message translates to:
  /// **'Refer to the counsellor'**
  String get decisionRefer;

  /// No description provided for @decisionReferDetail.
  ///
  /// In en, this message translates to:
  /// **'A case is opened for the counsellor'**
  String get decisionReferDetail;

  /// No description provided for @noteDismissLabel.
  ///
  /// In en, this message translates to:
  /// **'Reason for dismissing'**
  String get noteDismissLabel;

  /// No description provided for @noteDisciplinaryLabel.
  ///
  /// In en, this message translates to:
  /// **'The issue to record'**
  String get noteDisciplinaryLabel;

  /// No description provided for @noteReferLabel.
  ///
  /// In en, this message translates to:
  /// **'For the counsellor (optional)'**
  String get noteReferLabel;

  /// No description provided for @noteRequired.
  ///
  /// In en, this message translates to:
  /// **'Write a few words.'**
  String get noteRequired;

  /// No description provided for @decisionRecorded.
  ///
  /// In en, this message translates to:
  /// **'Decision recorded'**
  String get decisionRecorded;

  /// No description provided for @alertAlreadyDecided.
  ///
  /// In en, this message translates to:
  /// **'This alert is no longer open: it has been decided.'**
  String get alertAlreadyDecided;

  /// No description provided for @summonTitle.
  ///
  /// In en, this message translates to:
  /// **'Summon {name}\'s parents'**
  String summonTitle(String name);

  /// No description provided for @meetingLabel.
  ///
  /// In en, this message translates to:
  /// **'Meeting'**
  String get meetingLabel;

  /// No description provided for @meetingNextSchoolDay.
  ///
  /// In en, this message translates to:
  /// **'Next school day, when classes start'**
  String get meetingNextSchoolDay;

  /// No description provided for @meetingChoose.
  ///
  /// In en, this message translates to:
  /// **'Choose the day and time'**
  String get meetingChoose;

  /// No description provided for @meetingAt.
  ///
  /// In en, this message translates to:
  /// **'{date} at {time}'**
  String meetingAt(String date, String time);

  /// No description provided for @summonReasonLabel.
  ///
  /// In en, this message translates to:
  /// **'Reason'**
  String get summonReasonLabel;

  /// No description provided for @summonReasonOptional.
  ///
  /// In en, this message translates to:
  /// **'Reason (optional, the alert says why)'**
  String get summonReasonOptional;

  /// D31: the reason is not sent to the parents.
  ///
  /// In en, this message translates to:
  /// **'Kept in your records. Parents get the day and time, not the reason.'**
  String get summonReasonHelp;

  /// No description provided for @sendSummons.
  ///
  /// In en, this message translates to:
  /// **'Send the summons'**
  String get sendSummons;

  /// No description provided for @summonsSent.
  ///
  /// In en, this message translates to:
  /// **'Parents summoned for {date} at {time}'**
  String summonsSent(String date, String time);

  /// No description provided for @parentsSummoned.
  ///
  /// In en, this message translates to:
  /// **'Parents summoned'**
  String get parentsSummoned;

  /// BR-12: several records at once.
  ///
  /// In en, this message translates to:
  /// **'Tap absences and lateness to select them, then justify them together.'**
  String get selectToJustify;

  /// No description provided for @justifyCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{Justify 1 record} other{Justify {count} records}}'**
  String justifyCount(int count);

  /// No description provided for @clearSelection.
  ///
  /// In en, this message translates to:
  /// **'Clear the selection'**
  String get clearSelection;

  /// No description provided for @recordMenu.
  ///
  /// In en, this message translates to:
  /// **'More actions'**
  String get recordMenu;

  /// No description provided for @cancelRecordAction.
  ///
  /// In en, this message translates to:
  /// **'Cancel this record'**
  String get cancelRecordAction;

  /// No description provided for @justificationReason.
  ///
  /// In en, this message translates to:
  /// **'Reason'**
  String get justificationReason;

  /// No description provided for @reasonIllness.
  ///
  /// In en, this message translates to:
  /// **'Illness'**
  String get reasonIllness;

  /// No description provided for @reasonFamily.
  ///
  /// In en, this message translates to:
  /// **'Family reason'**
  String get reasonFamily;

  /// No description provided for @reasonSchoolActivity.
  ///
  /// In en, this message translates to:
  /// **'School activity'**
  String get reasonSchoolActivity;

  /// No description provided for @reasonOther.
  ///
  /// In en, this message translates to:
  /// **'Other reason'**
  String get reasonOther;

  /// No description provided for @justificationNote.
  ///
  /// In en, this message translates to:
  /// **'Note (optional)'**
  String get justificationNote;

  /// NFR-10, D26: an illness takes no note.
  ///
  /// In en, this message translates to:
  /// **'“Illness” is enough: no health details are kept.'**
  String get illnessNoNote;

  /// No description provided for @justify.
  ///
  /// In en, this message translates to:
  /// **'Justify'**
  String get justify;

  /// No description provided for @justifiedCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 record justified} other{{count} records justified}}'**
  String justifiedCount(int count);

  /// No description provided for @cancelRecordTitle.
  ///
  /// In en, this message translates to:
  /// **'Cancel this record?'**
  String get cancelRecordTitle;

  /// BR-13 to BR-15, D27.
  ///
  /// In en, this message translates to:
  /// **'Only for a mistake. The record stays in the school\'s files but no longer counts. Parents who were told get a correction.'**
  String get cancelRecordBody;

  /// D37: the cancel sheet of a disciplinary incident; parents never see incidents
  ///
  /// In en, this message translates to:
  /// **'Only for a mistake. The incident stays in the school\'s files but is no longer shown.'**
  String get cancelIncidentBody;

  /// No description provided for @cancelReason.
  ///
  /// In en, this message translates to:
  /// **'Reason for cancelling'**
  String get cancelReason;

  /// No description provided for @confirmCancelRecord.
  ///
  /// In en, this message translates to:
  /// **'Cancel the record'**
  String get confirmCancelRecord;

  /// No description provided for @recordCancelled.
  ///
  /// In en, this message translates to:
  /// **'Record cancelled'**
  String get recordCancelled;

  /// UC-D9: the title of the full-day absence PDF.
  ///
  /// In en, this message translates to:
  /// **'Students absent the whole day'**
  String get reportTitle;

  /// UC-D9: how many are listed.
  ///
  /// In en, this message translates to:
  /// **'{students, plural, =1{1 student} other{{students} students}} in {classes, plural, =1{1 class} other{{classes} classes}}'**
  String reportSummary(int students, int classes);

  /// UC-D9: the DM who made the report.
  ///
  /// In en, this message translates to:
  /// **'Discipline Master: {name}'**
  String reportBy(String name);

  /// UC-D9: above each class table.
  ///
  /// In en, this message translates to:
  /// **'{className}: {count, plural, =1{1 student} other{{count} students}}'**
  String reportClassHeading(String className, int count);

  /// UC-D9: table column.
  ///
  /// In en, this message translates to:
  /// **'No.'**
  String get reportColumnNumber;

  /// UC-D9: table column, last name first.
  ///
  /// In en, this message translates to:
  /// **'Name'**
  String get reportColumnName;

  /// UC-D9: table column.
  ///
  /// In en, this message translates to:
  /// **'Matricule'**
  String get reportColumnMatricule;

  /// UC-D9, D2: classes with a lesson never recorded, e.g. "4e B (P5)".
  ///
  /// In en, this message translates to:
  /// **'Lessons not recorded, so full-day absences there cannot be known: {list}'**
  String reportUnrecorded(String list);

  /// UC-D9: a lesson by its number, in the unrecorded line.
  ///
  /// In en, this message translates to:
  /// **'P{number}'**
  String reportPeriod(int number);

  /// UC-D9: the PDF footer.
  ///
  /// In en, this message translates to:
  /// **'Generated by SchoolLink on {date} at {time}'**
  String reportFooter(String date, String time);

  /// UC-D9: the PDF page number.
  ///
  /// In en, this message translates to:
  /// **'Page {page}/{pages}'**
  String reportPage(int page, int pages);

  /// UC-D9: the DM home entry and the screen title.
  ///
  /// In en, this message translates to:
  /// **'Full-day absences'**
  String get fullDayEntry;

  /// UC-D9: under the DM home entry.
  ///
  /// In en, this message translates to:
  /// **'The day\'s list, as a PDF to share'**
  String get fullDayEntryHint;

  /// UC-D9: the day field.
  ///
  /// In en, this message translates to:
  /// **'Day'**
  String get fullDayPickDay;

  /// UC-D9: under the day field.
  ///
  /// In en, this message translates to:
  /// **'From 5 PM the day proposed is today; before, the previous school day.'**
  String get fullDayDayHint;

  /// UC-D9: the button.
  ///
  /// In en, this message translates to:
  /// **'Generate the document'**
  String get fullDayGenerate;

  /// UC-D9: while the PDF is made.
  ///
  /// In en, this message translates to:
  /// **'Preparing the document…'**
  String get fullDayPreparing;

  /// UC-D9: opens Android's share sheet with the PDF.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get fullDayShare;

  /// UC-D9: nobody listed; no PDF is made.
  ///
  /// In en, this message translates to:
  /// **'No student was absent from all the school periods on {date}.'**
  String fullDayNobody(String date);

  /// No description provided for @tabHistory.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get tabHistory;

  /// UC-P5, D39: the child page tab with the teachers and DMs.
  ///
  /// In en, this message translates to:
  /// **'Contacts'**
  String get tabContacts;

  /// No description provided for @contactsTeachers.
  ///
  /// In en, this message translates to:
  /// **'Teachers'**
  String get contactsTeachers;

  /// No description provided for @contactsDiscipline.
  ///
  /// In en, this message translates to:
  /// **'Discipline'**
  String get contactsDiscipline;

  /// No description provided for @contactsNoPhone.
  ///
  /// In en, this message translates to:
  /// **'No number'**
  String get contactsNoPhone;

  /// No description provided for @contactsNoDiscipline.
  ///
  /// In en, this message translates to:
  /// **'No discipline staff for this class yet.'**
  String get contactsNoDiscipline;

  /// No description provided for @contactsNoTeachers.
  ///
  /// In en, this message translates to:
  /// **'No teachers for this class yet.'**
  String get contactsNoTeachers;

  /// UC-P5: the tooltip of the button.
  ///
  /// In en, this message translates to:
  /// **'Call {name}'**
  String contactsCall(String name);

  /// UC-P5: the tooltip of the button.
  ///
  /// In en, this message translates to:
  /// **'Text {name}'**
  String contactsSms(String name);

  /// No description provided for @contactsCannotOpen.
  ///
  /// In en, this message translates to:
  /// **'Could not open the Phone app.'**
  String get contactsCannotOpen;

  /// UC-D5, D37: the student page tab with the disciplinary records.
  ///
  /// In en, this message translates to:
  /// **'Discipline'**
  String get disciplineTab;

  /// No description provided for @recordIncident.
  ///
  /// In en, this message translates to:
  /// **'Record an incident'**
  String get recordIncident;

  /// No description provided for @incidentRecorded.
  ///
  /// In en, this message translates to:
  /// **'Incident recorded.'**
  String get incidentRecorded;

  /// No description provided for @noIncidents.
  ///
  /// In en, this message translates to:
  /// **'No incidents recorded.'**
  String get noIncidents;

  /// No description provided for @noIncidentsBody.
  ///
  /// In en, this message translates to:
  /// **'Record an incident to keep track of it in the student\'s file.'**
  String get noIncidentsBody;

  /// No description provided for @incidentOpen.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get incidentOpen;

  /// No description provided for @incidentClosed.
  ///
  /// In en, this message translates to:
  /// **'Closed'**
  String get incidentClosed;

  /// No description provided for @closeIncident.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get closeIncident;

  /// No description provided for @cancelIncident.
  ///
  /// In en, this message translates to:
  /// **'Cancel the incident'**
  String get cancelIncident;

  /// No description provided for @incidentClosedDone.
  ///
  /// In en, this message translates to:
  /// **'Incident closed.'**
  String get incidentClosedDone;

  /// No description provided for @incidentCancelled.
  ///
  /// In en, this message translates to:
  /// **'Incident cancelled.'**
  String get incidentCancelled;

  /// No description provided for @incidentCategory.
  ///
  /// In en, this message translates to:
  /// **'Kind of incident'**
  String get incidentCategory;

  /// No description provided for @incidentMeasure.
  ///
  /// In en, this message translates to:
  /// **'Measure taken'**
  String get incidentMeasure;

  /// No description provided for @incidentDate.
  ///
  /// In en, this message translates to:
  /// **'Date of the incident'**
  String get incidentDate;

  /// No description provided for @incidentDescription.
  ///
  /// In en, this message translates to:
  /// **'What happened'**
  String get incidentDescription;

  /// No description provided for @closeNote.
  ///
  /// In en, this message translates to:
  /// **'Closing note (optional)'**
  String get closeNote;

  /// No description provided for @incidentRecordedBy.
  ///
  /// In en, this message translates to:
  /// **'Recorded by {name}'**
  String incidentRecordedBy(String name);

  /// No description provided for @incidentCloseNote.
  ///
  /// In en, this message translates to:
  /// **'Closed: {note}'**
  String incidentCloseNote(String note);

  /// No description provided for @categoryBehaviour.
  ///
  /// In en, this message translates to:
  /// **'Behaviour'**
  String get categoryBehaviour;

  /// No description provided for @categoryInsolence.
  ///
  /// In en, this message translates to:
  /// **'Insolence'**
  String get categoryInsolence;

  /// No description provided for @categoryFight.
  ///
  /// In en, this message translates to:
  /// **'Fight'**
  String get categoryFight;

  /// No description provided for @categoryAbsenteeism.
  ///
  /// In en, this message translates to:
  /// **'Absenteeism'**
  String get categoryAbsenteeism;

  /// No description provided for @categoryCheating.
  ///
  /// In en, this message translates to:
  /// **'Cheating'**
  String get categoryCheating;

  /// No description provided for @categoryDress.
  ///
  /// In en, this message translates to:
  /// **'Dress'**
  String get categoryDress;

  /// No description provided for @categoryPhone.
  ///
  /// In en, this message translates to:
  /// **'Phone'**
  String get categoryPhone;

  /// No description provided for @categoryDamage.
  ///
  /// In en, this message translates to:
  /// **'Damage'**
  String get categoryDamage;

  /// No description provided for @categoryOther.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get categoryOther;

  /// No description provided for @measureNone.
  ///
  /// In en, this message translates to:
  /// **'None'**
  String get measureNone;

  /// No description provided for @measureWarning.
  ///
  /// In en, this message translates to:
  /// **'Warning'**
  String get measureWarning;

  /// No description provided for @measureDetention.
  ///
  /// In en, this message translates to:
  /// **'Detention'**
  String get measureDetention;

  /// No description provided for @measureSuspension.
  ///
  /// In en, this message translates to:
  /// **'Suspension'**
  String get measureSuspension;

  /// No description provided for @measureOther.
  ///
  /// In en, this message translates to:
  /// **'Other measure'**
  String get measureOther;

  /// No description provided for @incidentDateOn.
  ///
  /// In en, this message translates to:
  /// **'Date of the incident: {date}'**
  String incidentDateOn(String date);

  /// DM home: entry to the sent messages (UC-D4).
  ///
  /// In en, this message translates to:
  /// **'Messages to parents'**
  String get messagesEntry;

  /// DM home: under the messages entry.
  ///
  /// In en, this message translates to:
  /// **'Write to the parents of a student or a class'**
  String get messagesEntryHint;

  /// Messages screen: button to write one.
  ///
  /// In en, this message translates to:
  /// **'New message'**
  String get newMessage;

  /// Messages screen: empty.
  ///
  /// In en, this message translates to:
  /// **'No message sent.'**
  String get noMessages;

  /// Messages screen: empty, what happens.
  ///
  /// In en, this message translates to:
  /// **'Parents get it in the app, or by SMS when they do not have it and you tick SMS.'**
  String get noMessagesBody;

  /// A message to every parent of the school.
  ///
  /// In en, this message translates to:
  /// **'Whole school'**
  String get messageToSchool;

  /// Sent list: the message also went by SMS.
  ///
  /// In en, this message translates to:
  /// **'With SMS'**
  String get messageWithSms;

  /// Sent list: guardians the message went to.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 recipient} other{{count} recipients}}'**
  String deliveryRecipients(int count);

  /// Sent list: pushes opened.
  ///
  /// In en, this message translates to:
  /// **'{count} opened'**
  String deliveryOpened(int count);

  /// Sent list: delivered, not opened yet.
  ///
  /// In en, this message translates to:
  /// **'{count} received'**
  String deliveryReceived(int count);

  /// Sent list: not delivered yet.
  ///
  /// In en, this message translates to:
  /// **'{count} in progress'**
  String deliveryPending(int count);

  /// Sent list: could not be delivered.
  ///
  /// In en, this message translates to:
  /// **'{count} failed'**
  String deliveryFailed(int count);

  /// Composer title and student page action.
  ///
  /// In en, this message translates to:
  /// **'Write to parents'**
  String get writeToParents;

  /// Composer: the class written to.
  ///
  /// In en, this message translates to:
  /// **'Class'**
  String get messageClass;

  /// Composer: the text field.
  ///
  /// In en, this message translates to:
  /// **'Your message'**
  String get messageBody;

  /// Composer: SMS switch.
  ///
  /// In en, this message translates to:
  /// **'Also by SMS, to every parent'**
  String get messageSms;

  /// Composer: under the SMS switch.
  ///
  /// In en, this message translates to:
  /// **'Paid, counted in the month\'s SMS.'**
  String get messageSmsHint;

  /// Preview: who gets the message, without SMS.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 parent} other{{count} parents}}: {byApp} by the app, {without} without the app'**
  String messageCounts(int count, int byApp, int without);

  /// Preview: who gets the message, with SMS.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 parent} other{{count} parents}}: {byApp} by the app, {bySms} by SMS'**
  String messageCountsSms(int count, int byApp, int bySms);

  /// Preview: the SMS cost and what the month has left (BR-25).
  ///
  /// In en, this message translates to:
  /// **'≈ {cost} FCFA · {left} SMS left this month'**
  String messageCost(String cost, String left);

  /// Preview: SMS units of the text.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 SMS per parent} other{{count} SMS per parent}}'**
  String messageUnits(int count);

  /// Preview: sending is refused over the monthly cap (BR-25).
  ///
  /// In en, this message translates to:
  /// **'Not enough SMS left this month: {left} left. Untick SMS or write to fewer parents.'**
  String messageOverCap(int left);

  /// Preview: the SMS is not GSM-7.
  ///
  /// In en, this message translates to:
  /// **'The text has special letters (ê, ç, ô…): each SMS holds fewer characters and costs more.'**
  String get messageUnicode;

  /// Preview: plain SMS switch.
  ///
  /// In en, this message translates to:
  /// **'Replace special letters'**
  String get messagePlain;

  /// Preview: under the plain SMS switch.
  ///
  /// In en, this message translates to:
  /// **'In the SMS only; the app keeps the exact text.'**
  String get messagePlainHint;

  /// Preview: the SMS as parents will read it.
  ///
  /// In en, this message translates to:
  /// **'The SMS sent'**
  String get messageSmsSample;

  /// Preview: no class yet.
  ///
  /// In en, this message translates to:
  /// **'Choose a class.'**
  String get messageChooseClass;

  /// Preview: no text yet.
  ///
  /// In en, this message translates to:
  /// **'Write the message to see who gets it.'**
  String get messagePreviewHint;

  /// Composer: send button.
  ///
  /// In en, this message translates to:
  /// **'Send'**
  String get messageSend;

  /// Confirm dialog title.
  ///
  /// In en, this message translates to:
  /// **'Send this message?'**
  String get messageConfirmTitle;

  /// Confirm dialog: messages are never edited.
  ///
  /// In en, this message translates to:
  /// **'It cannot be changed or taken back.'**
  String get messageConfirmBody;

  /// Snackbar after sending.
  ///
  /// In en, this message translates to:
  /// **'Message sent.'**
  String get messageSent;

  /// Parent message screen: the child a DM message is about (UC-P4).
  ///
  /// In en, this message translates to:
  /// **'About {name}'**
  String messageAbout(String name);

  /// No description provided for @dayTitle.
  ///
  /// In en, this message translates to:
  /// **'Today\'s attendance'**
  String get dayTitle;

  /// No description provided for @dayEntryHint.
  ///
  /// In en, this message translates to:
  /// **'Each period\'s roll call, absentees and lateness'**
  String get dayEntryHint;

  /// No description provided for @dayPeriodShort.
  ///
  /// In en, this message translates to:
  /// **'P{number}'**
  String dayPeriodShort(int number);

  /// No description provided for @dayRecorded.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0{Recorded · nobody absent} other{Recorded · {count} absent}}'**
  String dayRecorded(int count);

  /// No description provided for @dayNotRecorded.
  ///
  /// In en, this message translates to:
  /// **'Not recorded'**
  String get dayNotRecorded;

  /// No description provided for @dayUpcoming.
  ///
  /// In en, this message translates to:
  /// **'Upcoming'**
  String get dayUpcoming;

  /// No description provided for @dayNotRecordedHint.
  ///
  /// In en, this message translates to:
  /// **'No roll call for this period. Only the teacher can record it.'**
  String get dayNotRecordedHint;

  /// No description provided for @dayAbsentees.
  ///
  /// In en, this message translates to:
  /// **'Absent in P{number}'**
  String dayAbsentees(int number);

  /// No description provided for @dayLateness.
  ///
  /// In en, this message translates to:
  /// **'Lateness'**
  String get dayLateness;

  /// No description provided for @dayCancelled.
  ///
  /// In en, this message translates to:
  /// **'Cancelled'**
  String get dayCancelled;

  /// No description provided for @dayLatenessCancelled.
  ///
  /// In en, this message translates to:
  /// **'Cancelled'**
  String get dayLatenessCancelled;

  /// No description provided for @dayTotalNotRecorded.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{period not recorded} other{periods not recorded}}'**
  String dayTotalNotRecorded(int count);

  /// No description provided for @dayTotalAbsent.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{student absent} other{students absent}}'**
  String dayTotalAbsent(int count);

  /// No description provided for @dayTotalLateness.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{late arrival} other{late arrivals}}'**
  String dayTotalLateness(int count);

  /// No description provided for @dayNoLessons.
  ///
  /// In en, this message translates to:
  /// **'No lessons that day.'**
  String get dayNoLessons;

  /// No description provided for @dayNoClasses.
  ///
  /// In en, this message translates to:
  /// **'No class in your blocks.'**
  String get dayNoClasses;

  /// No description provided for @dayChangeDate.
  ///
  /// In en, this message translates to:
  /// **'Change day'**
  String get dayChangeDate;

  /// No description provided for @daySeeHistory.
  ///
  /// In en, this message translates to:
  /// **'See history'**
  String get daySeeHistory;

  /// More tab: alerts already decided (UC-D2, D41).
  ///
  /// In en, this message translates to:
  /// **'Decided alerts'**
  String get closedAlertsTitle;

  /// More tab: subtitle of decided alerts.
  ///
  /// In en, this message translates to:
  /// **'Decisions already taken'**
  String get closedAlertsHint;

  /// More tab: the DM's scope dashboard (UC-D8, D41).
  ///
  /// In en, this message translates to:
  /// **'Dashboard'**
  String get dashboardTitle;

  /// More tab: subtitle of the dashboard.
  ///
  /// In en, this message translates to:
  /// **'Absences and lateness of your classes over a period'**
  String get dashboardHint;

  /// More tab: thresholds and digest settings (UC-D7, D41).
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settingsTitle;

  /// More tab: subtitle of the settings.
  ///
  /// In en, this message translates to:
  /// **'Alert thresholds, start time, SMS digests'**
  String get settingsHint;

  /// Decided alert: the day of the decision.
  ///
  /// In en, this message translates to:
  /// **'Decided on {date}'**
  String decidedOn(String date);

  /// Decided alert: the parents were summoned.
  ///
  /// In en, this message translates to:
  /// **'Parents summoned'**
  String get decidedSummon;

  /// Decided alert: closed with no action.
  ///
  /// In en, this message translates to:
  /// **'Dismissed'**
  String get decidedDismiss;

  /// Decided alert: a disciplinary record was opened.
  ///
  /// In en, this message translates to:
  /// **'Disciplinary record opened'**
  String get decidedDisciplinary;

  /// Decided alert: referred to the counsellor.
  ///
  /// In en, this message translates to:
  /// **'Referred to the counsellor'**
  String get decidedRefer;

  /// Decided alerts: empty list.
  ///
  /// In en, this message translates to:
  /// **'No alert decided yet.'**
  String get noClosedAlerts;

  /// Dashboard: the range shown; tap to change it.
  ///
  /// In en, this message translates to:
  /// **'{from} to {to}'**
  String dashboardRange(String from, String to);

  /// Dashboard figure: absent periods over expected student periods.
  ///
  /// In en, this message translates to:
  /// **'Absence rate'**
  String get kpiAbsenceRate;

  /// Dashboard figure: absent periods not justified.
  ///
  /// In en, this message translates to:
  /// **'Unjustified absences'**
  String get kpiUnjustified;

  /// Dashboard figure: lateness recorded.
  ///
  /// In en, this message translates to:
  /// **'Lateness'**
  String get kpiLateness;

  /// Dashboard figure: alerts open now.
  ///
  /// In en, this message translates to:
  /// **'Alerts to decide'**
  String get kpiOpenAlerts;

  /// Dashboard section: each class, most absences first.
  ///
  /// In en, this message translates to:
  /// **'By class'**
  String get dashboardByClass;

  /// Dashboard: one class's absent periods and lateness.
  ///
  /// In en, this message translates to:
  /// **'{absent, plural, =1{1 missed lesson} other{{absent} missed lessons}} · {late} late'**
  String dashboardClassLine(int absent, int late);

  /// Dashboard section: alerts per rule.
  ///
  /// In en, this message translates to:
  /// **'Alerts by rule'**
  String get dashboardAlertsByRule;

  /// Dashboard: alerts of one rule in the range, and open now.
  ///
  /// In en, this message translates to:
  /// **'{raised} raised · {closed} decided · {open} open'**
  String dashboardRuleLine(int raised, int closed, int open);

  /// Dashboard: empty range.
  ///
  /// In en, this message translates to:
  /// **'No roll call and no lateness recorded in this period.'**
  String get dashboardNoActivity;

  /// Dashboard footnote (BR-15).
  ///
  /// In en, this message translates to:
  /// **'Cancelled records are not counted.'**
  String get dashboardCancelledNote;

  /// Settings screen intro (UC-D7).
  ///
  /// In en, this message translates to:
  /// **'When SchoolLink raises an alert, when school starts, and when parents get the SMS digest. These settings apply to the whole school.'**
  String get settingsIntro;

  /// Settings: rules section.
  ///
  /// In en, this message translates to:
  /// **'Alert rules'**
  String get settingsRulesTitle;

  /// Settings: rules intro.
  ///
  /// In en, this message translates to:
  /// **'A rule switched off raises no alert. Alerts already raised stay.'**
  String get settingsRulesIntro;

  /// Settings: rule name.
  ///
  /// In en, this message translates to:
  /// **'Absent all day'**
  String get settingsRuleFullDay;

  /// Settings: rule name.
  ///
  /// In en, this message translates to:
  /// **'Late several days running'**
  String get settingsRuleLateness;

  /// Settings: rule name.
  ///
  /// In en, this message translates to:
  /// **'Unjustified absences'**
  String get settingsRuleUnjustified;

  /// Settings: what the full-day rule does.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{An alert when a student misses every lesson of a day.} other{An alert when a student misses every lesson {count} days running.}}'**
  String settingsHelpFullDay(int count);

  /// Settings: what the lateness rule does.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{An alert when a student arrives late.} other{An alert when a student arrives late {count} school days running.}}'**
  String settingsHelpLateness(int count);

  /// Settings: what the unjustified rule does.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{An alert at the first unjustified missed lesson of the term.} other{An alert at {count} unjustified missed lessons in the term.}}'**
  String settingsHelpUnjustified(int count);

  /// Settings: count label.
  ///
  /// In en, this message translates to:
  /// **'Days in a row'**
  String get settingsCountFullDay;

  /// Settings: count label.
  ///
  /// In en, this message translates to:
  /// **'School days in a row'**
  String get settingsCountLateness;

  /// Settings: count label.
  ///
  /// In en, this message translates to:
  /// **'Lessons in the term'**
  String get settingsCountUnjustified;

  /// Settings: a count's bounds.
  ///
  /// In en, this message translates to:
  /// **'between {min} and {max}'**
  String settingsCountRange(int min, int max);

  /// Settings: lower the count.
  ///
  /// In en, this message translates to:
  /// **'Less'**
  String get settingsLess;

  /// Settings: raise the count.
  ///
  /// In en, this message translates to:
  /// **'More'**
  String get settingsMore;

  /// Settings: times section.
  ///
  /// In en, this message translates to:
  /// **'School times'**
  String get settingsTimesTitle;

  /// Settings: times intro.
  ///
  /// In en, this message translates to:
  /// **'Arriving after the start time counts as late at the gate.'**
  String get settingsTimesIntro;

  /// Settings: start time.
  ///
  /// In en, this message translates to:
  /// **'Start of school'**
  String get settingsStartTime;

  /// Settings: start time help.
  ///
  /// In en, this message translates to:
  /// **'Also the default time of a summons.'**
  String get settingsStartTimeHelp;

  /// Settings: a time out of bounds.
  ///
  /// In en, this message translates to:
  /// **'Between {min} and {max}'**
  String settingsTimeRange(String min, String max);

  /// Settings: digest time label.
  ///
  /// In en, this message translates to:
  /// **'SMS digest {n}'**
  String settingsDigest(int n);

  /// Settings: second digest must be later.
  ///
  /// In en, this message translates to:
  /// **'After the first digest'**
  String get settingsDigestOrder;

  /// Settings: add a digest time.
  ///
  /// In en, this message translates to:
  /// **'Add a second digest'**
  String get settingsAddDigest;

  /// Settings: remove the second digest.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get settingsRemoveDigest;

  /// Settings: digest help.
  ///
  /// In en, this message translates to:
  /// **'Parents without the app get one SMS with the day\'s absences and lateness, at these times.'**
  String get settingsDigestHelp;

  /// Settings: saved.
  ///
  /// In en, this message translates to:
  /// **'Settings saved.'**
  String get settingsSaved;

  /// Student page: the one button for incident, message, summons (D41).
  ///
  /// In en, this message translates to:
  /// **'Act'**
  String get studentActions;

  /// Student page Messages tab: empty.
  ///
  /// In en, this message translates to:
  /// **'No message sent to the parents.'**
  String get studentNoMessages;

  /// Student page Messages tab: what to do.
  ///
  /// In en, this message translates to:
  /// **'To write to them, tap “Act”.'**
  String get studentNoMessagesBody;

  /// D41: Today tab on a day without lessons (weekend, holiday), in place of the day counts
  ///
  /// In en, this message translates to:
  /// **'No lessons today: nothing to record.'**
  String get todayNoLessons;

  /// D24: confirmation before a parent signs out
  ///
  /// In en, this message translates to:
  /// **'Sign out?'**
  String get signOutParentTitle;

  /// D24: a parent has no password; signing back in sends an SMS code
  ///
  /// In en, this message translates to:
  /// **'To come back, sign in with your phone number and password.'**
  String get signOutParentBody;
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
