package com.cdms.emulator;

import com.cdms.emulator.dto.VietfulProduct;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Emulated Vietful Inventory API.
 *
 * <p>Giả lập các endpoint của Vietful Inventory Service thực tế.
 * Scheduler và client code sẽ gọi các endpoint này.
 *
 * <p><strong>Endpoints:</strong>
 * <pre>
 *   GET  /vietful/products       → danh sách tất cả sản phẩm (có random updates)
 *   GET  /vietful/products/{id}  → lấy sản phẩm theo ID
 *   POST /vietful/products       → tạo sản phẩm mới
 *   PUT  /vietful/products/{id}  → update sản phẩm (tăng version)
 * </pre>
 *
 * <p><strong>Tại sao prefix là {@code /vietful} không phải {@code /api/v1}?</strong>
 * Để phân biệt rõ: {@code /api/v1/**} là CDMS API thật,
 * {@code /vietful/**} là emulator giả lập external service.
 */
@Slf4j
@RestController
@RequestMapping("/vietful/products")
@RequiredArgsConstructor
@Tag(name = "Vietful Emulator API", description = "Mock API giả lập Vietful Inventory External Service")
public class VietfulEmulatorController {

    private final VietfulEmulatorService emulatorService;

    /**
     * GET /vietful/products
     * Trả danh sách tất cả sản phẩm. Mỗi lần gọi có thể có 2–3 sản phẩm
     * được tự động update (simulate real Vietful).
     */
    @Operation(summary = "Lấy danh sách tất cả sản phẩm Vietful (mô phỏng)")
    @GetMapping
    public ResponseEntity<List<VietfulProduct>> getAllProducts() {
        List<VietfulProduct> products = emulatorService.getAllProducts();
        log.debug("Vietful emulator: returning {} products", products.size());
        return ResponseEntity.ok(products);
    }

    /**
     * GET /vietful/products/{id}
     * Trả sản phẩm theo ID. 404 nếu không tìm thấy.
     */
    @Operation(summary = "Lấy chi tiết sản phẩm Vietful theo ID")
    @GetMapping("/{id}")
    public ResponseEntity<VietfulProduct> getProductById(
            @Parameter(description = "Vietful Product ID", required = true) @PathVariable String id) {
        return emulatorService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /vietful/products
     * Tạo sản phẩm mới. ID được sinh tự động nếu không cung cấp.
     * Trả HTTP 201 Created với body là sản phẩm vừa tạo (bao gồm ID được gán).
     */
    @Operation(summary = "Tạo sản phẩm Vietful mới")
    @PostMapping
    public ResponseEntity<VietfulProduct> createProduct(@RequestBody VietfulProduct product) {
        VietfulProduct created = emulatorService.create(product);
        log.info("Vietful emulator: created product sku={}", created.sku());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT /vietful/products/{id}
     * Update sản phẩm. Version tự động tăng.
     * 404 nếu ID không tồn tại.
     */
    @Operation(summary = "Cập nhật sản phẩm Vietful (tăng version)")
    @PutMapping("/{id}")
    public ResponseEntity<VietfulProduct> updateProduct(
            @Parameter(description = "Vietful Product ID", required = true) @PathVariable String id,
            @RequestBody VietfulProduct product) {

        return emulatorService.update(id, product)
                .map(updated -> {
                    log.info("Vietful emulator: updated product id={}, version={}", id, updated.version());
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
