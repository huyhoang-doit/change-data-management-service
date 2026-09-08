package com.cdms.exception;

import com.cdms.dto.response.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import java.util.List;

/**
 * Global exception handler — bắt tất cả exception từ controller layer.
 *
 * <p><strong>Nguyên tắc:</strong>
 * <ul>
 *   <li>Controller KHÔNG try/catch — để exception tự nổi lên đây.</li>
 *   <li>Handler này chịu trách nhiệm map exception → HTTP status + JSON response chuẩn.</li>
 *   <li>KHÔNG bao giờ để exception leak ra ngoài dưới dạng HTML error page.</li>
 * </ul>
 *
 * <p><strong>Thứ tự xử lý (từ specific → generic):</strong>
 * <pre>
 *   MethodArgumentNotValidException  → 400 (validation)
 *   IllegalArgumentException         → 400 (bad input logic)
 *   MaxUploadSizeExceededException   → 413 (file quá lớn)
 *   DataIntegrityViolationException  → 409 (DB constraint — chỉ xảy ra ngoài service)
 *   Exception                        → 500 (unexpected)
 * </pre>
 *
 * <p><strong>Lưu ý về DataIntegrityViolationException:</strong>
 * {@link com.cdms.service.ChangeProcessingService} đã bắt exception này bên trong
 * và trả DUPLICATE (HTTP 200). Exception này chỉ đến đây nếu có DB constraint violation
 * từ nơi khác — ví dụ Excel import controller chưa được bao bởi service.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Validation failed — @Valid trong controller reject request.
     *
     * <p>Ví dụ: thiếu eventId, quantity âm.
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
     * Bad input — business logic validation (e.g. empty file, unsupported format).
     * Trả HTTP 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", ex.getMessage()));
    }

    /**
     * File upload vượt giới hạn 50MB (cấu hình trong application.yaml).
     * Trả HTTP 413 Payload Too Large.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartException(MultipartException ex) {
        // Bao gồm cả file size exceeded — kiểm tra message để phân biệt
        if (ex.getMessage() != null && ex.getMessage().contains("size")) {
            log.warn("Upload too large: {}", ex.getMessage());
            return ResponseEntity
                    .status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(ApiErrorResponse.of(413, "Payload Too Large",
                            "File size exceeds maximum allowed limit (50MB)"));
        }
        log.warn("Multipart error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(400, "Bad Request", "Invalid multipart request"));
    }

    /**
     * DB constraint violation xảy ra NGOÀI ChangeProcessingService.
     *
     * <p>Bình thường ChangeProcessingService đã bắt và xử lý exception này.
     * Handler này là safety net cho các trường hợp còn lại.
     * Trả HTTP 409 Conflict.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        Throwable cause = ex.getRootCause() != null ? ex.getRootCause() : ex;
        log.error("Data integrity violation: {}", cause.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(409, "Conflict",
                        "Data integrity constraint violated"));
    }

    /**
     * Catch-all — bắt mọi exception không được handle ở trên.
     *
     * <p><strong>Quan trọng:</strong> Log error đầy đủ (stack trace) để debug,
     * nhưng KHÔNG expose stack trace ra client response — security risk.
     * Trả HTTP 500 với message chung chung.
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
