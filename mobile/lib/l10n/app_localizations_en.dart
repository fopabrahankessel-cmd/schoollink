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
  String get navToday => 'Today';

  @override
  String get navStudents => 'Students';

  @override
  String get navMessages => 'Messages';

  @override
  String get navMore => 'More';

  @override
  String todayAlerts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count alerts to decide',
      one: '1 alert to decide',
      zero: 'No alert to decide',
    );
    return '$_temp0';
  }

  @override
  String todayNotRecorded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count periods not recorded',
      one: '1 period not recorded',
      zero: 'Every period recorded',
    );
    return '$_temp0';
  }

  @override
  String todayAbsences(int absent, int late) {
    return '$absent absent · $late late';
  }

  @override
  String get openAlertsTitle => 'Alerts to decide';

  @override
  String get reportSectionTitle => 'Day report';

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
  String get notificationChannelName => 'Absences and lateness';

  @override
  String get notificationChannelDescription => 'Absences, lateness and summons of your children, and school alerts.';

  @override
  String get parentQuestion => 'Parent?';

  @override
  String parentSignInStep(int step) {
    return 'Step $step of 2';
  }

  @override
  String get phoneLabel => 'Phone number';

  @override
  String get phoneHelp => 'The number you gave the school';

  @override
  String get phoneInvalid => 'Enter a Cameroon number with 9 digits, for example 6 99 00 00 01.';

  @override
  String get sendSmsCode => 'Receive a code by SMS';

  @override
  String get otpTitle => 'Enter the code received';

  @override
  String get otpLabel => 'Verification code';

  @override
  String get otpInvalid => 'Enter the 6 digits of the code.';

  @override
  String otpValidUntil(String time) {
    return 'The code works until $time.';
  }

  @override
  String otpResent(String time) {
    return 'A new code was sent. It works until $time.';
  }

  @override
  String get resendCode => 'Send a new code';

  @override
  String get changeNumber => 'Change the number';

  @override
  String get parentSignInWithSms => 'Sign in with a code by SMS';

  @override
  String get parentSignInTitle => 'Parent sign-in';

  @override
  String get parentSignInHeadline => 'Your phone number';

  @override
  String get parentSignInIntro =>
      'Choose your school and enter the number you gave it. We will send you a code by SMS.';

  @override
  String otpSentIfKnown(String phone) {
    return 'If the school knows $phone, a 6-digit code was sent to it by SMS.';
  }

  @override
  String get loading => 'Loading…';

  @override
  String get today => 'Today';

  @override
  String get yesterday => 'Yesterday';

  @override
  String unreadMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unread messages',
      one: '1 unread message',
      zero: 'All read',
    );
    return '$_temp0';
  }

  @override
  String get unread => 'Unread';

  @override
  String get inboxEmpty => 'No messages yet';

  @override
  String get inboxEmptyHint => 'Your children\'s absences and lateness will appear here, the same day.';

  @override
  String get olderMessages => 'Show older messages';

  @override
  String get olderMessagesFailed => 'Older messages could not be loaded. Check your connection and try again.';

  @override
  String get messageNotFound => 'This message is no longer in your notifications.';

  @override
  String get backToInbox => 'Back to notifications';

  @override
  String receivedAt(String date, String time) {
    return 'Received $date at $time';
  }

  @override
  String get seeHistory => 'See absences and lateness';

  @override
  String get eventAbsence => 'Absence';

  @override
  String get eventLateness => 'Lateness';

  @override
  String get eventCorrection => 'Correction';

  @override
  String get eventSummons => 'Summons';

  @override
  String get eventMessage => 'Message';

  @override
  String get eventAnnouncement => 'Announcement';

  @override
  String get eventOther => 'Notification';

  @override
  String yourChildren(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Your $count children',
      one: 'Your child',
      zero: 'No child',
    );
    return '$_temp0';
  }

  @override
  String get childrenHint => 'Tap a child to see their absences and lateness.';

  @override
  String get noChildren => 'No child is linked to your account';

  @override
  String get noChildrenHint => 'Contact the school to check the phone number it has for you.';

  @override
  String classLabel(String name) {
    return 'Class: $name';
  }

  @override
  String get historyTitle => 'History';

  @override
  String periodFromTo(String from, String to) {
    return 'From $from to $to';
  }

  @override
  String absentPeriodsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'lessons missed', one: 'lesson missed');
    return '$_temp0';
  }

  @override
  String unjustifiedAbsences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count not justified',
      one: '1 not justified',
      zero: 'none unjustified',
    );
    return '$_temp0';
  }

  @override
  String latenessLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'times late', one: 'time late');
    return '$_temp0';
  }

  @override
  String unjustifiedLateness(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count not justified',
      one: '1 not justified',
      zero: 'none unjustified',
    );
    return '$_temp0';
  }

  @override
  String get dayByDay => 'Day by day';

  @override
  String historyEmpty(String name) {
    return 'No absence or lateness for $name';
  }

  @override
  String absentFromLesson(String time) {
    return 'Lesson at $time';
  }

  @override
  String lateArrivedAt(String time) {
    return 'Arrived at $time';
  }

  @override
  String get absenceJustified => 'Justified';

  @override
  String get absenceUnjustified => 'Not justified';

  @override
  String get latenessJustified => 'Justified';

  @override
  String get latenessUnjustified => 'Not justified';

  @override
  String alertsToDecide(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count alerts to decide',
      one: '1 alert to decide',
      zero: 'No alert to decide',
    );
    return '$_temp0';
  }

  @override
  String get noAlertsTitle => 'No alert to decide';

  @override
  String get noAlertsBody =>
      'Repeated absences and lateness in your classes show here for your decision. The app never decides for you.';

  @override
  String get ruleFullDayAbsence => 'Absent all day';

  @override
  String get ruleConsecutiveLateness => 'Late day after day';

  @override
  String get ruleUnjustifiedAbsences => 'Unjustified absences';

  @override
  String get ruleOther => 'Risk alert';

  @override
  String evidenceFullDay(int count, String dates) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Absent from every lesson $count days in a row: $dates',
      one: 'Absent from every lesson on $dates',
    );
    return '$_temp0';
  }

  @override
  String evidenceLateness(int count, String dates) {
    return 'Late $count school days in a row: $dates';
  }

  @override
  String evidenceUnjustified(int count, String from, String to) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unjustified absent lessons from $from to $to',
      one: '1 unjustified absent lesson from $from to $to',
    );
    return '$_temp0';
  }

  @override
  String alertRaisedOn(String date) {
    return 'Raised on $date';
  }

  @override
  String get alertTitle => 'Risk alert';

  @override
  String get whatTheRuleFound => 'What the rule found';

  @override
  String get yourDecision => 'Your decision';

  @override
  String get decisionHint => 'SchoolLink only raises the alert. You decide what happens next.';

  @override
  String get decisionSummon => 'Summon the parents';

  @override
  String get decisionSummonDetail => 'They are told at once, by the app or by SMS';

  @override
  String get decisionDismiss => 'Dismiss';

  @override
  String get decisionDismissDetail => 'Close the alert, with the reason';

  @override
  String get decisionDisciplinary => 'Record a disciplinary issue';

  @override
  String get decisionDisciplinaryDetail => 'Describe the issue to record';

  @override
  String get decisionRefer => 'Refer to the counsellor';

  @override
  String get decisionReferDetail => 'A case is opened for the counsellor';

  @override
  String get noteDismissLabel => 'Reason for dismissing';

  @override
  String get noteDisciplinaryLabel => 'The issue to record';

  @override
  String get noteReferLabel => 'For the counsellor (optional)';

  @override
  String get noteRequired => 'Write a few words.';

  @override
  String get decisionRecorded => 'Decision recorded';

  @override
  String get alertAlreadyDecided => 'This alert is no longer open: it has been decided.';

  @override
  String summonTitle(String name) {
    return 'Summon $name\'s parents';
  }

  @override
  String get meetingLabel => 'Meeting';

  @override
  String get meetingNextSchoolDay => 'Next school day, when classes start';

  @override
  String get meetingChoose => 'Choose the day and time';

  @override
  String meetingAt(String date, String time) {
    return '$date at $time';
  }

  @override
  String get summonReasonLabel => 'Reason';

  @override
  String get summonReasonOptional => 'Reason (optional, the alert says why)';

  @override
  String get summonReasonHelp => 'Kept in your records. Parents get the day and time, not the reason.';

  @override
  String get sendSummons => 'Send the summons';

  @override
  String summonsSent(String date, String time) {
    return 'Parents summoned for $date at $time';
  }

  @override
  String get parentsSummoned => 'Parents summoned';

  @override
  String get selectToJustify => 'Tap absences and lateness to select them, then justify them together.';

  @override
  String justifyCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Justify $count records',
      one: 'Justify 1 record',
    );
    return '$_temp0';
  }

  @override
  String get clearSelection => 'Clear the selection';

  @override
  String get recordMenu => 'More actions';

  @override
  String get cancelRecordAction => 'Cancel this record';

  @override
  String get justificationReason => 'Reason';

  @override
  String get reasonIllness => 'Illness';

  @override
  String get reasonFamily => 'Family reason';

  @override
  String get reasonSchoolActivity => 'School activity';

  @override
  String get reasonOther => 'Other reason';

  @override
  String get justificationNote => 'Note (optional)';

  @override
  String get illnessNoNote => '“Illness” is enough: no health details are kept.';

  @override
  String get justify => 'Justify';

  @override
  String justifiedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count records justified',
      one: '1 record justified',
    );
    return '$_temp0';
  }

  @override
  String get cancelRecordTitle => 'Cancel this record?';

  @override
  String get cancelRecordBody =>
      'Only for a mistake. The record stays in the school\'s files but no longer counts. Parents who were told get a correction.';

  @override
  String get cancelIncidentBody =>
      'Only for a mistake. The incident stays in the school\'s files but is no longer shown.';

  @override
  String get cancelReason => 'Reason for cancelling';

  @override
  String get confirmCancelRecord => 'Cancel the record';

  @override
  String get recordCancelled => 'Record cancelled';

  @override
  String get reportTitle => 'Students absent the whole day';

  @override
  String reportSummary(int students, int classes) {
    String _temp0 = intl.Intl.pluralLogic(students, locale: localeName, other: '$students students', one: '1 student');
    String _temp1 = intl.Intl.pluralLogic(classes, locale: localeName, other: '$classes classes', one: '1 class');
    return '$_temp0 in $_temp1';
  }

  @override
  String reportBy(String name) {
    return 'Discipline Master: $name';
  }

  @override
  String reportClassHeading(String className, int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count students', one: '1 student');
    return '$className: $_temp0';
  }

  @override
  String get reportColumnNumber => 'No.';

  @override
  String get reportColumnName => 'Name';

  @override
  String get reportColumnMatricule => 'Matricule';

  @override
  String reportUnrecorded(String list) {
    return 'Lessons not recorded, so full-day absences there cannot be known: $list';
  }

  @override
  String reportPeriod(int number) {
    return 'P$number';
  }

  @override
  String reportFooter(String date, String time) {
    return 'Generated by SchoolLink on $date at $time';
  }

  @override
  String reportPage(int page, int pages) {
    return 'Page $page/$pages';
  }

  @override
  String get fullDayEntry => 'Full-day absences';

  @override
  String get fullDayEntryHint => 'The day\'s list, as a PDF to share';

  @override
  String get fullDayPickDay => 'Day';

  @override
  String get fullDayDayHint => 'From 5 PM the day proposed is today; before, the previous school day.';

  @override
  String get fullDayGenerate => 'Generate the document';

  @override
  String get fullDayPreparing => 'Preparing the document…';

  @override
  String get fullDayShare => 'Share';

  @override
  String fullDayNobody(String date) {
    return 'No student was absent from all the school periods on $date.';
  }

  @override
  String get tabHistory => 'History';

  @override
  String get tabContacts => 'Contacts';

  @override
  String get contactsTeachers => 'Teachers';

  @override
  String get contactsDiscipline => 'Discipline';

  @override
  String get contactsNoPhone => 'No number';

  @override
  String get contactsNoDiscipline => 'No discipline staff for this class yet.';

  @override
  String get contactsNoTeachers => 'No teachers for this class yet.';

  @override
  String contactsCall(String name) {
    return 'Call $name';
  }

  @override
  String contactsSms(String name) {
    return 'Text $name';
  }

  @override
  String get contactsCannotOpen => 'Could not open the Phone app.';

  @override
  String get disciplineTab => 'Discipline';

  @override
  String get recordIncident => 'Record an incident';

  @override
  String get incidentRecorded => 'Incident recorded.';

  @override
  String get noIncidents => 'No incidents recorded.';

  @override
  String get noIncidentsBody => 'Record an incident to keep track of it in the student\'s file.';

  @override
  String get incidentOpen => 'Open';

  @override
  String get incidentClosed => 'Closed';

  @override
  String get closeIncident => 'Close';

  @override
  String get cancelIncident => 'Cancel the incident';

  @override
  String get incidentClosedDone => 'Incident closed.';

  @override
  String get incidentCancelled => 'Incident cancelled.';

  @override
  String get incidentCategory => 'Kind of incident';

  @override
  String get incidentMeasure => 'Measure taken';

  @override
  String get incidentDate => 'Date of the incident';

  @override
  String get incidentDescription => 'What happened';

  @override
  String get closeNote => 'Closing note (optional)';

  @override
  String incidentRecordedBy(String name) {
    return 'Recorded by $name';
  }

  @override
  String incidentCloseNote(String note) {
    return 'Closed: $note';
  }

  @override
  String get categoryBehaviour => 'Behaviour';

  @override
  String get categoryInsolence => 'Insolence';

  @override
  String get categoryFight => 'Fight';

  @override
  String get categoryAbsenteeism => 'Absenteeism';

  @override
  String get categoryCheating => 'Cheating';

  @override
  String get categoryDress => 'Dress';

  @override
  String get categoryPhone => 'Phone';

  @override
  String get categoryDamage => 'Damage';

  @override
  String get categoryOther => 'Other';

  @override
  String get measureNone => 'None';

  @override
  String get measureWarning => 'Warning';

  @override
  String get measureDetention => 'Detention';

  @override
  String get measureSuspension => 'Suspension';

  @override
  String get measureOther => 'Other measure';

  @override
  String incidentDateOn(String date) {
    return 'Date of the incident: $date';
  }

  @override
  String get messagesEntry => 'Messages to parents';

  @override
  String get messagesEntryHint => 'Write to the parents of a student or a class';

  @override
  String get newMessage => 'New message';

  @override
  String get noMessages => 'No message sent.';

  @override
  String get noMessagesBody => 'Parents get it in the app, or by SMS when they do not have it and you tick SMS.';

  @override
  String get messageToSchool => 'Whole school';

  @override
  String get messageWithSms => 'With SMS';

  @override
  String deliveryRecipients(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count recipients', one: '1 recipient');
    return '$_temp0';
  }

  @override
  String deliveryOpened(int count) {
    return '$count opened';
  }

  @override
  String deliveryReceived(int count) {
    return '$count received';
  }

  @override
  String deliveryPending(int count) {
    return '$count in progress';
  }

  @override
  String deliveryFailed(int count) {
    return '$count failed';
  }

  @override
  String get writeToParents => 'Write to parents';

  @override
  String get messageClass => 'Class';

  @override
  String get messageBody => 'Your message';

  @override
  String get messageSms => 'Also by SMS for parents without the app';

  @override
  String get messageSmsHint => 'Paid, counted in the month\'s SMS.';

  @override
  String messageCounts(int count, int byApp, int without) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count parents', one: '1 parent');
    return '$_temp0: $byApp by the app, $without without the app';
  }

  @override
  String messageCountsSms(int count, int byApp, int bySms) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count parents', one: '1 parent');
    return '$_temp0: $byApp by the app, $bySms by SMS';
  }

  @override
  String messageCost(String cost, String left) {
    return '≈ $cost FCFA · $left SMS left this month';
  }

  @override
  String messageUnits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count SMS per parent',
      one: '1 SMS per parent',
    );
    return '$_temp0';
  }

  @override
  String messageOverCap(int left) {
    return 'Not enough SMS left this month: $left left. Untick SMS or write to fewer parents.';
  }

  @override
  String get messageUnicode =>
      'The text has special letters (ê, ç, ô…): each SMS holds fewer characters and costs more.';

  @override
  String get messagePlain => 'Replace special letters';

  @override
  String get messagePlainHint => 'In the SMS only; the app keeps the exact text.';

  @override
  String get messageSmsSample => 'The SMS sent';

  @override
  String get messageChooseClass => 'Choose a class.';

  @override
  String get messagePreviewHint => 'Write the message to see who gets it.';

  @override
  String get messageSend => 'Send';

  @override
  String get messageConfirmTitle => 'Send this message?';

  @override
  String get messageConfirmBody => 'It cannot be changed or taken back.';

  @override
  String get messageSent => 'Message sent.';

  @override
  String messageAbout(String name) {
    return 'About $name';
  }

  @override
  String get dayTitle => 'Today\'s attendance';

  @override
  String get dayEntryHint => 'Each period\'s roll call, absentees and lateness';

  @override
  String dayPeriodShort(int number) {
    return 'P$number';
  }

  @override
  String dayRecorded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Recorded · $count absent',
      zero: 'Recorded · nobody absent',
    );
    return '$_temp0';
  }

  @override
  String get dayNotRecorded => 'Not recorded';

  @override
  String get dayUpcoming => 'Upcoming';

  @override
  String get dayNotRecordedHint => 'No roll call for this period. Only the teacher can record it.';

  @override
  String dayAbsentees(int number) {
    return 'Absent in P$number';
  }

  @override
  String get dayLateness => 'Lateness';

  @override
  String get dayCancelled => 'Cancelled';

  @override
  String get dayLatenessCancelled => 'Cancelled';

  @override
  String dayTotalNotRecorded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'periods not recorded',
      one: 'period not recorded',
    );
    return '$_temp0';
  }

  @override
  String dayTotalAbsent(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'students absent', one: 'student absent');
    return '$_temp0';
  }

  @override
  String dayTotalLateness(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'late arrivals', one: 'late arrival');
    return '$_temp0';
  }

  @override
  String get dayNoLessons => 'No lessons that day.';

  @override
  String get dayNoClasses => 'No class in your blocks.';

  @override
  String get dayChangeDate => 'Change day';

  @override
  String get daySeeHistory => 'See history';

  @override
  String get closedAlertsTitle => 'Decided alerts';

  @override
  String get closedAlertsHint => 'Decisions already taken';

  @override
  String get dashboardTitle => 'Dashboard';

  @override
  String get dashboardHint => 'Absences and lateness of your classes over a period';

  @override
  String get settingsTitle => 'Settings';

  @override
  String get settingsHint => 'Alert thresholds, start time, SMS digests';

  @override
  String decidedOn(String date) {
    return 'Decided on $date';
  }

  @override
  String get decidedSummon => 'Parents summoned';

  @override
  String get decidedDismiss => 'Dismissed';

  @override
  String get decidedDisciplinary => 'Disciplinary record opened';

  @override
  String get decidedRefer => 'Referred to the counsellor';

  @override
  String get noClosedAlerts => 'No alert decided yet.';

  @override
  String dashboardRange(String from, String to) {
    return '$from to $to';
  }

  @override
  String get kpiAbsenceRate => 'Absence rate';

  @override
  String get kpiUnjustified => 'Unjustified absences';

  @override
  String get kpiLateness => 'Lateness';

  @override
  String get kpiOpenAlerts => 'Alerts to decide';

  @override
  String get dashboardByClass => 'By class';

  @override
  String dashboardClassLine(int absent, int late) {
    String _temp0 = intl.Intl.pluralLogic(
      absent,
      locale: localeName,
      other: '$absent missed lessons',
      one: '1 missed lesson',
    );
    return '$_temp0 · $late late';
  }

  @override
  String get dashboardAlertsByRule => 'Alerts by rule';

  @override
  String dashboardRuleLine(int raised, int closed, int open) {
    return '$raised raised · $closed decided · $open open';
  }

  @override
  String get dashboardNoActivity => 'No roll call and no lateness recorded in this period.';

  @override
  String get dashboardCancelledNote => 'Cancelled records are not counted.';
}
