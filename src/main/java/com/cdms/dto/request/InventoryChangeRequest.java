package com.cdms.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Payload thay đổi tồn kho gửi từ CDC Callback Client")
public record InventoryChangeRequest(

        @Schema(description = "Unique Event ID (định danh sự kiện CDC)", example = "EVT-20260908-001", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "eventId is required")
        String eventId,

        @Schema(description = "ID sản phẩm trong hệ thống Vietful", example = "PROD-10023", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "productId is required")
        String productId,

        @Schema(description = "Mã SKU sản phẩm", example = "IPHONE-15-PRO-256GB", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "sku is required")
        String sku,

        @Schema(description = "Mã SKU đối tác", example = "PARTNER-SKU-99")
        String partnerSku,

        @Schema(description = "Tên sản phẩm", example = "iPhone 15 Pro Max 256GB")
        String name,

        @Schema(description = "Mã đơn vị tính", example = "Cái")
        String unitCode,

        @Schema(description = "Tên đơn vị tính", example = "Cái")
        String unitName,

        @Schema(description = "Mã danh mục", example = "PHONE")
        String categoryCode,

        @Schema(description = "Tên danh mục", example = "Điện thoại thông minh")
        String categoryName,

        @Schema(description = "Số lượng tồn kho", example = "150")
        @PositiveOrZero(message = "quantity must be >= 0")
        Integer quantity,

        @Schema(description = "Giá bán", example = "29990000.00")
        BigDecimal price,

        @Schema(description = "Màu sắc", example = "Titanium Natural")
        String color,

        @Schema(description = "Kích thước / dung lượng", example = "256GB")
        String size,

        @Schema(description = "Mô tả sản phẩm", example = "Hàng chính hãng VN/A")
        String description,

        @Schema(description = "URL ảnh đại diện", example = "https://cdn.store.com/images/iphone15.jpg")
        String avatarUrl,

        @Schema(description = "Loại quản lý Serial (0: Không, 1: Có)", example = "1")
        Integer serialType,

        @Schema(description = "Loại tài sản", example = "COMMODITY")
        String assetType,

        @Schema(description = "Có phải hàng vật lý không", example = "true")
        Boolean isPhysical,

        @Schema(description = "Version bản ghi (monotonically increasing)", example = "1")
        Long version
) {}
