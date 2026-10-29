package com.schoollink.shared.phone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** D15: Cameroon numbers only, normalized to E.164. */
class PhoneNumbersTests {

	@Test
	void normalizesTheWaysPeopleTypeACameroonNumber() {
		assertThat(PhoneNumbers.normalize("+237699000001")).hasValue("+237699000001");
		assertThat(PhoneNumbers.normalize("699 00 00 01")).hasValue("+237699000001");
		assertThat(PhoneNumbers.normalize("699-000-001")).hasValue("+237699000001");
		assertThat(PhoneNumbers.normalize("237699000001")).hasValue("+237699000001");
		assertThat(PhoneNumbers.normalize("00237 699 000 001")).hasValue("+237699000001");
		assertThat(PhoneNumbers.normalize(" (+237) 699.00.00.01 ")).hasValue("+237699000001");
	}

	@Test
	void rejectsForeignAndMalformedNumbers() {
		assertThat(PhoneNumbers.normalize("+33612345678")).isEmpty();
		assertThat(PhoneNumbers.normalize("0033612345678")).isEmpty();
		assertThat(PhoneNumbers.normalize("69900001")).isEmpty();
		assertThat(PhoneNumbers.normalize("+2376990000011")).isEmpty();
		assertThat(PhoneNumbers.normalize("abc")).isEmpty();
		assertThat(PhoneNumbers.normalize(null)).isEmpty();
	}

}
