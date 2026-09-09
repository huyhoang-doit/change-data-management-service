package com.cdms.service;

import com.cdms.domain.repository.ChangeEventRepository;
import com.cdms.domain.repository.ProductRepository;
import com.cdms.dto.response.ProcessingResult;
import com.cdms.service.model.ChangeData;
import com.cdms.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency Test — kiểm chứng exactly-once guarantee dưới tải đồng thời.
 *
 * <p><strong>Mục tiêu của test này:</strong>
 * <pre>
 *   100 thread cùng gửi EVENT-001 đồng thời
 *   → Chỉ đúng 1 thread được PROCESSED
 *   → 99 thread còn lại nhận DUPLICATE
 *   → DB có đúng 1 record change_event với event_id=EVENT-001
 *   → DB có đúng 1 record product (không bị duplicate)
 * </pre>
 *
 * <p><strong>Cơ chế đảm bảo:</strong>
 * <ul>
 *   <li>Layer 1: {@code existsByEventId()} — pre-check (application level)</li>
 *   <li>Layer 2: {@code UNIQUE(event_id)} DB constraint — atomic guarantee</li>
 *   <li>Catch {@code DataIntegrityViolationException} → DUPLICATE</li>
 * </ul>
 *
 * <p><strong>Tại sao dùng Testcontainers thay vì H2?</strong>
 * H2 in-memory không enforce UNIQUE constraint giống PostgreSQL.
 * Race condition chỉ xuất hiện với real DB — Testcontainers spin up
 * PostgreSQL thật để test chính xác.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrencyTest {

    @Autowired
    private ChangeProcessingService changeProcessingService;

    @Autowired
    private ChangeEventRepository changeEventRepository;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void cleanUp() {
        // Xóa data trước mỗi test để tránh interference
        changeEventRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("100 threads cùng gửi EVT-001 → chỉ 1 PROCESSED, 99 DUPLICATE")
    void givenSameEvent_when100ThreadsConcurrent_thenExactlyOneProcessed()
            throws InterruptedException {

        // ── Arrange ───────────────────────────────────────────────────────────
        int threadCount = 100;
        String sharedEventId = "CONCURRENT-TEST-" + UUID.randomUUID();
        ChangeData sharedData = buildChangeData(sharedEventId, "SKU-CONCURRENT-001");

        CountDownLatch startLatch = new CountDownLatch(1);  // chặn tất cả threads đợi
        CountDownLatch doneLatch  = new CountDownLatch(threadCount); // đợi tất cả xong

        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        AtomicInteger failedCount    = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // ── Act ───────────────────────────────────────────────────────────────
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // tất cả thread đợi signal để bắt đầu đồng thời
                    ProcessingResult result = changeProcessingService.processChange(sharedData);
                    switch (result) {
                        case PROCESSED -> processedCount.incrementAndGet();
                        case DUPLICATE -> duplicateCount.incrementAndGet();
                        default        -> failedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // BẮT ĐẦU: tất cả 100 threads chạy cùng lúc
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(finished).isTrue()
                .as("All threads should complete within 30 seconds");

        // Quan trọng nhất: đúng 1 lần PROCESSED
        assertThat(processedCount.get())
                .isEqualTo(1)
                .as("Exactly 1 event should be PROCESSED (exactly-once guarantee)");

        // 99 còn lại phải là DUPLICATE
        assertThat(duplicateCount.get())
                .isEqualTo(threadCount - 1)
                .as("All other events should be DUPLICATE");

        // Không có lỗi
        assertThat(failedCount.get())
                .isZero()
                .as("No thread should fail with an exception");

        // DB phải có đúng 1 record
        long eventCount = changeEventRepository.countByEventId(sharedEventId);
        assertThat(eventCount)
                .isEqualTo(1L)
                .as("DB must have exactly 1 change_event record (UNIQUE constraint enforced)");

        long productCount = productRepository.countByExternalIdAndSku(
                sharedData.productId(), sharedData.sku());
        assertThat(productCount)
                .isEqualTo(1L)
                .as("DB must have exactly 1 product record (no duplicate insert)");
    }

    @Test
    @DisplayName("100 threads với 100 eventId khác nhau → tất cả PROCESSED")
    void givenDifferentEvents_when100ThreadsConcurrent_thenAllProcessed()
            throws InterruptedException {

        // ── Arrange ───────────────────────────────────────────────────────────
        int threadCount = 100;
        String batchId  = UUID.randomUUID().toString().substring(0, 8);

        // Mỗi thread có eventId và sku riêng → không conflict
        List<ChangeData> distinctEvents = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            distinctEvents.add(buildChangeData(
                    "EVT-DISTINCT-" + batchId + "-" + i,
                    "SKU-DISTINCT-" + batchId + "-" + i
            ));
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger failedCount    = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // ── Act ───────────────────────────────────────────────────────────────
        for (int i = 0; i < threadCount; i++) {
            final ChangeData data = distinctEvents.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ProcessingResult result = changeProcessingService.processChange(data);
                    if (result == ProcessingResult.PROCESSED) processedCount.incrementAndGet();
                    else failedCount.incrementAndGet();
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // ── Assert ────────────────────────────────────────────────────────────
        assertThat(finished).isTrue();
        assertThat(processedCount.get())
                .isEqualTo(threadCount)
                .as("All 100 distinct events should be PROCESSED");
        assertThat(failedCount.get()).isZero();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ChangeData buildChangeData(String eventId, String sku) {
        return new ChangeData(
                eventId,
                "PRODUCT-" + sku,
                sku,
                "PARTNER-" + sku,
                "Test Product " + sku,
                "CAI",
                "Cái",
                "ELECTRONICS",
                "Electronics",
                100,
                new BigDecimal("999000"),
                "Black",
                "M",
                "Test description",
                null,
                (short) 0,
                "Single",
                true,
                1L,
                ChangeData.SOURCE_WEBHOOK
        );
    }
}
