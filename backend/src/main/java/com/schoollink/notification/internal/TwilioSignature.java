package com.schoollink.notification.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Twilio's request signature ({@code X-Twilio-Signature}): Base64 of the HMAC-SHA1, keyed with the
 * account's auth token, of the URL Twilio called followed by each POST parameter's name and value,
 * names in alphabetical order (D32).
 */
final class TwilioSignature {

	private TwilioSignature() {
	}

	static String of(String authToken, String url, Map<String, String[]> params) {
		StringBuilder data = new StringBuilder(url);
		for (Map.Entry<String, String[]> param : new TreeMap<>(params).entrySet()) {
			String[] values = param.getValue().clone();
			Arrays.sort(values);
			for (String value : values) {
				data.append(param.getKey()).append(value);
			}
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(data.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA1 is not available", ex);
		}
	}

	/** In constant time; {@code false} without a signature or without a token to check it with. */
	static boolean valid(String authToken, String url, Map<String, String[]> params, String signature) {
		if (authToken.isEmpty() || url.isEmpty() || signature == null || signature.isEmpty()) {
			return false;
		}
		return MessageDigest.isEqual(of(authToken, url, params).getBytes(StandardCharsets.UTF_8),
				signature.getBytes(StandardCharsets.UTF_8));
	}

}
