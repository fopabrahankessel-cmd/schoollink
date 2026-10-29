package com.schoollink;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the modular monolith (CLAUDE.md): no dependency cycles between modules and no access
 * to another module's internal packages.
 */
class ModularityTests {

	static final ApplicationModules modules = ApplicationModules.of(SchoolLinkApplication.class);

	@Test
	void verifiesModuleStructure() {
		modules.verify();
	}

	@Test
	void detectsAllModules() {
		assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
			.containsExactlyInAnyOrder("auth", "school", "people", "attendance", "lateness", "justification",
					"detection", "discipline", "notification", "reporting", "audit", "shared");
	}

}
