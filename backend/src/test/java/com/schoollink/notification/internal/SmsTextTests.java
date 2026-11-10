package com.schoollink.notification.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D38: how many SMS a text takes, and the "replace special letters" option. */
class SmsTextTests {

	@Test
	void plainAsciiIsOneUnitUpTo160() {
		assertThat(SmsText.units("a".repeat(160))).isEqualTo(1);
		assertThat(SmsText.units("a".repeat(161))).isEqualTo(2);
		assertThat(SmsText.units("a".repeat(306))).isEqualTo(2);
		assertThat(SmsText.units("a".repeat(307))).isEqualTo(3);
	}

	@Test
	void extensionCharactersCountTwice() {
		assertThat(SmsText.units("€".repeat(80))).isEqualTo(1);
		assertThat(SmsText.units("€".repeat(81))).isEqualTo(2);
	}

	@Test
	void accentsSwitchTo70CharacterUnits() {
		// é, è, à, ù and É are in the GSM-7 alphabet; ê, ç, ô are not
		assertThat(SmsText.isGsm7("élève à l'école")).isTrue();
		assertThat(SmsText.isGsm7("fête")).isFalse();
		assertThat(SmsText.units("ê".repeat(70))).isEqualTo(1);
		assertThat(SmsText.units("ê".repeat(71))).isEqualTo(2);
		assertThat(SmsText.units("ê".repeat(134))).isEqualTo(2);
		assertThat(SmsText.units("ê".repeat(135))).isEqualTo(3);
	}

	@Test
	void plainRemovesAccentsAndLigatures() {
		String plain = SmsText.plain("Ça, élève, cœur, l’été « oui »");
		assertThat(plain).isEqualTo("Ca, eleve, coeur, l'ete \" oui \"");
		assertThat(SmsText.isGsm7(plain)).isTrue();
	}

	@Test
	void emptyTextTakesNoSms() {
		assertThat(SmsText.units("")).isZero();
	}

}
