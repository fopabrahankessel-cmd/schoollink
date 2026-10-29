package com.schoollink.auth.internal;

import com.schoollink.shared.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * D18: a user with a temporary password may only read {@code /me} and replace the password; every
 * other request gets 403 {@code auth.password_change_required}. Added to the security chain only
 * (not a bean, so it is not also registered as a servlet filter).
 */
class PasswordChangeGate extends OncePerRequestFilter {

	private final SecurityErrorHandler errors;

	PasswordChangeGate(SecurityErrorHandler errors) {
		this.errors = errors;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof CurrentUser user && user.passwordChangeRequired()
				&& !allowed(request)) {
			errors.write(request, response, HttpServletResponse.SC_FORBIDDEN, "auth.password_change_required");
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean allowed(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return ("GET".equals(request.getMethod()) && "/api/v1/me".equals(path))
				|| ("POST".equals(request.getMethod()) && "/api/v1/me/password".equals(path));
	}

}
