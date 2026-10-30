/// The roles of SPEC §3, as the API names them.
enum Role {
  admin('ADMIN'),
  teacher('TEACHER'),
  assistantDm('ASSISTANT_DM'),
  dm('DM'),
  counsellor('COUNSELLOR'),
  parent('PARENT'),
  researcher('RESEARCHER');

  const Role(this.apiName);

  final String apiName;

  static Role? fromApi(String name) {
    for (final role in values) {
      if (role.apiName == name) {
        return role;
      }
    }
    return null;
  }
}

/// The signed-in user, as `GET /me` returns it: enough to pick the screens and the language.
class UserInfo {
  const UserInfo({
    required this.id,
    required this.schoolId,
    required this.roles,
    required this.language,
    required this.passwordChangeRequired,
  });

  factory UserInfo.fromJson(Map<String, dynamic> json) => UserInfo(
    id: json['id'] as String,
    schoolId: json['schoolId'] as String?,
    roles: {for (final name in (json['roles'] as List).cast<String>()) ?Role.fromApi(name)},
    language: json['language'] as String? ?? 'FR',
    passwordChangeRequired: json['passwordChangeRequired'] as bool? ?? false,
  );

  final String id;
  final String? schoolId;
  final Set<Role> roles;

  /// `FR` or `EN` (NFR-12).
  final String language;

  /// D18: a temporary password must be replaced before anything else.
  final bool passwordChangeRequired;

  Map<String, dynamic> toJson() => {
    'id': id,
    'schoolId': schoolId,
    'roles': [for (final role in roles) role.apiName],
    'language': language,
    'passwordChangeRequired': passwordChangeRequired,
  };
}

/// What the login, refresh, verify and password endpoints return.
class TokenResponse {
  const TokenResponse({required this.accessToken, required this.refreshToken, required this.user});

  factory TokenResponse.fromJson(Map<String, dynamic> json) => TokenResponse(
    accessToken: json['accessToken'] as String,
    refreshToken: json['refreshToken'] as String,
    user: UserInfo.fromJson(json['user'] as Map<String, dynamic>),
  );

  final String accessToken;
  final String refreshToken;
  final UserInfo user;
}
