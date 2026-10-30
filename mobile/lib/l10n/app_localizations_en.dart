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

  @override
  String get sessionToRecord => 'To record';

  @override
  String sessionRecorded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Recorded, $count absent',
      one: 'Recorded, 1 absent',
      zero: 'Recorded, no absences',
    );
    return '$_temp0';
  }

  @override
  String get sessionNow => 'Now';

  @override
  String sessionTimes(String start, String end) {
    return '$start to $end';
  }

  @override
  String get notASchoolDay => 'No classes today: it is not a school day.';

  @override
  String get noLessonsToday => 'You have no classes in the timetable today.';

  @override
  String offlineSince(String time) {
    return 'No connection. Shown as last updated at $time.';
  }

  @override
  String get attendanceHint => 'Tap the students who are absent.';

  @override
  String presentCount(int count) {
    return 'Present: $count';
  }

  @override
  String absentCount(int count) {
    return 'Absent: $count';
  }

  @override
  String get absentLabel => 'Absent';

  @override
  String get save => 'Save';

  @override
  String confirmAbsentees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'These $count students are marked absent. Correct?',
      one: 'This student is marked absent. Correct?',
      zero: 'No student is marked absent. Correct?',
    );
    return '$_temp0';
  }

  @override
  String get back => 'Back';

  @override
  String get yes => 'Yes';

  @override
  String get confirmNotifyTitle => 'Confirm and notify parents?';

  @override
  String get confirmNotifyBody => 'Once confirmed, the session is locked and can no longer be changed.';

  @override
  String get confirm => 'Confirm';

  @override
  String sessionLockedAt(String time) {
    return 'Locked: recorded at $time.';
  }

  @override
  String get absentStudents => 'Absent students';

  @override
  String get noAbsentStudents => 'No student was absent.';

  @override
  String absentTotal(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count students were absent.',
      one: '1 student was absent.',
      zero: 'No student was absent.',
    );
    return '$_temp0';
  }

  @override
  String get syncWaiting => 'Saved on this phone, waiting to be sent.';

  @override
  String get syncSent => 'Sent to the school.';

  @override
  String get syncNotAccepted => 'Not accepted by the school.';

  @override
  String get leaveTitle => 'Leave without saving?';

  @override
  String get leaveBody => 'The absences you marked will be lost.';

  @override
  String get leave => 'Leave';

  @override
  String get rosterUnavailable => 'The class list is not on this phone yet. Connect to the network once to load it.';

  @override
  String get sessionNotFound => 'This session is not in today\'s list.';

  @override
  String get signedInAs => 'Signed in as';

  @override
  String get roleAdmin => 'Administrator';

  @override
  String get roleTeacher => 'Teacher';

  @override
  String get roleAssistantDm => 'Assistant discipline master';

  @override
  String get roleDm => 'Discipline master';

  @override
  String get roleCounsellor => 'Counsellor';

  @override
  String get roleParent => 'Parent';

  @override
  String get roleResearcher => 'Researcher';

  @override
  String pendingOnline(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count records waiting to be sent.',
      one: '1 record waiting to be sent.',
    );
    return '$_temp0';
  }

  @override
  String pendingOffline(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'No network: $count records will be sent when it is back.',
      one: 'No network: 1 record will be sent when it is back.',
    );
    return '$_temp0';
  }

  @override
  String get sendNow => 'Send now';

  @override
  String get todaySchedule => 'Today\'s timetable';

  @override
  String get noRollToday => 'No roll call today';

  @override
  String rollsLeft(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count roll calls to do',
      one: '1 roll call to do',
      zero: 'All rolls are done',
    );
    return '$_temp0';
  }

  @override
  String rollsProgress(int done, int total) {
    return '$done of $total recorded';
  }

  @override
  String get takeRoll => 'Take the roll';

  @override
  String get findStudent => 'Find a student';

  @override
  String latenessToday(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count late arrivals today',
      one: '1 late arrival today',
      zero: 'No lateness today',
    );
    return '$_temp0';
  }

  @override
  String classesStartAt(String time) {
    return 'Classes start at $time';
  }

  @override
  String get searchStudentHint => 'Name or matricule';

  @override
  String get clearSearch => 'Clear';

  @override
  String get gateStudentsUnavailable =>
      'The student list is not on this phone yet. Connect to the network once to load it.';

  @override
  String get noStudentFound => 'No student matches this search.';

  @override
  String searchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count students', one: '1 student');
    return '$_temp0';
  }

  @override
  String get typeMore => 'Type more letters to narrow the list.';

  @override
  String get latenessListTitle => 'Today\'s lateness';

  @override
  String get noLatenessToday => 'No lateness recorded today.';

  @override
  String get noLatenessTodayHint => 'Search a student above to record their arrival.';

  @override
  String classAndMatricule(String className, String matricule) {
    return '$className, no. $matricule';
  }

  @override
  String alreadyLateAt(String time) {
    return 'Late at $time';
  }

  @override
  String get arrivalTimeLabel => 'Arrival time';

  @override
  String get changeTime => 'Change';

  @override
  String arrivalNotLate(String start) {
    return 'Classes start at $start: arriving at that time or before is not late.';
  }

  @override
  String get arrivalInFuture => 'The arrival time cannot be later than now.';

  @override
  String get latenessIndependent => 'Recorded apart from the class roll: it does not change it.';

  @override
  String get recordLateness => 'Record lateness';

  @override
  String latenessSaved(String name, String time) {
    return 'Lateness recorded: $name at $time.';
  }

  @override
  String get latenessWaiting => 'On this phone, waiting to be sent';

  @override
  String latenessRefused(String name, String time) {
    return 'Not accepted by the school: $name at $time.';
  }

  @override
  String get justifiedLabel => 'Justified';

  @override
  String get inYourClasses => 'In the classes you are in charge of';

  @override
  String get alertsTitle => 'Alerts to decide';

  @override
  String get alertsSoonTitle => 'Risk alerts arrive in a coming update';

  @override
  String get alertsSoonBody =>
      'Repeated absences and lateness in your classes will show here for your decision. The app never decides for you.';
}
