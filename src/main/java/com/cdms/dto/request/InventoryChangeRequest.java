package com.cdms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Webhook request DTO — payload gửi từ CDC Callback Client.
 *
 * <p>Dùng Java Record để bất biến (immutable) theo mặc định.
 * Spring Validation sẽ reject request nếu các @NotBlank fields bị thiếu.
 *
 * <p>Vietful field mapping:
 * <pre>
 *   eventId   — do CDC tạo ra, unique per change
 *   productId — Vietful external product ID
 *   sku       — product SKU
 *   version   — monotonically increasing; dùng để detect old data
 * </pre>
 */
public record InventoryChangeRequest(

        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "productId is required")
        String productId,

        @NotBlank(message = "sku is required")
        String sku,

        String partnerSku,

        String name,

        String unitCode,

        String unitName,

        String categoryCode,

        String categoryName,

        @PositiveOrZero(message = "quantity must be >= 0")
        Integer quantity,

        BigDecimal price,

        String color,

        String size,

        String description,

        String avatarUrl,

        Integer serialType,

        String assetType,

        Boolean isPhysical,
        
        Long version
) {}
