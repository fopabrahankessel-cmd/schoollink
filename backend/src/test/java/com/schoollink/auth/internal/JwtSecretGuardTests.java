package com.schoollink.auth.internal;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** NFR-7: the JWT signing secret fails closed; the dev secret only works under dev or test. */
class JwtSecretGuardTests {

	static final String DEV_SECRET = "dev-only-insecure-jwt-secret-change-me-0123456789";

	static final String REAL_SECRET = "Zq8vT2mN6pW1xR4yK7bH3cJ9dF5gL0sA";

	private final SecurityConfig config = new SecurityConfig();

	@Test
	void missingSecretRefusesToStart() {
		assertThatIllegalStateException().isThrownBy(() -> config.jwtKey(props(null), env()))
			.withMessageContaining("Set JWT_SECRET");
		assertThatIllegalStateException().isThrownBy(() -> config.jwtKey(props("  "), env()));
	}

	@Test
	void shortSecretRefusesToStart() {
		assertThatIllegalStateException().isThrownBy(() -> config.jwtKey(props("too-short"), env("dev")));
	}

	@Test
	void devSecretRefusesToStartWithoutAnyProfile() {
		assertThatIllegalStateException().isThrownBy(() -> config.jwtKey(props(DEV_SECRET), env()));
	}

	@Test
	void devSecretRefusesToStartUnderOtherProfiles() {
		assertThatIllegalStateException().isThrownBy(() -> config.jwtKey(props(DEV_SECRET), env("prod")));
		assertThatIllegalStateException().isThrownBy(() -> config.jwtKey(props(DEV_SECRET), env("staging")));
	}

	@Test
	void devSecretIsAcceptedUnderDevOrTest() {
		assertThat(config.jwtKey(props(DEV_SECRET), env("dev"))).isNotNull();
		assertThat(config.jwtKey(props(DEV_SECRET), env("test"))).isNotNull();
	}

	@Test
	void realSecretWorksWithoutAProfile() {
		assertThat(config.jwtKey(props(REAL_SECRET), env()).getAlgorithm()).isEqualTo("HmacSHA256");
	}

	private static JwtProperties props(String secret) {
		return new JwtProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30));
	}

	private static MockEnvironment env(String... profiles) {
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles(profiles);
		return env;
	}

}
