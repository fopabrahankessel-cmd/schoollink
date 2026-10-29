package com.schoollink.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The single JSON error shape of the API (SPEC §9): a stable machine-readable {@code code}, a
 * {@code message} in the user's language (NFR-12) and the offending {@code field}, if any. When a
 * request has several invalid fields, each one is listed in {@code details} with the same shape.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, String field, List<ApiError> details) {

	public ApiError(String code, String message, String field) {
		this(code, message, field, List.of());
	}

}
