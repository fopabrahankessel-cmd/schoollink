package com.schoollink.auth.internal;

import com.schoollink.people.UserAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The signed-in user as the clients see it: enough to pick the role-based screens and the language. */
record UserInfo(UUID id, UUID schoolId, List<String> roles, String language) {

	static UserInfo of(UserAccount account) {
		return new UserInfo(account.id(), account.schoolId(), account.roles().stream().sorted().toList(),
				account.language());
	}

	static UserInfo of(Jwt jwt) {
		String school = jwt.getClaimAsString("school");
		return new UserInfo(UUID.fromString(jwt.getSubject()), school == null ? null : UUID.fromString(school),
				jwt.getClaimAsStringList("roles").stream().sorted().toList(), jwt.getClaimAsString("lang"));
	}

}
