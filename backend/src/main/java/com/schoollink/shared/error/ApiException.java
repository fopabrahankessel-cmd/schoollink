package com.schoollink.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Business error raised by any module. {@code code} is looked up as {@code error.<code>} in
 * {@code messages_fr/en.properties}, with {@code args} filling the message placeholders. Never
 * put a phone number in {@code args} (NFR-9).
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final String field;

	private final transient Object[] args;

	public ApiException(HttpStatus status, String code, String field, Object... args) {
		super(code);
		this.status = status;
		this.code = code;
		this.field = field;
		this.args = args;
	}

	public static ApiException notFound(String code, Object... args) {
		return new ApiException(HttpStatus.NOT_FOUND, code, null, args);
	}

	public static ApiException conflict(String code, Object... args) {
		return new ApiException(HttpStatus.CONFLICT, code, null, args);
	}

	public static ApiException invalid(String code, String field, Object... args) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, field, args);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

	public String field() {
		return field;
	}

	public Object[] args() {
		return args;
	}

}
