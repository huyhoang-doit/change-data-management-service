package com.cdms.controller;

import com.cdms.dto.request.InventoryChangeRequest;
import com.cdms.dto.response.ProcessingResult;
import com.cdms.dto.response.WebhookResponse;
import com.cdms.service.ChangeProcessingService;
import com.cdms.service.model.ChangeData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
