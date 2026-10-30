import '../../core/api/api_failure.dart';
import '../../l10n/app_localizations.dart';

/// The text to show for a failed request: the server's message, already in the user's language
/// (NFR-12), or the app's own words when the server could not be reached.
String failureText(Object error, AppLocalizations l) => switch (error) {
  ApiFailure(isNetwork: true) => l.networkError,
  ApiFailure(:final message?) => message,
  _ => l.unexpectedError,
};
