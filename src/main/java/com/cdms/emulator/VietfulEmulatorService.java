package com.cdms.emulator;

import com.cdms.emulator.dto.VietfulProduct;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store giả lập Vietful Inventory Service.
 *
 * <p><strong>Đây là emulator, không phải production code.</strong>
 * Dữ liệu chỉ sống trong RAM — mất khi app restart.
 *
 * <p><strong>Cách hoạt động:</strong>
 * <ol>
 *   <li>Khi app start → {@link #init()} tạo sẵn 10 sản phẩm Faker.</li>
 *   <li>Scheduler (Phase 7) gọi {@link #getAllProducts()} mỗi 60s để poll.</li>
 *   <li>Mỗi lần poll, emulator random update 2–3 sản phẩm (tăng version, đổi quantity)
 *       → Scheduler nhận data mới → ChangeProcessingService xử lý → DB updated.</li>
 * </ol>
 *
 * <p><strong>Tại sao dùng ConcurrentHashMap?</strong>
 * Scheduler đọc dữ liệu (GET) trong khi HTTP PUT/POST có thể đang write.
 * ConcurrentHashMap thread-safe cho concurrent read/write — không cần synchronized.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VietfulEmulatorService {

    private final FakerProductFactory fakerFactory;

    /** In-memory store: productId → VietfulProduct */
    private final Map<String, VietfulProduct> store = new ConcurrentHashMap<>();

    /** Số sản phẩm khởi tạo khi app start */
    private static final int INITIAL_PRODUCT_COUNT = 10;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Khởi tạo 10 sản phẩm random khi app start.
     * {@code @PostConstruct} đảm bảo method này chạy sau khi Spring đã inject
     * tất cả dependencies (fakerFactory đã sẵn sàng).
     */
    @PostConstruct
    public void init() {
        for (int i = 0; i < INITIAL_PRODUCT_COUNT; i++) {
            VietfulProduct product = fakerFactory.createRandom();
            store.put(product.id(), product);
        }
        log.info("Vietful emulator initialized with {} products", store.size());
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Trả toàn bộ danh sách sản phẩm.
     * Được gọi bởi Scheduler mỗi polling cycle.
     *
     * <p>Trước khi trả, random update 2–3 sản phẩm để simulate real-world changes.
     * Điều này đảm bảo Scheduler luôn có data mới để xử lý.
     */
    public List<VietfulProduct> getAllProducts() {
        simulateRandomUpdates();
        return new ArrayList<>(store.values());
    }

    /**
     * Tìm sản phẩm theo ID.
     *
     * @param id Vietful product ID
     * @return Optional chứa product nếu tìm thấy
     */
    public Optional<VietfulProduct> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Tạo sản phẩm mới.
     * Nếu request không có ID → sinh ID mới.
     * Nếu ID đã tồn tại → trả 409 (handled ở controller).
     *
     * @param product product data từ request
     * @return sản phẩm vừa tạo (với ID được gán)
     */
    public VietfulProduct create(VietfulProduct product) {
        String id = (product.id() != null && !product.id().isBlank())
                ? product.id()
                : "VF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        VietfulProduct toSave = VietfulProduct.builder()
                .id(id)
                .sku(product.sku())
                .partnerSKU(product.partnerSKU())
                .productName(product.productName())
                .unitCode(product.unitCode())
                .unitName(product.unitName())
                .categories(product.categories())
                .quantity(product.quantity() != null ? product.quantity() : 0)
                .color(product.color())
                .size(product.size())
                .description(product.description())
                .avatarURL(product.avatarURL())
                .serialType(product.serialType())
                .assetType(product.assetType())
                .isPhysical(product.isPhysical())
                .productUnits(product.productUnits())
                .version(1L)   // version mới bắt đầu từ 1
                .build();

        store.put(id, toSave);
        log.info("Emulator: created product id={}, sku={}", id, toSave.sku());
        return toSave;
    }

    /**
     * Update sản phẩm theo ID.
     *
     * @param id      product ID cần update
     * @param product data mới
     * @return updated product nếu tồn tại, empty nếu không tìm thấy
     */
    public Optional<VietfulProduct> update(String id, VietfulProduct product) {
        if (!store.containsKey(id)) {
            return Optional.empty();
        }

        VietfulProduct existing = store.get(id);
        VietfulProduct updated = VietfulProduct.builder()
                .id(id)
                .sku(product.sku() != null ? product.sku() : existing.sku())
                .partnerSKU(product.partnerSKU() != null ? product.partnerSKU() : existing.partnerSKU())
                .productName(product.productName() != null ? product.productName() : existing.productName())
                .unitCode(product.unitCode() != null ? product.unitCode() : existing.unitCode())
                .unitName(product.unitName() != null ? product.unitName() : existing.unitName())
                .categories(product.categories() != null ? product.categories() : existing.categories())
                .quantity(product.quantity() != null ? product.quantity() : existing.quantity())
                .color(product.color() != null ? product.color() : existing.color())
                .size(product.size() != null ? product.size() : existing.size())
                .description(product.description() != null ? product.description() : existing.description())
                .avatarURL(product.avatarURL() != null ? product.avatarURL() : existing.avatarURL())
                .serialType(product.serialType() != null ? product.serialType() : existing.serialType())
                .assetType(product.assetType() != null ? product.assetType() : existing.assetType())
                .isPhysical(product.isPhysical() != null ? product.isPhysical() : existing.isPhysical())
                .productUnits(product.productUnits() != null ? product.productUnits() : existing.productUnits())
                .version(existing.version() + 1)   // ← tăng version mỗi lần update
                .build();

        store.put(id, updated);
        log.info("Emulator: updated product id={}, new version={}", id, updated.version());
        return Optional.of(updated);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Random update 2–3 sản phẩm để simulate Vietful cập nhật data liên tục.
     * Gọi mỗi lần {@link #getAllProducts()} được invoke (= mỗi polling cycle).
     */
    private void simulateRandomUpdates() {
        List<String> ids = new ArrayList<>(store.keySet());
        if (ids.isEmpty()) return;

        Collections.shuffle(ids);
        int updateCount = Math.min(3, ids.size());

        for (int i = 0; i < updateCount; i++) {
            String id = ids.get(i);
            VietfulProduct current = store.get(id);
            if (current != null) {
                store.put(id, fakerFactory.createUpdated(current));
            }
        }
        log.debug("Emulator: simulated {} product updates", updateCount);
    }
}
