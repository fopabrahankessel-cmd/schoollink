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
}
