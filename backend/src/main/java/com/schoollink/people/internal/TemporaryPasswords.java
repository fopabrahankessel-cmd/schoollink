package com.schoollink.people.internal;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * D18: temporary staff passwords, read out or written down by the admin, so without look-alike
 * characters (0/O, 1/l/I). 12 characters from 57 give about 70 bits.
 */
@Component
class TemporaryPasswords {

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	private static final int LENGTH = 12;

	private final SecureRandom random = new SecureRandom();

	String generate() {
		StringBuilder password = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return password.toString();
	}

}
