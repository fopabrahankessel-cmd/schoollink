package com.schoollink.shared.i18n;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

	private static Properties load(String name) throws IOException {
		Properties p = new Properties();
		try (var in = MessageBundleTests.class.getClassLoader().getResourceAsStream(name)) {
			assertThat(in).as(name).isNotNull();
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		return p;
	}

}
