package com.schoollink.shared.error;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an {@link ApiError} with a message in the request's language (SPEC §9,
 * NFR-12). Messages come from {@code error.<code>} keys; unexpected errors never leak details.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final MessageSource messages;

	ApiExceptionHandler(MessageSource messages) {
		this.messages = messages;
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException ex) {
		var response = ResponseEntity.status(ex.status());
		if (ex instanceof RateLimitedException limited) {
			response.header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.retryAfterSeconds()));
		}
		return response.body(error(ex.code(), ex.field(), ex.args()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("internal", null));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ApiError> details = ex.getBindingResult().getFieldErrors().stream().map(this::fieldError).toList();
		ApiError first = details.getFirst();
		return ResponseEntity.badRequest()
			.body(new ApiError(first.code(), first.message(), first.field(), details.size() > 1 ? details : List.of()));
	}

	/** Every other Spring MVC exception (malformed JSON, 404, 405, 415, ...). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		String code = switch (statusCode.value()) {
			case 400 -> "request.malformed";
			case 404 -> "not_found";
			case 405 -> "method_not_allowed";
			case 415 -> "media_type_unsupported";
			default -> statusCode.is5xxServerError() ? "internal" : "request.invalid";
		};
		return ResponseEntity.status(statusCode).headers(headers).body(error(code, null));
	}

	private ApiError fieldError(FieldError fe) {
		// NotBlank -> validation.not_blank; constraint attributes are passed as message arguments
		String code = "validation." + snakeCase(fe.getCode());
		Object[] args = fe.getArguments() == null ? new Object[0] : fe.getArguments();
		String message = messages.getMessage("error." + code, args, null, locale());
		if (message == null) {
			code = "validation.invalid";
			message = messages.getMessage("error." + code, null, locale());
		}
		return new ApiError(code, message, fe.getField());
	}

	private ApiError error(String code, String field, Object... args) {
		return new ApiError(code, messages.getMessage("error." + code, args, locale()), field);
	}

	private static Locale locale() {
		return LocaleContextHolder.getLocale();
	}

	private static String snakeCase(String constraint) {
		return constraint == null ? "invalid" : constraint.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
	}

}
