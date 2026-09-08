package com.cdms.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a product record in CDMS, mapped from the Vietful Inventory API.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>UNIQUE(external_id, sku) enforced at DB level — prevents duplicate products.</li>
 *   <li>{@code version} field is used to detect and reject old/stale data from any source.</li>
 *   <li>{@code content_hash} (SHA-256) enables fast change detection without full field comparison.</li>
 *   <li>{@code source} tracks which ingestion mechanism last updated this record.</li>
 * </ul>
 */
@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_products_external_id_sku",
                        columnNames = {"external_id", "sku"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Vietful product identity ─────────────────────────────────────────────

    /** Vietful internal product ID — used as the primary lookup key. */
    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    /** Product SKU from Vietful API ({@code sku} field). */
    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    /** Vietful partner SKU ({@code partnerSKU} field). */
    @Column(name = "partner_sku", length = 100)
    private String partnerSku;

    // ── Basic product info ───────────────────────────────────────────────────

    /** Product display name ({@code productName} from Vietful). */
    @Column(name = "name", length = 500)
    private String name;

    /** Unit of measurement code ({@code unitCode} from Vietful). */
    @Column(name = "unit_code", length = 50)
    private String unitCode;

    /** Unit of measurement name ({@code unitName} from Vietful). */
    @Column(name = "unit_name", length = 100)
    private String unitName;

    // ── Category (first entry from categories[] array) ───────────────────────

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    // ── Inventory ────────────────────────────────────────────────────────────

    @Column(name = "quantity")
    @Builder.Default
    private Integer quantity = 0;

    /** Selling price mapped from {@code productUnits[0].sellingPrice}. */
    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    // ── Product attributes ───────────────────────────────────────────────────

    @Column(name = "color", length = 100)
    private String color;

    @Column(name = "size", length = 50)
    private String size;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    /**
     * Serial tracking type from Vietful ({@code serialType}).
     * Stored as a small integer — values are Vietful-defined.
     */
    @Column(name = "serial_type")
    private Short serialType;

    /**
     * Asset type from Vietful ({@code assetType}).
     * Known values: "Single", "Bundle".
     */
    @Column(name = "asset_type", length = 50)
    private String assetType;

    @Column(name = "is_physical")
    @Builder.Default
    private Boolean isPhysical = false;

    // ── CDMS tracking fields ─────────────────────────────────────────────────

    /**
     * Monotonically increasing version number.
     * Used to detect and reject old/stale data:
     * <pre>
     *   incoming.version > current.version → PROCESS
     *   incoming.version <= current.version → OLD_DATA
     * </pre>
     */
    @Column(name = "version")
    @Builder.Default
    private Long version = 0L;

    /**
     * SHA-256 hash of key content fields.
     * If the hash matches the stored value, the data is unchanged → skip.
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /**
     * Ingestion source that last wrote this product.
     * Values: {@code WEBHOOK}, {@code SCHEDULER}, {@code EXCEL}.
     */
    @Column(name = "source", length = 50)
    private String source;

    // ── Timestamps ───────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }
}
