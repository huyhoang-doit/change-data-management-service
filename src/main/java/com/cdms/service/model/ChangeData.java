package com.cdms.service.model;

import java.math.BigDecimal;

import com.cdms.dto.request.InventoryChangeRequest;

/**
 * Internal data transfer model — cầu nối chung giữa 3 nguồn ingestion và ChangeProcessingService.
 *
 * <p><strong>Tại sao cần class này?</strong>
 * Ba nguồn dữ liệu (Webhook, Scheduler, Excel) có format input khác nhau,
 * nhưng đều phải đi qua cùng một {@code ChangeProcessingService}.
 * {@code ChangeData} là ngôn ngữ chung (internal contract) giữa chúng:
 *
 * <pre>
 *   InventoryChangeRequest (Webhook)  ─┐
 *   VietfulProduct         (Scheduler)─┼──→ ChangeData ──→ ChangeProcessingService
 *   Excel Row              (Excel)    ─┘
 * </pre>
 *
 * <p>Dùng Java Record để immutable — không thể bị thay đổi sau khi tạo.
 */
public record ChangeData(

        /**
         * Unique event ID — key cho exactly-once deduplication.
         * - Webhook: lấy từ request.eventId()
         * - Scheduler: tạo dạng "POLL-{sku}-{version}" hoặc UUID
         * - Excel: lấy từ cột eventId trong file
         */
        String eventId,

        /** Vietful external product ID (external_id trong DB). */
        String productId,

        /** Product SKU. */
        String sku,

        String partnerSku,

        String name,

        String unitCode,

        String unitName,

        String categoryCode,

        String categoryName,

        Integer quantity,

        BigDecimal price,

        String color,

        String size,

        String description,

        String avatarUrl,

        Short serialType,

        String assetType,

        Boolean isPhysical,

        Long version,

        /**
         * Nguồn ingestion tạo ra ChangeData này.
         * Values: "WEBHOOK", "SCHEDULER", "EXCEL"
         */
        String source
) {

    /** Source constants — tránh hardcode string ở service layer. */
    public static final String SOURCE_WEBHOOK   = "WEBHOOK";
    public static final String SOURCE_SCHEDULER = "SCHEDULER";
    public static final String SOURCE_EXCEL     = "EXCEL";

    /**
     * Convert từ {@link InventoryChangeRequest} (Webhook source).
     */
    public static ChangeData fromWebhook(InventoryChangeRequest req) {
        return new ChangeData(
                req.eventId(),
                req.productId(),
                req.sku(),
                req.partnerSku(),
                req.name(),
                req.unitCode(),
                req.unitName(),
                req.categoryCode(),
                req.categoryName(),
                req.quantity(),
                req.price(),
                req.color(),
                req.size(),
                req.description(),
                req.avatarUrl(),
                req.serialType() != null ? req.serialType().shortValue() : null,
                req.assetType(),
                req.isPhysical(),
                req.version(),
                SOURCE_WEBHOOK
        );
    }

    /** Version hiệu quả: null → 0. */
    public long effectiveVersion() {
        return version != null ? version : 0L;
    }
}
