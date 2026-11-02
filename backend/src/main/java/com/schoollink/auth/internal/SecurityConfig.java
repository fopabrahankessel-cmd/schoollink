package com.schoollink.auth.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless API secured by JWT bearer tokens (SPEC §9, NFR-7). Only health, login, refresh and the
 * school picker are public; everything else needs a valid access token.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

	static final String ISSUER = "schoollink";

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, SecurityErrorHandler errors, CurrentUserResolver currentUser)
			throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
						"/api/v1/auth/logout", "/api/v1/auth/parent/activate", "/api/v1/auth/parent/verify",
						"/api/v1/auth/parent/sign-in", "/api/v1/auth/parent/sign-in/verify").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/schools").permitAll()
				// BR-24: the SMS gateway signs its delivery reports instead of signing in (D32)
				.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/sms/delivery").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(currentUser))
				.authenticationEntryPoint(errors)
				.accessDeniedHandler(errors))
			.exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
			.addFilterAfter(new PasswordChangeGate(errors), BearerTokenAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtKey) {
		return NimbusJwtEncoder.withSecretKey(jwtKey).algorithm(MacAlgorithm.HS256).build();
	}

	/**
	 * Expiry is checked on the clock the tokens are issued with ({@link AccessTokens}), not the
	 * system clock, so issuing and checking always agree (and tests on a fixed clock keep working).
	 */
	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtKey, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator expiry = new JwtTimestampValidator();
		expiry.setClock(clock);
		decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new JwtIssuerValidator(ISSUER), expiry));
		return decoder;
	}

	/**
	 * Fails closed: there is no default secret, and a development secret ("dev-only-...") is
	 * accepted only when the dev or test profile is explicitly active.
	 */
	@Bean
	SecretKey jwtKey(JwtProperties properties, Environment environment) {
		String secret = properties.secret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException("Set JWT_SECRET (at least 32 bytes) to sign access tokens");
		}
		if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
		}
		if (secret.startsWith(JwtProperties.DEV_SECRET_PREFIX) && !environment.acceptsProfiles(Profiles.of("dev | test"))) {
			throw new IllegalStateException(
					"The development JWT secret is only allowed with the dev or test profile; set JWT_SECRET");
		}
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

}
