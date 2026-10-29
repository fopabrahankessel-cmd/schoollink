package com.schoollink.audit.internal;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** NFR-9: secrets never reach the audit log and phone numbers are masked, at any depth. */
class AuditRedactorTests {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void masksPhoneKeepingCountryCodeAndLastTwoDigits() {
		assertThat(AuditRedactor.maskPhone("+237699123456")).isEqualTo("+237*******56");
		assertThat(AuditRedactor.maskPhone("123")).isEqualTo("[redacted]");
	}

	@Test
	void redactsSecretsAndPhonesInNestedObjectsAndArrays() {
		JsonNode node = json.valueToTree(Map.of("guardians",
				List.of(Map.of("phone_number", "+237699000011", "refresh_token", "t0k3n")), "user",
				Map.of("passwordHash", "$2a$10$x", "email", "dm@example.test")));

		JsonNode redacted = AuditRedactor.redact(node);

		assertThat(redacted.at("/guardians/0/phone_number").asString()).isEqualTo("+237*******11");
		assertThat(redacted.at("/guardians/0/refresh_token").asString()).isEqualTo("[redacted]");
		assertThat(redacted.at("/user/passwordHash").asString()).isEqualTo("[redacted]");
		assertThat(redacted.at("/user/email").asString()).isEqualTo("dm@example.test");
	}

	@Test
	void leavesNullSecretsAsNull() {
		JsonNode node = json.valueToTree(java.util.Collections.singletonMap("password_hash", null));

		assertThat(AuditRedactor.redact(node).get("password_hash").isNull()).isTrue();
	}

}
