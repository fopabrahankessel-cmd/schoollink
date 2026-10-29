package com.schoollink.auth.internal;

import com.schoollink.people.ScopeAssignments;
import com.schoollink.people.UserAccount;
import com.schoollink.people.UserAccounts;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.CurrentUserAuthentication;
import com.schoollink.shared.security.Role;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Turns a valid access token into the {@link CurrentUser} (NFR-8, SPEC §3). Only the token's
 * subject and school are trusted; roles, language and scope are read from the database on every
 * request, so a disabled account or a removed assignment stops working at once instead of when the
 * 15-minute token expires.
 */
@Component
class CurrentUserResolver implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserAccounts accounts;

	private final SchoolClasses classes;

	CurrentUserResolver(UserAccounts accounts, SchoolClasses classes) {
		this.accounts = accounts;
		this.classes = classes;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UserAccount account = accounts.findById(UUID.fromString(jwt.getSubject()))
			.filter(UserAccount::isActive)
			.filter(a -> Objects.equals(Objects.toString(a.schoolId(), null), jwt.getClaimAsString("school")))
			.orElseThrow(() -> new InvalidBearerTokenException("Unknown or inactive account"));
		return new CurrentUserAuthentication(resolve(account), jwt);
	}

	CurrentUser resolve(UserAccount account) {
		Set<Role> roles = account.roles().stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());
		if (account.schoolId() == null) {
			// the researcher: no school, anonymised data only (SPEC §3)
			return new CurrentUser(account.id(), null, account.language(), account.passwordChangeRequired(), roles,
					Map.of(), Set.of());
		}
		ScopeAssignments assigned = accounts.assignmentsOf(account.id(), account.schoolId());
		Map<Role, Set<UUID>> scope = new EnumMap<>(Role.class);
		if (roles.contains(Role.TEACHER)) {
			scope.put(Role.TEACHER, assigned.taughtClasses());
		}
		if (roles.contains(Role.DM)) {
			scope.put(Role.DM, classes.idsInBlocks(account.schoolId(), assigned.dmBlocks()));
		}
		if (roles.contains(Role.ASSISTANT_DM)) {
			scope.put(Role.ASSISTANT_DM, classes.idsInBlocks(account.schoolId(), assigned.assistantDmBlocks()));
		}
		Set<UUID> children = roles.contains(Role.PARENT) ? assigned.children() : Set.of();
		return new CurrentUser(account.id(), account.schoolId(), account.language(), account.passwordChangeRequired(),
				roles, scope, children);
	}

}
