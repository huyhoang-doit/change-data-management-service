package com.cdms.scheduler;

import com.cdms.client.VietfulInventoryClient;
import com.cdms.dto.response.ProcessingResult;
import com.cdms.emulator.dto.VietfulProduct;
import com.cdms.service.ChangeProcessingService;
import com.cdms.service.model.ChangeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Scheduler định kỳ poll Vietful Inventory API và xử lý thay đổi.
 *
 * <p><strong>Flow mỗi polling cycle:</strong>
 * <pre>
 *   1. Gọi VietfulInventoryClient.fetchProducts()
 *      └─ Timeout → retry 2x → return [] → skip cycle
 *   2. Với mỗi product trong danh sách:
 *      a. Convert VietfulProduct → ChangeData (source=SCHEDULER)
 *      b. Gọi ChangeProcessingService.processChange()
 *      c. PROCESSED: product updated in DB
 *         DUPLICATE: event đã xử lý rồi (skip)
 *         OLD_DATA:  version không mới hơn (skip)
 *   3. Log summary: processed/duplicate/old/failed
 * </pre>
 *
 * <p><strong>{@code @ConditionalOnProperty} — tắt Scheduler trong test:</strong>
 * Testcontainers test không muốn Scheduler chạy nền và gây side effect.
 * Đặt {@code scheduling.enabled=false} trong test config → Scheduler bean không tạo.
 *
 * <p><strong>Tại sao {@code fixedDelay} thay vì {@code fixedRate}?</strong>
 * <ul>
 *   <li>{@code fixedRate}: chạy mỗi N ms, bất kể lần trước xong chưa → overlap nếu chậm</li>
 *   <li>{@code fixedDelay}: đợi lần trước xong RỒII mới đếm N ms → an toàn hơn</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cdms.polling.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryPollingScheduler {

    private final VietfulInventoryClient vietfulClient;
    private final ChangeProcessingService processingService;

    /**
     * Polling task — chạy mỗi {@code cdms.polling.interval-ms} milliseconds.
     *
     * <p>Toàn bộ method được bao bởi try/catch:
     * bất kỳ exception nào cũng KHÔNG làm crash Scheduler.
     * Spring sẽ tiếp tục gọi method này ở lần tiếp theo.
     */
    @Scheduled(fixedDelayString = "${cdms.polling.interval-ms:60000}",
               initialDelayString = "${cdms.polling.interval-ms:60000}")
    public void pollInventory() {
        log.info("=== Inventory polling cycle started ===");
        long startMs = System.currentTimeMillis();

        try {
            // Step 1: Fetch từ Vietful (có retry bên trong client)
            List<VietfulProduct> products = vietfulClient.fetchProducts();

            if (products.isEmpty()) {
                log.info("Polling cycle skipped: no products fetched (Vietful unavailable?)");
                return;
            }

            log.info("Fetched {} products from Vietful, processing...", products.size());

            // Step 2: Xử lý từng product
            Map<ProcessingResult, Long> resultCounts = products.stream()
                    .map(this::toChangeData)
                    .map(processingService::processChange)
                    .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

            // Step 3: Log summary
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("=== Polling cycle done in {}ms | PROCESSED={} DUPLICATE={} OLD_DATA={} FAILED={} ===",
                    elapsed,
                    resultCounts.getOrDefault(ProcessingResult.PROCESSED, 0L),
                    resultCounts.getOrDefault(ProcessingResult.DUPLICATE, 0L),
                    resultCounts.getOrDefault(ProcessingResult.OLD_DATA, 0L),
                    resultCounts.getOrDefault(ProcessingResult.FAILED, 0L));

        } catch (Exception e) {
            // Safety net: KHÔNG để exception crash Scheduler thread
            // Nếu Scheduler thread die → @Scheduled không bao giờ chạy nữa
            log.error("Polling cycle failed unexpectedly: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert {@link VietfulProduct} → {@link ChangeData} cho ChangeProcessingService.
     *
     * <p>Event ID cho Scheduler được tạo theo pattern:
     * {@code POLL-{sku}-{version}} — đảm bảo:
     * <ul>
     *   <li>Cùng SKU nhưng version khác → eventId khác → được xử lý</li>
     *   <li>Cùng SKU, cùng version → eventId giống → DUPLICATE (không xử lý 2 lần)</li>
     * </ul>
     */
    private ChangeData toChangeData(VietfulProduct product) {
        // Event ID: POLL-{sku}-{version} — deterministic, idempotent
        String eventId = "POLL-" + product.sku() + "-v" + product.version();

        // Lấy category đầu tiên (categories[0])
        String categoryCode = null;
        String categoryName = null;
        if (product.categories() != null && !product.categories().isEmpty()) {
            categoryCode = product.categories().get(0).categoryCode();
            categoryName = product.categories().get(0).categoryName();
        }

        // Lấy sellingPrice từ base unit (productUnits[0])
        BigDecimal price = null;
        if (product.productUnits() != null && !product.productUnits().isEmpty()) {
            price = product.productUnits().get(0).sellingPrice();
        }

        return new ChangeData(
                eventId,
                product.id(),                                           // productId = Vietful ID
                product.sku(),
                product.partnerSKU(),
                product.productName(),
                product.unitCode(),
                product.unitName(),
                categoryCode,
                categoryName,
                product.quantity(),
                price,
                product.color(),
                product.size(),
                product.description(),
                product.avatarURL(),
                product.serialType() != null ? product.serialType().shortValue() : null,
                product.assetType(),
                product.isPhysical(),
                product.version(),
                ChangeData.SOURCE_SCHEDULER                             // source = SCHEDULER
        );
    }
}
