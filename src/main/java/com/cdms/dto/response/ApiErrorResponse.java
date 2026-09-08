package com.cdms.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chuẩn error response cho mọi API endpoint trong CDMS.
 *
 * <p>Examples:
 *
 * <p>Validation error (400):
 * <pre>
 * {
 *   "status": 400,
 *   "error": "Validation Failed",
 *   "message": "Request validation failed",
 *   "timestamp": "2026-09-08T12:00:00",
 *   "fieldErrors": [
 *     { "field": "eventId", "message": "eventId is required" }
 *   ]
 * }
 * </pre>
 *
 * <p>System error (500):
 * <pre>
 * {
 *   "status": 500,
 *   "error": "Internal Server Error",
 *   "message": "An unexpected error occurred",
 *   "timestamp": "2026-09-08T12:00:00"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {}

    /** Factory: generic error (4xx, 5xx). */
    public static ApiErrorResponse of(int status, String error, String message) {
        return new ApiErrorResponse(status, error, message, LocalDateTime.now(), null);
    }

    /** Factory: validation error với danh sách field lỗi. */
    public static ApiErrorResponse validation(List<FieldError> fieldErrors) {
        return new ApiErrorResponse(
                400,
                "Validation Failed",
                "Request validation failed",
                LocalDateTime.now(),
                fieldErrors
        );
    }
}
