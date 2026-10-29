package com.schoollink.auth.internal;

import com.schoollink.shared.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 and 403 raised by the security filters, before Spring MVC, written in the same error shape
 * and language as every other error (SPEC §9, NFR-12).
 */
@Component
class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final MessageSource messages;

	private final LocaleResolver localeResolver;

	private final JsonMapper json;

	SecurityErrorHandler(MessageSource messages, LocaleResolver localeResolver, JsonMapper json) {
		this.messages = messages;
		this.localeResolver = localeResolver;
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "auth.unauthorized");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		write(request, response, HttpServletResponse.SC_FORBIDDEN, "auth.forbidden");
	}

	private void write(HttpServletRequest request, HttpServletResponse response, int status, String code)
			throws IOException {
		Locale locale = localeResolver.resolveLocale(request);
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getWriter(),
				new ApiError(code, messages.getMessage("error." + code, null, locale), null));
	}

}
