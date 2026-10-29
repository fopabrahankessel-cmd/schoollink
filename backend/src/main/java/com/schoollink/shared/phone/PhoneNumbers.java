package com.schoollink.shared.phone;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Phone numbers are stored in E.164 and only Cameroon numbers are accepted (D15): +237 followed by
 * 9 digits. Never log or put a phone number in a URL (NFR-9).
 */
public final class PhoneNumbers {

	private static final Pattern CAMEROON = Pattern.compile("^\\+237[0-9]{9}$");

	private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");

	private PhoneNumbers() {
	}

	/**
	 * Normalizes what a person types ("699 00 00 01", "00237699000001", "237699000001") to
	 * {@code +237699000001}; returns empty for anything that is not a Cameroon number.
	 */
	public static Optional<String> normalize(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String s = SEPARATORS.matcher(raw.strip()).replaceAll("");
		if (s.startsWith("00")) {
			s = "+" + s.substring(2);
		}
		else if (s.matches("^237[0-9]{9}$")) {
			s = "+" + s;
		}
		else if (s.matches("^[0-9]{9}$")) {
			s = "+237" + s;
		}
		return CAMEROON.matcher(s).matches() ? Optional.of(s) : Optional.empty();
	}

}
