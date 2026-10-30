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

  /// No description provided for @alertsSoonTitle.
  ///
  /// In en, this message translates to:
  /// **'Risk alerts arrive in a coming update'**
  String get alertsSoonTitle;

  /// BR-16 to BR-19.
  ///
  /// In en, this message translates to:
  /// **'Repeated absences and lateness in your classes will show here for your decision. The app never decides for you.'**
  String get alertsSoonBody;

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

  /// UC-P1: entry point on the sign-in screen.
  ///
  /// In en, this message translates to:
  /// **'Activate with my invitation code'**
  String get activateWithCode;

  /// No description provided for @activationTitle.
  ///
  /// In en, this message translates to:
  /// **'Activate my account'**
  String get activationTitle;

  /// No description provided for @activationStep.
  ///
  /// In en, this message translates to:
  /// **'Step {step} of 2'**
  String activationStep(int step);

  /// No description provided for @activationHeadline.
  ///
  /// In en, this message translates to:
  /// **'Your invitation code'**
  String get activationHeadline;

  /// No description provided for @activationIntro.
  ///
  /// In en, this message translates to:
  /// **'The school sent it to you by SMS. Enter it with the phone number you gave the school.'**
  String get activationIntro;

  /// No description provided for @invitationCodeLabel.
  ///
  /// In en, this message translates to:
  /// **'Invitation code'**
  String get invitationCodeLabel;

  /// No description provided for @invitationCodeHelp.
  ///
  /// In en, this message translates to:
  /// **'As written in the school\'s SMS'**
  String get invitationCodeHelp;

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
  /// **'Enter the code received'**
  String get otpTitle;

  /// No description provided for @otpSentTo.
  ///
  /// In en, this message translates to:
  /// **'We sent a 6-digit code by SMS to {phone}.'**
  String otpSentTo(String phone);

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

  /// No description provided for @activateAccount.
  ///
  /// In en, this message translates to:
  /// **'Activate my account'**
  String get activateAccount;

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

  /// D24: a parent who already activated signs in again.
  ///
  /// In en, this message translates to:
  /// **'Sign in with a code by SMS'**
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
  /// **'Choose your school and enter the number you gave it. We will send you a code by SMS. First time? Use your invitation code instead.'**
  String get parentSignInIntro;

  /// D24, NFR-7: the server does not say whether the number has an account.
  ///
  /// In en, this message translates to:
  /// **'If {phone} has a SchoolLink parent account, a 6-digit code was sent to it by SMS.'**
  String otpSentIfAccount(String phone);

  /// Read by screen readers while a skeleton stands in for content.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loading;
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
