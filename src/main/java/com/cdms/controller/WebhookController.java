package com.cdms.controller;

import com.cdms.dto.request.InventoryChangeRequest;
import com.cdms.dto.response.ProcessingResult;
import com.cdms.dto.response.WebhookResponse;
import com.cdms.service.ChangeProcessingService;
import com.cdms.service.model.ChangeData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.cdms.dto.response.ApiErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook endpoint — nhận change event từ CDC Callback Client (Vietful).
 *
 * <p><strong>Contract:</strong>
 * <pre>
 *   POST /api/v1/webhooks/inventory
 *   Content-Type: application/json
 *
 *   Request:  { "eventId": "EVT-001", "productId": "P-123", "sku": "PHONE001", ... }
 *   Response: { "eventId": "EVT-001", "status": "PROCESSED" }  HTTP 200
 *             { "eventId": "EVT-001", "status": "DUPLICATE"  }  HTTP 200  ← idempotent
 *             { "eventId": "EVT-001", "status": "OLD_DATA"   }  HTTP 200  ← idempotent
 * </pre>
 *
 * <p><strong>Tại sao DUPLICATE và OLD_DATA vẫn trả HTTP 200?</strong>
 * <p>Đây là idempotency đúng chuẩn. CDC system thường retry khi không nhận được 200.
 * Nếu CDMS trả 4xx/5xx cho duplicate → CDC retry mãi → vòng lặp vô hạn.
 * Trả 200 + status=DUPLICATE báo cho client "tôi đã biết về event này, không cần retry".
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook API", description = "Endpoint tiếp nhận real-time inventory change event từ CDC Callback Client")
public class WebhookController {

    private final ChangeProcessingService changeProcessingService;

    /**
     * Nhận inventory change event từ CDC.
     *
     * <p>Spring Validation (@Valid) tự reject request nếu @NotBlank fields bị thiếu
     * → MethodArgumentNotValidException → GlobalExceptionHandler → 400 response.
     *
     * @param request validated request body
     * @return processing result với HTTP 200 cho mọi kết quả hợp lệ
     */
    @Operation(
            summary = "Nhận inventory change event real-time",
            description = "Xử lý event từ CDC. Trả về 200 OK với status PROCESSED, DUPLICATE hoặc OLD_DATA. Đảm bảo tính idempotent."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Xử lý thành công (hoặc trùng lặp / dữ liệu cũ)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WebhookResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dữ liệu request không hợp lệ",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Hệ thống tạm thời không khả dụng (DB down)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping("/inventory")
    public ResponseEntity<WebhookResponse> handleInventoryChange(
            @Valid @RequestBody InventoryChangeRequest request) {

        log.info("Webhook received: eventId={}, sku={}", request.eventId(), request.sku());

        // Convert request DTO → internal ChangeData (source = WEBHOOK)
        ChangeData changeData = ChangeData.fromWebhook(request);

        // Delegate toàn bộ logic cho service
        ProcessingResult result = changeProcessingService.processChange(changeData);

        log.info("Webhook processed: eventId={}, result={}", request.eventId(), result);

        // Luôn trả HTTP 200 — kể cả DUPLICATE và OLD_DATA
        // Lý do: idempotent behavior, tránh CDC retry loop
        return ResponseEntity.ok(WebhookResponse.of(request.eventId(), result));
    }
}
