package com.cdms.emulator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO map với Vietful Inventory Product API payload.
 *
 * <p>Structure dựa trên Vietful API contract được cung cấp trong đề bài:
 * <pre>
 * {
 *   "sku": "PHONE001",
 *   "partnerSKU": "PHONEINTERNAL001",
 *   "productName": "Iphone 12 new brand",
 *   "unitCode": "CAI",
 *   "categories": [{ "categoryCode": "CATEGORY001", "categoryName": "..." }],
 *   "color": "Red",
 *   "size": "XXL",
 *   ...
 * }
 * </pre>
 *
 * <p>Chỉ map các field mà CDMS thực sự cần — bỏ qua các field không liên quan
 * đến inventory tracking (productBundles, barcodes, v.v.).
 *
 * <p>Dùng {@code @JsonInclude(NON_NULL)} để response JSON gọn hơn,
 * không có các field null.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VietfulProduct(

        /** Vietful internal product ID — dùng làm external_id trong CDMS. */
        String id,

        /** Product SKU — phần của UNIQUE(external_id, sku) trong CDMS. */
        String sku,

        /** Partner (merchant) SKU. */
        String partnerSKU,

        /** Product display name. */
        String productName,

        /** Unit code (e.g. "CAI", "HOP", "KG"). */
        String unitCode,

        /** Unit name. */
        String unitName,

        /** Product categories — CDMS lấy categories[0]. */
        List<Category> categories,

        /** Current inventory quantity. */
        Integer quantity,

        /** Product color. */
        String color,

        /** Product size. */
        String size,

        /** Product description. */
        String description,

        /** Avatar/image URL. */
        String avatarURL,

        /** Serial tracking type (1 = Serial, 0 = None). */
        Integer serialType,

        /** Asset type: "Single" hoặc "Bundle". */
        String assetType,

        /** Is physical product? */
        Boolean isPhysical,

        /** Product units (pricing info). */
        List<ProductUnit> productUnits,

        /**
         * Version — CDMS dùng để detect old data.
         * Tăng mỗi lần Vietful update product.
         */
        Long version
) {

    /** Nested category record. */
    public record Category(
            String categoryCode,
            String categoryName
    ) {}

    /** Nested product unit record — chứa thông tin giá. */
    public record ProductUnit(
            String unitCode,
            String unitName,
            BigDecimal sellingPrice,
            BigDecimal costPrice,
            Boolean isBaseUnit
    ) {}
}
