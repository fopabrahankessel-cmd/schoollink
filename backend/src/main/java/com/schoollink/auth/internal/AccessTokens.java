package com.schoollink.auth.internal;

import com.schoollink.people.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues 15-minute access tokens (NFR-7). The school comes from the token, never from a request
 * (NFR-8); the class scope is resolved per request (task 1.8), so assignment changes apply at once.
 */
@Component
class AccessTokens {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	private final Clock clock;

	AccessTokens(JwtEncoder encoder, JwtProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	String issue(UserAccount account) {
		Instant now = clock.instant();
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer(SecurityConfig.ISSUER)
			.subject(account.id().toString())
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenTtl()))
			.claim("roles", List.copyOf(account.roles()))
			.claim("lang", account.language())
			// D18: rejected once the password changes, is reset, or the account is disabled
			.claim("cv", account.credentialsVersion());
		if (account.schoolId() != null) {
			claims.claim("school", account.schoolId().toString());
		}
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	long ttlSeconds() {
		return properties.accessTokenTtl().toSeconds();
	}

}
