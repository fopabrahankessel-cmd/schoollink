package com.schoollink.shared.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * French and English only (NFR-12). A signed-in user gets the language stored on their account
 * (the access token's {@code lang} claim); otherwise {@code Accept-Language} decides, and anything
 * else, or no header, falls back to French.
 */
@Configuration(proxyBeanMethods = false)
class LocaleConfig {

	static final List<Locale> SUPPORTED = List.of(Locale.FRENCH, Locale.ENGLISH);

	@Bean
	LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new UserLanguageLocaleResolver();
		resolver.setSupportedLocales(SUPPORTED);
		resolver.setDefaultLocale(Locale.FRENCH);
		return resolver;
	}

	static class UserLanguageLocaleResolver extends AcceptHeaderLocaleResolver {

		@Override
		public Locale resolveLocale(HttpServletRequest request) {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
				String lang = jwt.getClaimAsString("lang");
				if ("EN".equals(lang)) {
					return Locale.ENGLISH;
				}
				if ("FR".equals(lang)) {
					return Locale.FRENCH;
				}
			}
			return super.resolveLocale(request);
		}

	}

}
