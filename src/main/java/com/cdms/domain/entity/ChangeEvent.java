package com.cdms.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents an ingested change event in CDMS.
 *
 * <p>This table is the foundation of the exactly-once processing guarantee:
 * <ul>
 *   <li>{@code event_id} has a DB-level UNIQUE constraint.</li>
 *   <li>On concurrent requests with the same {@code eventId}, only one INSERT succeeds.</li>
 *   <li>Others receive a {@code DataIntegrityViolationException} → mapped to {@code DUPLICATE}.</li>
 * </ul>
 *
 * <p>Exactly-once layers:
 * <pre>
 *   Layer 1 (fast): existsByEventId() check before transaction
 *   Layer 2 (safe): DB UNIQUE(event_id) constraint blocks race conditions
 * </pre>
 *
 * <p>The full {@code payload} is stored for audit traceability and potential replay.
 */
@Entity
@Table(
        name = "change_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_change_events_event_id",
                        columnNames = {"event_id"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Deduplication key ────────────────────────────────────────────────────

    /**
     * Unique event identifier — the core of exactly-once processing.
     *
     * <p>UNIQUE constraint at DB level ensures that even under concurrent load,
     * only one row is ever inserted per {@code eventId}.
     */
    @Column(name = "event_id", nullable = false, length = 200)
    private String eventId;

    // ── Linked product ───────────────────────────────────────────────────────

    /** Vietful external product ID this event refers to. */
    @Column(name = "product_id", length = 100)
    private String productId;

    /** Product SKU — stored for quick reference without joining products table. */
    @Column(name = "sku", length = 100)
    private String sku;

    // ── Ingestion metadata ───────────────────────────────────────────────────

    /**
     * Source that triggered this event.
     * Values: {@code WEBHOOK}, {@code SCHEDULER}, {@code EXCEL}.
     */
    @Column(name = "source", length = 50)
    private String source;

    // ── Processing outcome ───────────────────────────────────────────────────

    /**
     * Processing result status.
     * <ul>
     *   <li>{@code PROCESSED} — event was new and successfully applied.</li>
     *   <li>{@code DUPLICATE} — event_id already existed; ignored.</li>
     *   <li>{@code OLD_DATA} — incoming version <= stored version; ignored.</li>
     *   <li>{@code FAILED} — processing error; product state unchanged.</li>
     * </ul>
     */
    @Column(name = "status", length = 20)
    private String status;

    // ── Payload (for audit/replay) ───────────────────────────────────────────

    /** Raw JSON payload received from the ingestion source. Stored for audit/replay. */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /** SHA-256 hash of the payload — used to detect unchanged data. */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    // ── Timestamps ───────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Set when processing completes (success or failure). */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ── Convenience factory methods ──────────────────────────────────────────

    /** Build a PROCESSED event record. */
    public static ChangeEvent processed(String eventId, String productId, String sku,
                                        String source, String payload, String payloadHash) {
        return ChangeEvent.builder()
                .eventId(eventId)
                .productId(productId)
                .sku(sku)
                .source(source)
                .status(EventStatus.PROCESSED.name())
                .payload(payload)
                .payloadHash(payloadHash)
                .processedAt(LocalDateTime.now())
                .build();
    }


    /**
     * Status enum — used only as constants; stored as String in DB
     * to keep the column human-readable without an @Enumerated mapping.
     */
    public enum EventStatus {
        PROCESSED,
        DUPLICATE,
        OLD_DATA,
        FAILED
    }
}
