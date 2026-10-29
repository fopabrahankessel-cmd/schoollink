package com.schoollink.auth.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NFR-7: access token 15 min, refresh token 30 days. {@code secret} signs the access tokens
 * (HS256, at least 32 bytes); production sets it through the JWT_SECRET environment variable.
 */
@ConfigurationProperties("schoollink.jwt")
record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

	static final String DEV_SECRET_PREFIX = "dev-only-";

}
