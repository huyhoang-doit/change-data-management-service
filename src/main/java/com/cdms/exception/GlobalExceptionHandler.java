package com.cdms.exception;

import com.cdms.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Global exception handler — bắt tất cả exception từ controller layer.
 *
 * <p><strong>Nguyên tắc:</strong>
 * <ul>
 *   <li>Controller KHÔNG try/catch — để exception tự nổi lên đây.</li>
 *   <li>Handler này map exception → HTTP status + JSON response chuẩn.</li>
 *   <li>KHÔNG để exception leak ra ngoài dưới dạng HTML error page.</li>
 *   <li>Log đầy đủ server-side, KHÔNG expose stack trace ra client.</li>
 * </ul>
 *
 * <p><strong>Thứ tự xử lý (specific → generic):</strong>
 * <pre>
 *   CannotGetJdbcConnectionException  → 503 Service Unavailable (DB down)
 *   MethodArgumentNotValidException   → 400 Validation Failed
 *   HttpMessageNotReadableException   → 400 Malformed JSON
 *   IllegalArgumentException          → 400 Bad Request
 *   MultipartException                → 413 / 400
 *   NoResourceFoundException          → 404 Not Found
 *   DataIntegrityViolationException   → 409 Conflict (safety net)
 *   Exception                         → 500 Internal Server Error
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 503 — Database unavailable ────────────────────────────────────────────

    /**
     * DB không kết nối được — Postgres down hoặc connection pool exhausted.
     *
     * <p>Trả <strong>503 Service Unavailable</strong>, KHÔNG phải 500:
     * <ul>
     *   <li>503 = service tạm thời không khả dụng → client nên retry sau</li>
     *   <li>500 = lỗi code → client không nên retry</li>
     * </ul>
     * Load balancer có thể dùng 503 để failover sang instance khác.
     */
    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ResponseEntity<ApiErrorResponse> handleDbUnavailable(CannotGetJdbcConnectionException ex) {
        log.error("Database connection unavailable: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of(503, "Service Unavailable",
                        "Database is temporarily unavailable. Please try again later."));
    }

    // ── 400 — Client errors ───────────────────────────────────────────────────

    /**
     * @Valid trong controller reject request — thiếu field bắt buộc, sai format.
     * Trả HTTP 400 với danh sách field lỗi chi tiết.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        List<ApiErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ApiErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage()
                ))
                .toList();

        log.warn("Validation failed: {} field errors", fieldErrors.size());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(fieldErrors));
    }

    /**
     * Request body không parse được — JSON sai cú pháp hoặc sai kiểu dữ liệu.
     *
     * <p>Ví dụ: {@code "quantity": "abc"} (string thay vì number),
     * hoặc JSON thiếu dấu ngoặc {@code { }}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request",
                        "Request body is malformed or contains invalid data types."));
    }

    /**
     * Business logic validation — file rỗng, không phải .xlsx, v.v.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }

    // ── 404 — Endpoint không tồn tại ─────────────────────────────────────────

    /**
     * Client gọi endpoint không tồn tại (Spring Boot 4 dùng NoResourceFoundException).
     * Trả HTTP 404 Not Found.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(404, "Not Found",
                        "The requested endpoint does not exist."));
    }

    // ── 409 — DB constraint (safety net) ─────────────────────────────────────

    /**
     * File upload vượt giới hạn 50MB hoặc multipart request lỗi.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(MultipartException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("size")) {
            log.warn("Upload too large: {}", ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiErrorResponse.of(413, "Payload Too Large",
                            "File size exceeds maximum allowed limit (50MB)."));
        }
        log.warn("Multipart error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", "Invalid multipart request."));
    }

    // ── 409 — DB constraint (safety net) ─────────────────────────────────────

    /**
     * DB UNIQUE constraint violation xảy ra ngoài ChangeProcessingService.
     * ChangeProcessingService đã bắt exception này và trả DUPLICATE (HTTP 200).
     * Handler này là safety net cho các trường hợp còn lại.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        Throwable cause = ex.getRootCause() != null ? ex.getRootCause() : ex;
        log.error("Data integrity violation: {}", cause.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Conflict",
                        "Data integrity constraint violated."));
    }

    // ── 500 — Catch-all ───────────────────────────────────────────────────────

    /**
     * Catch-all — bắt mọi exception không được handle ở trên.
     * Log đầy đủ (kể cả stack trace) server-side.
     * KHÔNG expose stack trace ra client response — security risk.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred. Please try again later."));
    }
}
