package com.schoollink.auth.internal;

import com.schoollink.people.UserAccount;
import com.schoollink.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;

/** The signed-in user as the clients see it: enough to pick the role-based screens and the language. */
record UserInfo(UUID id, UUID schoolId, List<String> roles, String language, boolean passwordChangeRequired) {

	static UserInfo of(UserAccount account) {
		return new UserInfo(account.id(), account.schoolId(), account.roles().stream().sorted().toList(),
				account.language(), account.passwordChangeRequired());
	}

	static UserInfo of(CurrentUser user) {
		return new UserInfo(user.userId(), user.schoolId(), user.roles().stream().map(Enum::name).sorted().toList(),
				user.language(), user.passwordChangeRequired());
	}

}
