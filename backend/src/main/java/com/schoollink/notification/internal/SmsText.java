package com.schoollink.notification.internal;

import java.text.Normalizer;

/**
 * D38: how many SMS a text takes, and its "plain" form for the "replace special letters" option.
 * A text in the GSM-7 alphabet fits 160 characters in one SMS, 153 per SMS when it needs several;
 * the extension characters count twice. Any other letter (ê, ç, ô, œ...) makes the whole text
 * Unicode: 70 characters in one SMS, 67 per SMS when it needs several.
 */
final class SmsText {

	/** The GSM 03.38 basic alphabet. é, è, à, ù, ì, ò, É and Ç are in it; ê, ç, ô, â, î, œ are not. */
	private static final String BASIC = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
			+ "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";

	/** Sent with an escape character: two septets each. */
	private static final String EXTENSION = "^{}\\[~]|€\f";

	private SmsText() {
	}

	static boolean isGsm7(String text) {
		return text.codePoints().allMatch(c -> BASIC.indexOf(c) >= 0 || EXTENSION.indexOf(c) >= 0);
	}

	static int units(String text) {
		if (text.isEmpty()) {
			return 0;
		}
		if (isGsm7(text)) {
			int septets = text.codePoints().map(c -> EXTENSION.indexOf(c) >= 0 ? 2 : 1).sum();
			return septets <= 160 ? 1 : (septets + 152) / 153;
		}
		int chars = text.length();
		return chars <= 70 ? 1 : (chars + 66) / 67;
	}

	/** The text without accents or ligatures, for an SMS in 160-character units. */
	static String plain(String text) {
		String replaced = text.replace("œ", "oe")
			.replace("Œ", "OE")
			.replace("æ", "ae")
			.replace("Æ", "AE")
			.replace('’', '\'')
			.replace('‘', '\'')
			.replace('«', '"')
			.replace('»', '"')
			.replace(' ', ' ')
			.replace(' ', ' ');
		return Normalizer.normalize(replaced, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
	}

}
