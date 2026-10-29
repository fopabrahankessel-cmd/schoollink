package com.schoollink.shared.security;

import java.util.stream.Collectors;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A request authenticated by an access token, whose principal is the {@link CurrentUser} resolved
 * from the database (NFR-8). Authorities are {@code ROLE_ADMIN}, {@code ROLE_DM}, ... from the
 * current role assignments, not from the token, so a removed role stops working at once.
 */
public class CurrentUserAuthentication extends AbstractAuthenticationToken {

	private final CurrentUser user;

	private final Object token;

	public CurrentUserAuthentication(CurrentUser user, Object token) {
		super(user.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toSet()));
		this.user = user;
		this.token = token;
		setAuthenticated(true);
	}

	@Override
	public CurrentUser getPrincipal() {
		return user;
	}

	@Override
	public Object getCredentials() {
		return token;
	}

	@Override
	public String getName() {
		return user.userId().toString();
	}

}
