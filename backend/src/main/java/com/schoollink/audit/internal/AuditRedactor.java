package com.schoollink.audit.internal;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Removes secrets from audit snapshots and masks phone numbers (NFR-9): an audit row may show that
 * a phone changed, never the full number.
 */
final class AuditRedactor {

	static final String REDACTED = "[redacted]";

	/** Compared after lower-casing and removing '_' so passwordHash and password_hash both match. */
	private static final Set<String> SECRET_KEYS = Set.of("password", "passwordhash", "codehash", "code", "otp",
			"token", "accesstoken", "refreshtoken", "fcmtoken", "bodyencrypted");

	private static final Set<String> PHONE_KEYS = Set.of("phone", "phonenumber");

	private AuditRedactor() {
	}

	static JsonNode redact(JsonNode node) {
		if (node instanceof ObjectNode object) {
			for (String name : object.propertyNames().stream().toList()) {
				String key = name.replace("_", "").toLowerCase(Locale.ROOT);
				JsonNode value = object.get(name);
				if (SECRET_KEYS.contains(key) && !value.isNull()) {
					object.put(name, REDACTED);
				}
				else if (PHONE_KEYS.contains(key) && value.isString()) {
					object.put(name, maskPhone(value.asString()));
				}
				else {
					redact(value);
				}
			}
		}
		else if (node instanceof ArrayNode array) {
			array.forEach(AuditRedactor::redact);
		}
		return node;
	}

	/** +237699123456 -> +237*******56 */
	static String maskPhone(String phone) {
		if (phone.length() <= 6) {
			return REDACTED;
		}
		String prefix = phone.substring(0, 4);
		String suffix = phone.substring(phone.length() - 2);
		return prefix + "*".repeat(phone.length() - 6) + suffix;
	}

}
