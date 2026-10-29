package com.schoollink.shared.i18n;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * French and English only (NFR-12). The request language comes from {@code Accept-Language};
 * anything else, or no header, falls back to French. Once authentication exists (task 1.6), the
 * user's stored language takes precedence.
 */
@Configuration(proxyBeanMethods = false)
class LocaleConfig {

	static final List<Locale> SUPPORTED = List.of(Locale.FRENCH, Locale.ENGLISH);

	@Bean
	LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(SUPPORTED);
		resolver.setDefaultLocale(Locale.FRENCH);
		return resolver;
	}

}
