// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get appTitle => 'SchoolLink';

  @override
  String get languageLabel => 'Langue';

  @override
  String get loginTitle => 'Connexion';

  @override
  String get loginIntro => 'Utilisez le numéro de téléphone ou l\'e-mail enregistré par votre école.';

  @override
  String get schoolLabel => 'École';

  @override
  String get schoolsLoadFailed => 'La liste des écoles n\'a pas pu être chargée.';

  @override
  String get retry => 'Réessayer';

  @override
  String get loginLabel => 'Numéro de téléphone ou e-mail';

  @override
  String get passwordLabel => 'Mot de passe';

  @override
  String get showPassword => 'Afficher le mot de passe';

  @override
  String get hidePassword => 'Masquer le mot de passe';

  @override
  String get signIn => 'Se connecter';

  @override
  String get accountMenu => 'Compte';

  @override
  String get signOut => 'Se déconnecter';

  @override
  String get signOutPendingTitle => 'Données pas encore envoyées';

  @override
  String signOutPendingBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count enregistrements attendent d\'être envoyés.',
      one: '1 enregistrement attend d\'être envoyé.',
    );
    return '$_temp0 Ils seront envoyés quand vous vous reconnecterez sur ce téléphone.';
  }

  @override
  String get cancel => 'Annuler';

  @override
  String get fieldRequired => 'Ce champ est obligatoire.';

  @override
  String get networkError => 'Pas de connexion au serveur. Vérifiez votre réseau et réessayez.';

  @override
  String get unexpectedError => 'Une erreur s\'est produite. Réessayez.';

  @override
  String get changePasswordTitle => 'Choisissez votre mot de passe';

  @override
  String get changePasswordIntro =>
      'Votre mot de passe actuel est temporaire. Choisissez votre propre mot de passe pour continuer.';

  @override
  String get temporaryPasswordLabel => 'Mot de passe temporaire';

  @override
  String get newPasswordLabel => 'Nouveau mot de passe';

  @override
  String get newPasswordHelp => 'Au moins 8 caractères.';

  @override
  String get confirmPasswordLabel => 'Confirmez le nouveau mot de passe';

  @override
  String get passwordTooShort => 'Utilisez au moins 8 caractères.';

  @override
  String get passwordTooLong => 'Utilisez au plus 72 caractères.';

  @override
  String get passwordsDoNotMatch => 'Les deux mots de passe ne correspondent pas.';

  @override
  String get savePassword => 'Enregistrer le mot de passe';

  @override
  String get webOnlyTitle => 'Utilisez le tableau de bord web';

  @override
  String get webOnlyBody =>
      'Votre compte fonctionne sur le tableau de bord web de SchoolLink. Ouvrez-le sur un ordinateur.';

  @override
  String get navSessions => 'Appel';

  @override
  String get navGate => 'Retards';

  @override
  String get navAlerts => 'Alertes';

  @override
  String get navCases => 'Suivis';

  @override
  String get navChildren => 'Enfants';

  @override
  String get navInbox => 'Notifications';

  @override
  String get comingSoon => 'Cet écran arrive dans une prochaine mise à jour.';

  @override
  String get sessionToRecord => 'À faire';

  @override
  String sessionRecorded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Enregistré, $count absents',
      one: 'Enregistré, 1 absent',
      zero: 'Enregistré, aucun absent',
    );
    return '$_temp0';
  }

  @override
  String get sessionNow => 'En cours';

  @override
  String sessionTimes(String start, String end) {
    return '$start à $end';
  }

  @override
  String get notASchoolDay => 'Pas de cours aujourd\'hui : ce n\'est pas un jour de classe.';

  @override
  String get noLessonsToday => 'Vous n\'avez aucun cours à l\'emploi du temps aujourd\'hui.';

  @override
  String offlineSince(String time) {
    return 'Pas de connexion. Affiché tel que mis à jour à $time.';
  }

  @override
  String get attendanceHint => 'Touchez les élèves absents.';

  @override
  String presentCount(int count) {
    return 'Présents : $count';
  }

  @override
  String absentCount(int count) {
    return 'Absents : $count';
  }

  @override
  String get absentLabel => 'Absent';

  @override
  String get save => 'Enregistrer';

  @override
  String confirmAbsentees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Ces $count élèves sont marqués absents. Est-ce correct ?',
      one: 'Cet élève est marqué absent. Est-ce correct ?',
      zero: 'Aucun élève n\'est marqué absent. Est-ce correct ?',
    );
    return '$_temp0';
  }

  @override
  String get back => 'Retour';

  @override
  String get yes => 'Oui';

  @override
  String get confirmNotifyTitle => 'Confirmer et prévenir les parents ?';

  @override
  String get confirmNotifyBody => 'Une fois confirmée, la séance est verrouillée et ne peut plus être modifiée.';

  @override
  String get confirm => 'Confirmer';

  @override
  String sessionLockedAt(String time) {
    return 'Verrouillée : enregistrée à $time.';
  }

  @override
  String get absentStudents => 'Élèves absents';

  @override
  String get noAbsentStudents => 'Aucun élève absent.';

  @override
  String absentTotal(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count élèves absents.',
      one: '1 élève absent.',
      zero: 'Aucun élève absent.',
    );
    return '$_temp0';
  }

  @override
  String get syncWaiting => 'Enregistrée sur ce téléphone, en attente d\'envoi.';

  @override
  String get syncSent => 'Envoyée à l\'école.';

  @override
  String get syncNotAccepted => 'Refusée par l\'école.';

  @override
  String get leaveTitle => 'Quitter sans enregistrer ?';

  @override
  String get leaveBody => 'Les absences marquées seront perdues.';

  @override
  String get leave => 'Quitter';

  @override
  String get rosterUnavailable =>
      'La liste de la classe n\'est pas encore sur ce téléphone. Connectez-vous une fois au réseau pour la charger.';

  @override
  String get sessionNotFound => 'Cette séance n\'est pas dans la liste du jour.';

  @override
  String get signedInAs => 'Connecté en tant que';

  @override
  String get roleAdmin => 'Administrateur';

  @override
  String get roleTeacher => 'Enseignant';

  @override
  String get roleAssistantDm => 'Surveillant général adjoint';

  @override
  String get roleDm => 'Surveillant général';

  @override
  String get roleCounsellor => 'Conseiller d\'orientation';

  @override
  String get roleParent => 'Parent';

  @override
  String get roleResearcher => 'Chercheur';

  @override
  String pendingOnline(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count enregistrements en attente d\'envoi.',
      one: '1 enregistrement en attente d\'envoi.',
    );
    return '$_temp0';
  }

  @override
  String pendingOffline(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Pas de réseau : $count enregistrements seront envoyés à son retour.',
      one: 'Pas de réseau : 1 enregistrement sera envoyé à son retour.',
    );
    return '$_temp0';
  }

  @override
  String get sendNow => 'Envoyer';

  @override
  String get todaySchedule => 'Programme du jour';

  @override
  String get noRollToday => 'Pas d\'appel aujourd\'hui';

  @override
  String rollsLeft(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count appels à faire',
      one: '1 appel à faire',
      zero: 'Tous les appels sont faits',
    );
    return '$_temp0';
  }

  @override
  String rollsProgress(int done, int total) {
    return '$done sur $total enregistrés';
  }

  @override
  String get takeRoll => 'Faire l\'appel';

  @override
  String get findStudent => 'Rechercher un élève';

  @override
  String latenessToday(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count retards aujourd\'hui',
      one: '1 retard aujourd\'hui',
      zero: 'Aucun retard aujourd\'hui',
    );
    return '$_temp0';
  }

  @override
  String classesStartAt(String time) {
    return 'Début des cours : $time';
  }

  @override
  String get searchStudentHint => 'Nom ou matricule';

  @override
  String get clearSearch => 'Effacer';

  @override
  String get gateStudentsUnavailable =>
      'La liste des élèves n\'est pas encore sur ce téléphone. Connectez-vous une fois au réseau pour la charger.';

  @override
  String get noStudentFound => 'Aucun élève ne correspond à cette recherche.';

  @override
  String searchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: '$count élèves', one: '1 élève');
    return '$_temp0';
  }

  @override
  String get typeMore => 'Tapez plus de lettres pour affiner la liste.';

  @override
  String get latenessListTitle => 'Retards du jour';

  @override
  String get noLatenessToday => 'Aucun retard enregistré aujourd\'hui.';

  @override
  String get noLatenessTodayHint => 'Cherchez un élève ci-dessus pour noter son arrivée.';

  @override
  String classAndMatricule(String className, String matricule) {
    return '$className, matricule $matricule';
  }

  @override
  String alreadyLateAt(String time) {
    return 'En retard à $time';
  }

  @override
  String get arrivalTimeLabel => 'Heure d\'arrivée';

  @override
  String get changeTime => 'Modifier';

  @override
  String arrivalNotLate(String start) {
    return 'Les cours commencent à $start : arriver à cette heure ou avant n\'est pas un retard.';
  }

  @override
  String get arrivalInFuture => 'L\'heure d\'arrivée ne peut pas être après l\'heure actuelle.';

  @override
  String get latenessIndependent => 'Noté à part de l\'appel en classe : il ne le modifie pas.';

  @override
  String get recordLateness => 'Enregistrer le retard';

  @override
  String latenessSaved(String name, String time) {
    return 'Retard enregistré : $name à $time.';
  }

  @override
  String get latenessWaiting => 'Sur ce téléphone, en attente d\'envoi';

  @override
  String latenessRefused(String name, String time) {
    return 'Refusé par l\'école : $name à $time.';
  }

  @override
  String get justifiedLabel => 'Justifié';

  @override
  String get inYourClasses => 'Dans les classes dont vous avez la charge';

  @override
  String get alertsTitle => 'Alertes à traiter';

  @override
  String get notificationChannelName => 'Absences et retards';

  @override
  String get notificationChannelDescription =>
      'Absences, retards et convocations de vos enfants, et alertes de l\'école.';

  @override
  String get parentQuestion => 'Parent ?';

  @override
  String get activateWithCode => 'Activer avec mon code d\'invitation';

  @override
  String get activationTitle => 'Activer mon compte';

  @override
  String activationStep(int step) {
    return 'Étape $step sur 2';
  }

  @override
  String get activationHeadline => 'Votre code d\'invitation';

  @override
  String get activationIntro =>
      'L\'établissement vous l\'a envoyé par SMS. Saisissez-le avec le numéro de téléphone donné à l\'établissement.';

  @override
  String get invitationCodeLabel => 'Code d\'invitation';

  @override
  String get invitationCodeHelp => 'Tel qu\'écrit dans le SMS de l\'établissement';

  @override
  String get phoneLabel => 'Numéro de téléphone';

  @override
  String get phoneHelp => 'Le numéro donné à l\'établissement';

  @override
  String get phoneInvalid => 'Saisissez un numéro camerounais à 9 chiffres, par exemple 6 99 00 00 01.';

  @override
  String get sendSmsCode => 'Recevoir un code par SMS';

  @override
  String get otpTitle => 'Saisissez le code reçu';

  @override
  String otpSentTo(String phone) {
    return 'Nous avons envoyé un code à 6 chiffres par SMS au $phone.';
  }

  @override
  String get otpLabel => 'Code de vérification';

  @override
  String get otpInvalid => 'Saisissez les 6 chiffres du code.';

  @override
  String otpValidUntil(String time) {
    return 'Le code est valable jusqu\'à $time.';
  }

  @override
  String otpResent(String time) {
    return 'Un nouveau code a été envoyé. Il est valable jusqu\'à $time.';
  }

  @override
  String get activateAccount => 'Activer mon compte';

  @override
  String get resendCode => 'Renvoyer un code';

  @override
  String get changeNumber => 'Changer de numéro';

  @override
  String get parentSignInWithSms => 'Me connecter avec un code par SMS';

  @override
  String get parentSignInTitle => 'Connexion parent';

  @override
  String get parentSignInHeadline => 'Votre numéro de téléphone';

  @override
  String get parentSignInIntro =>
      'Choisissez votre établissement et saisissez le numéro que vous lui avez donné. Nous vous enverrons un code par SMS. Première fois ? Utilisez plutôt votre code d\'invitation.';

  @override
  String otpSentIfAccount(String phone) {
    return 'Si le $phone a un compte parent SchoolLink, un code à 6 chiffres lui a été envoyé par SMS.';
  }

  @override
  String get loading => 'Chargement…';

  @override
  String get today => 'Aujourd\'hui';

  @override
  String get yesterday => 'Hier';

  @override
  String unreadMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count messages non lus',
      one: '1 message non lu',
      zero: 'Tout est lu',
    );
    return '$_temp0';
  }

  @override
  String get unread => 'Non lu';

  @override
  String get inboxEmpty => 'Aucun message pour l\'instant';

  @override
  String get inboxEmptyHint => 'Les absences et retards de vos enfants s\'afficheront ici, le jour même.';

  @override
  String get olderMessages => 'Afficher les messages plus anciens';

  @override
  String get olderMessagesFailed =>
      'Les messages plus anciens n\'ont pas pu être chargés. Vérifiez votre connexion et réessayez.';

  @override
  String get messageNotFound => 'Ce message n\'est plus dans vos notifications.';

  @override
  String get backToInbox => 'Retour aux notifications';

  @override
  String receivedAt(String date, String time) {
    return 'Reçu le $date à $time';
  }

  @override
  String get seeHistory => 'Voir les absences et retards';

  @override
  String get eventAbsence => 'Absence';

  @override
  String get eventLateness => 'Retard';

  @override
  String get eventCorrection => 'Correction';

  @override
  String get eventSummons => 'Convocation';

  @override
  String get eventMessage => 'Message';

  @override
  String get eventAnnouncement => 'Annonce';

  @override
  String get eventOther => 'Notification';

  @override
  String yourChildren(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Vos $count enfants',
      one: 'Votre enfant',
      zero: 'Aucun enfant',
    );
    return '$_temp0';
  }

  @override
  String get childrenHint => 'Touchez un enfant pour voir ses absences et retards.';

  @override
  String get noChildren => 'Aucun enfant n\'est lié à votre compte';

  @override
  String get noChildrenHint => 'Contactez l\'établissement pour vérifier le numéro qu\'il a pour vous.';

  @override
  String classLabel(String name) {
    return 'Classe : $name';
  }

  @override
  String get historyTitle => 'Historique';

  @override
  String historyOf(String name) {
    return 'Historique de $name';
  }

  @override
  String periodFromTo(String from, String to) {
    return 'Du $from au $to';
  }

  @override
  String absentPeriodsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'heures d\'absence',
      one: 'heure d\'absence',
    );
    return '$_temp0';
  }

  @override
  String unjustifiedAbsences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'dont $count non justifiées',
      one: 'dont 1 non justifiée',
      zero: 'aucune non justifiée',
    );
    return '$_temp0';
  }

  @override
  String latenessLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(count, locale: localeName, other: 'retards', one: 'retard');
    return '$_temp0';
  }

  @override
  String unjustifiedLateness(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'dont $count non justifiés',
      one: 'dont 1 non justifié',
      zero: 'aucun non justifié',
    );
    return '$_temp0';
  }

  @override
  String get dayByDay => 'Jour par jour';

  @override
  String historyEmpty(String name) {
    return 'Aucune absence ni retard pour $name';
  }

  @override
  String absentFromLesson(String time) {
    return 'Cours de $time';
  }

  @override
  String lateArrivedAt(String time) {
    return 'Arrivée à $time';
  }

  @override
  String get absenceJustified => 'Justifiée';

  @override
  String get absenceUnjustified => 'Non justifiée';

  @override
  String get latenessJustified => 'Justifié';

  @override
  String get latenessUnjustified => 'Non justifié';

  @override
  String alertsToDecide(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count alertes à traiter',
      one: '1 alerte à traiter',
      zero: 'Aucune alerte à traiter',
    );
    return '$_temp0';
  }

  @override
  String get noAlertsTitle => 'Aucune alerte à traiter';

  @override
  String get noAlertsBody =>
      'Les absences et retards répétés de vos classes apparaissent ici pour votre décision. L\'application ne décide jamais à votre place.';

  @override
  String get ruleFullDayAbsence => 'Absent toute la journée';

  @override
  String get ruleConsecutiveLateness => 'En retard plusieurs jours de suite';

  @override
  String get ruleUnjustifiedAbsences => 'Absences non justifiées';

  @override
  String get ruleOther => 'Alerte';

  @override
  String evidenceFullDay(int count, String dates) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Absent à tous les cours $count jours de suite : $dates',
      one: 'Absent à tous les cours le $dates',
    );
    return '$_temp0';
  }

  @override
  String evidenceLateness(int count, String dates) {
    return 'En retard $count jours de classe de suite : $dates';
  }

  @override
  String evidenceUnjustified(int count, String from, String to) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cours manqués sans justification du $from au $to',
      one: '1 cours manqué sans justification du $from au $to',
    );
    return '$_temp0';
  }

  @override
  String alertRaisedOn(String date) {
    return 'Signalée le $date';
  }

  @override
  String get alertTitle => 'Alerte';

  @override
  String get whatTheRuleFound => 'Ce que la règle a relevé';

  @override
  String get yourDecision => 'Votre décision';

  @override
  String get decisionHint => 'SchoolLink ne fait que signaler. C\'est vous qui décidez de la suite.';

  @override
  String get decisionSummon => 'Convoquer les parents';

  @override
  String get decisionSummonDetail => 'Ils sont prévenus tout de suite, par l\'application ou par SMS';

  @override
  String get decisionDismiss => 'Classer sans suite';

  @override
  String get decisionDismissDetail => 'Fermer l\'alerte, avec le motif';

  @override
  String get decisionDisciplinary => 'Ouvrir un dossier disciplinaire';

  @override
  String get decisionDisciplinaryDetail => 'Décrivez le problème à consigner';

  @override
  String get decisionRefer => 'Orienter vers le conseiller';

  @override
  String get decisionReferDetail => 'Un dossier est ouvert pour le conseiller';

  @override
  String get noteDismissLabel => 'Motif du classement';

  @override
  String get noteDisciplinaryLabel => 'Le problème à consigner';

  @override
  String get noteReferLabel => 'Pour le conseiller (facultatif)';

  @override
  String get noteRequired => 'Écrivez quelques mots.';

  @override
  String get decisionRecorded => 'Décision enregistrée';

  @override
  String get alertAlreadyDecided => 'Cette alerte n\'est plus ouverte : elle a été traitée.';

  @override
  String summonTitle(String name) {
    return 'Convoquer les parents de $name';
  }

  @override
  String get meetingLabel => 'Rendez-vous';

  @override
  String get meetingNextSchoolDay => 'Prochain jour de classe, à l\'heure de la rentrée des cours';

  @override
  String get meetingChoose => 'Choisir le jour et l\'heure';

  @override
  String meetingAt(String date, String time) {
    return '$date à $time';
  }

  @override
  String get summonReasonLabel => 'Motif';

  @override
  String get summonReasonOptional => 'Motif (facultatif, l\'alerte dit pourquoi)';

  @override
  String get summonReasonHelp => 'Gardé dans vos dossiers. Les parents reçoivent le jour et l\'heure, pas le motif.';

  @override
  String get sendSummons => 'Envoyer la convocation';

  @override
  String summonsSent(String date, String time) {
    return 'Parents convoqués le $date à $time';
  }

  @override
  String get parentsSummoned => 'Parents convoqués';

  @override
  String get selectToJustify => 'Touchez les absences et retards pour les choisir, puis justifiez-les ensemble.';

  @override
  String justifyCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Justifier $count éléments',
      one: 'Justifier 1 élément',
    );
    return '$_temp0';
  }

  @override
  String get clearSelection => 'Effacer la sélection';

  @override
  String get recordMenu => 'Autres actions';

  @override
  String get cancelRecordAction => 'Annuler cet enregistrement';

  @override
  String get justificationReason => 'Motif';

  @override
  String get reasonIllness => 'Maladie';

  @override
  String get reasonFamily => 'Raison familiale';

  @override
  String get reasonSchoolActivity => 'Activité scolaire';

  @override
  String get reasonOther => 'Autre motif';

  @override
  String get justificationNote => 'Note (facultative)';

  @override
  String get illnessNoNote => '« Maladie » suffit : aucun détail de santé n\'est conservé.';

  @override
  String get justify => 'Justifier';

  @override
  String justifiedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count éléments justifiés',
      one: '1 élément justifié',
    );
    return '$_temp0';
  }

  @override
  String get cancelRecordTitle => 'Annuler cet enregistrement ?';

  @override
  String get cancelRecordBody =>
      'Seulement pour une erreur. L\'enregistrement reste dans les dossiers de l\'école mais ne compte plus. Les parents déjà prévenus reçoivent une correction.';

  @override
  String get cancelReason => 'Motif de l\'annulation';

  @override
  String get confirmCancelRecord => 'Annuler l\'enregistrement';

  @override
  String get recordCancelled => 'Enregistrement annulé';
}
