package com.cdms.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body cho Webhook endpoint: {@code POST /api/v1/webhooks/inventory}.
 *
 * <p>Examples:
 * <pre>
 *   { "eventId": "EVT-001", "status": "PROCESSED" }
 *   { "eventId": "EVT-001", "status": "DUPLICATE" }
 *   { "eventId": "EVT-001", "status": "OLD_DATA"  }
 * </pre>
 *
 * <p>Cả DUPLICATE và OLD_DATA đều trả HTTP 200 — đây là idempotent behavior đúng chuẩn.
 * Client không cần retry, không cần lo về side effects.
 */
@Schema(description = "Response kết quả xử lý Webhook event")
public record WebhookResponse(
        @Schema(description = "Event ID", example = "EVT-20260908-001")
        String eventId,

        @Schema(description = "Trạng thái xử lý: PROCESSED | DUPLICATE | OLD_DATA | FAILED", example = "PROCESSED")
        String status
) {

    public static WebhookResponse of(String eventId, ProcessingResult result) {
        return new WebhookResponse(eventId, result.name());
    }
}
