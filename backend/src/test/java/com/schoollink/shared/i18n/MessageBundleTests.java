package com.schoollink.shared.i18n;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** NFR-12: every message exists in both French and English. */
class MessageBundleTests {

	@Test
	void frenchAndEnglishHaveTheSameKeys() throws IOException {
		Properties fr = load("messages_fr.properties");
		Properties en = load("messages_en.properties");

		assertThat(fr.stringPropertyNames()).isNotEmpty().containsExactlyInAnyOrderElementsOf(en.stringPropertyNames());
	}

	@Test
	void noMessageIsBlank() throws IOException {
		for (String bundle : new String[] { "messages_fr.properties", "messages_en.properties" }) {
			Properties p = load(bundle);
			assertThat(p.stringPropertyNames()).allSatisfy(key -> assertThat(p.getProperty(key)).as(bundle + " " + key).isNotBlank());
		}
	}

	/** A broken pattern (e.g. "#" inside a choice) only fails when the message is rendered. */
	@Test
	void everyMessageFormats() throws IOException {
		for (String bundle : new String[] { "messages_fr.properties", "messages_en.properties" }) {
			Properties p = load(bundle);
			Locale locale = bundle.contains("_fr") ? Locale.FRENCH : Locale.ENGLISH;
			assertThat(p.stringPropertyNames()).allSatisfy(key -> {
				for (Object[] args : new Object[][] { { 1, 1, 1, 1, 1 }, { 2, 30, 5, 2, 7 } }) {
					assertThat(new MessageFormat(p.getProperty(key), locale).format(args)).as(bundle + " " + key)
						.doesNotContain("{");
				}
			});
		}
	}

	private static Properties load(String name) throws IOException {
		Properties p = new Properties();
		try (var in = MessageBundleTests.class.getClassLoader().getResourceAsStream(name)) {
			assertThat(in).as(name).isNotNull();
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return p;
	}

}
