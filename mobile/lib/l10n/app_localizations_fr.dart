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
}
