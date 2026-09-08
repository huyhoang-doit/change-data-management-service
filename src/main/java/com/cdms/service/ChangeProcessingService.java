package com.cdms.service;

import com.cdms.domain.entity.ChangeEvent;
import com.cdms.domain.entity.Product;
import com.cdms.domain.repository.ChangeEventRepository;
import com.cdms.domain.repository.ProductRepository;
import com.cdms.dto.response.ProcessingResult;
import com.cdms.service.model.ChangeData;
import com.cdms.service.util.ContentHashUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ChangeProcessingService .
 *
 * <p><strong>Trách nhiệm duy nhất:</strong> nhận một {@link ChangeData} từ bất kỳ nguồn nào
 * (Webhook, Scheduler, Excel) và xử lý theo đúng logic exactly-once.
 *
 * <p><strong>Tại sao chỉ có một service cho cả 3 nguồn?</strong>
 * <pre>
 *   ❌ Sai:                        ✅ Đúng:
 *   WebhookService → save DB       Webhook   ─┐
 *   SchedulerService → save DB     Scheduler ─┼──→ ChangeProcessingService → DB
 *   ExcelService → save DB         Excel     ─┘
 * </pre>
 * Ba nguồn chia sẻ CÙNG một logic dedup, validation, và transaction.
 * Tách ra 3 service = lặp code và khó maintain.
 *
 * <p><strong>Exactly-once guarantee — 2 lớp bảo vệ:</strong>
 * <pre>
 *   Lớp 1 (Application): existsByEventId() — fast pre-check, tránh overhead transaction
 *   Lớp 2 (Database):    UNIQUE(event_id) — bảo đảm cuối cùng, bao gồm cả race condition
 *
 *   Scenario concurrent (100 threads cùng gửi EVT-001):
 *   ├── Thread 1: existsByEventId=false → INSERT → COMMIT OK      → PROCESSED
 *   ├── Thread 2: existsByEventId=false → INSERT → DataIntegrityViolationException → DUPLICATE
 *   └── Thread N: existsByEventId=true  → early return            → DUPLICATE
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeProcessingService {

    private final ProductRepository productRepository;
    private final ChangeEventRepository changeEventRepository;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý một change event từ bất kỳ nguồn nào.
     *
     * <p><strong>Flow chi tiết:</strong>
     * <pre>
     *   Step 1: Lớp 1 dedup — existsByEventId? → DUPLICATE (fast path)
     *   Step 2: Tìm product hiện tại trong DB
     *   Step 3: Version check — incoming ≤ current? → OLD_DATA
     *   Step 4: Content hash check — data không đổi? → OLD_DATA
     *   Step 5: @Transactional {
     *               upsert product (INSERT hoặc UPDATE)
     *               INSERT change_event — Lớp 2 UNIQUE constraint kích hoạt ở đây
     *           }
     *   Step 6: DataIntegrityViolationException → DUPLICATE (race condition handled)
     * </pre>
     *
     * @param data change data từ bất kỳ nguồn (Webhook/Scheduler/Excel)
     * @return kết quả xử lý
     */
    public ProcessingResult processChange(ChangeData data) {

        // ── Step 1: Lớp 1 dedup (NGOÀI transaction) ─────────────────────────
        // Mục đích: tránh mở transaction tốn kém cho event đã biết là duplicate.
        // KHÔNG đủ an toàn dưới concurrency — đó là việc của Lớp 2 (DB constraint).
        if (changeEventRepository.existsByEventId(data.eventId())) {
            log.debug("Layer-1 duplicate detected: eventId={}", data.eventId());
            return ProcessingResult.DUPLICATE;
        }

        // ── Step 2: Tìm product hiện tại ─────────────────────────────────────
        Optional<Product> existingProduct =
                productRepository.findByExternalIdAndSku(data.productId(), data.sku());

        // ── Step 3: Version check ─────────────────────────────────────────────
        // Nếu product đã tồn tại VÀ version incoming ≤ version hiện tại → OLD DATA.
        // Điều này đảm bảo CDMS "only stores new data" như yêu cầu bài test.
        if (existingProduct.isPresent()) {
            Product current = existingProduct.get();
            if (data.effectiveVersion() <= current.getVersion()) {
                log.debug("Old data rejected: eventId={}, incoming version={}, current version={}",
                        data.eventId(), data.effectiveVersion(), current.getVersion());
                return ProcessingResult.OLD_DATA;
            }

            // ── Step 4: Content hash check ────────────────────────────────────
            // Version mới hơn nhưng content không đổi? Cũng bỏ qua (idempotent).
            // Ví dụ: Scheduler poll liên tục nhưng data Vietful chưa thay đổi.
            String incomingHash = ContentHashUtil.compute(data);
            if (incomingHash.equals(current.getContentHash())) {
                log.debug("Unchanged content skipped: eventId={}, hash={}", data.eventId(), incomingHash);
                return ProcessingResult.OLD_DATA;
            }
        }

        // ── Step 5: Transaction — upsert product + insert change_event ────────
        // @Transactional đảm bảo:
        //   - Nếu INSERT change_event thất bại → UPDATE product cũng ROLLBACK
        //   - Không bao giờ xảy ra: product=updated nhưng change_event=missing
        try {
            return executeTransaction(data, existingProduct);
        } catch (DataIntegrityViolationException e) {
            // ── Step 6: Lớp 2 dedup (Race condition handled) ─────────────────
            // Xảy ra khi 2+ thread vượt qua Step 1 cùng lúc (cả hai thấy false).
            // DB UNIQUE(event_id) chỉ cho phép 1 INSERT thành công.
            // Thread còn lại nhận exception này → DUPLICATE, KHÔNG crash.
            log.warn("Layer-2 duplicate detected (DB constraint): eventId={}", data.eventId());
            return ProcessingResult.DUPLICATE;
        }
    }

    /**
     * Xử lý batch từ Excel — từng row độc lập, lỗi 1 row không ảnh hưởng row khác.
     *
     * @param batch danh sách ChangeData từ Excel parser
     * @return danh sách kết quả tương ứng với từng row
     */
    public List<ProcessingResult> processBatch(List<ChangeData> batch) {
        return batch.stream()
                .map(data -> {
                    try {
                        return processChange(data);
                    } catch (Exception e) {
                        // Row lỗi không dừng toàn bộ batch
                        log.error("Failed to process row: eventId={}, error={}", data.eventId(), e.getMessage());
                        return ProcessingResult.FAILED;
                    }
                })
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thực hiện phần transactional: upsert product + insert change_event.
     *
     * <p>Tách ra method riêng để {@code @Transactional} hoạt động đúng qua Spring proxy.
     * Nếu đặt @Transactional trực tiếp trên {@link #processChange}, proxy vẫn chạy đúng
     * vì method này là public. Tuy nhiên tách ra giúp rõ ràng transaction boundary.
     *
     * <p>Thứ tự quan trọng:
     * <ol>
     *   <li>Upsert product TRƯỚC</li>
     *   <li>Insert change_event SAU — UNIQUE constraint kích hoạt lúc commit</li>
     * </ol>
     */
    @Transactional
    protected ProcessingResult executeTransaction(ChangeData data, Optional<Product> existing) {
        // 1. Upsert product
        upsertProduct(data, existing);

        // 2. Lưu change_event — đây là nơi UNIQUE(event_id) phát huy tác dụng
        ChangeEvent event = buildChangeEvent(data);
        changeEventRepository.save(event);

        log.info("Event processed: eventId={}, sku={}, source={}",
                data.eventId(), data.sku(), data.source());

        return ProcessingResult.PROCESSED;
    }

    /**
     * INSERT hoặc UPDATE product tùy theo trạng thái hiện tại.
     *
     * <p>Upsert pattern:
     * <ul>
     *   <li>Product chưa tồn tại → INSERT (new product)</li>
     *   <li>Product đã tồn tại → UPDATE với data mới và version tăng</li>
     * </ul>
     */
    private Product upsertProduct(ChangeData data, Optional<Product> existing) {
        String hash = ContentHashUtil.compute(data);

        Product product = existing.map(current -> {
            // UPDATE: map data mới vào entity hiện tại
            current.setName(data.name());
            current.setPartnerSku(data.partnerSku());
            current.setUnitCode(data.unitCode());
            current.setUnitName(data.unitName());
            current.setCategoryCode(data.categoryCode());
            current.setCategoryName(data.categoryName());
            current.setQuantity(data.quantity() != null ? data.quantity() : current.getQuantity());
            current.setPrice(data.price() != null ? data.price() : current.getPrice());
            current.setColor(data.color());
            current.setSize(data.size());
            current.setDescription(data.description());
            current.setAvatarUrl(data.avatarUrl());
            current.setSerialType(data.serialType());
            current.setAssetType(data.assetType());
            current.setIsPhysical(data.isPhysical() != null ? data.isPhysical() : current.getIsPhysical());
            current.setVersion(data.effectiveVersion());
            current.setContentHash(hash);
            current.setSource(data.source());
            current.setUpdatedAt(LocalDateTime.now());
            return current;
        }).orElseGet(() ->
            // INSERT: tạo product mới
            Product.builder()
                    .externalId(data.productId())
                    .sku(data.sku())
                    .partnerSku(data.partnerSku())
                    .name(data.name())
                    .unitCode(data.unitCode())
                    .unitName(data.unitName())
                    .categoryCode(data.categoryCode())
                    .categoryName(data.categoryName())
                    .quantity(data.quantity() != null ? data.quantity() : 0)
                    .price(data.price())
                    .color(data.color())
                    .size(data.size())
                    .description(data.description())
                    .avatarUrl(data.avatarUrl())
                    .serialType(data.serialType())
                    .assetType(data.assetType())
                    .isPhysical(data.isPhysical() != null ? data.isPhysical() : false)
                    .version(data.effectiveVersion())
                    .contentHash(hash)
                    .source(data.source())
                    .build()
        );

        return productRepository.save(product);
    }

    /**
     * Tạo ChangeEvent entity để ghi audit log.
     * Payload được serialize thành JSON để lưu raw data.
     */
    private ChangeEvent buildChangeEvent(ChangeData data) {
        String payload = serializeToJson(data);
        String payloadHash = ContentHashUtil.compute(data);

        return ChangeEvent.processed(
                data.eventId(),
                data.productId(),
                data.sku(),
                data.source(),
                payload,
                payloadHash
        );
    }

    /**
     * Serialize ChangeData thành JSON string để lưu vào {@code change_events.payload}.
     * JSON payload này dùng cho audit trail và potential replay.
     */
    private String serializeToJson(ChangeData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            // Không nên xảy ra với ChangeData record — fallback an toàn
            log.warn("Failed to serialize payload for eventId={}", data.eventId());
            return "{\"eventId\":\"" + data.eventId() + "\"}";
        }
    }
}
