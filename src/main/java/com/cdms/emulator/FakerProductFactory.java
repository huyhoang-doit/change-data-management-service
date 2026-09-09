package com.cdms.emulator;

import com.cdms.emulator.dto.VietfulProduct;
import net.datafaker.Faker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Factory sinh dữ liệu product random bằng Java Faker (DataFaker).
 *
 * <p>Mục đích: tạo dữ liệu realistic cho Emulated Vietful Service,
 * giúp test Scheduler polling và processing pipeline với dữ liệu thực tế.
 *
 * <p><strong>Tại sao dùng DataFaker thay vì hardcode?</strong>
 * Hardcode 10 sản phẩm cố định → chỉ test được 10 case.
 * Faker → mỗi lần generate là tập data mới, test được nhiều edge case hơn.
 */
@Component
public class FakerProductFactory {

    private static final Faker faker = new Faker();

    private static final String[] UNIT_CODES  = {"CAI", "HOP", "KG", "GOI", "CHIEC"};
    private static final String[] ASSET_TYPES = {"Single", "Bundle"};
    private static final String[] CATEGORIES  = {"ELECTRONICS", "FASHION", "FOOD", "HEALTH", "HOME"};
    private static final String[] COLORS      = {"Red", "Blue", "Black", "White", "Green", "Yellow"};
    private static final String[] SIZES       = {"XS", "S", "M", "L", "XL", "XXL"};

    /**
     * Sinh một product với ID và version cố định.
     * Dùng khi cần tạo product với identity xác định (update scenario).
     *
     * @param id      Vietful product ID
     * @param sku     product SKU
     * @param version version hiện tại của product
     */
    public VietfulProduct createWithId(String id, String sku, long version) {
        String unitCode = faker.options().option(UNIT_CODES);
        String categoryCode = faker.options().option(CATEGORIES);

        return VietfulProduct.builder()
                .id(id)
                .sku(sku)
                .partnerSKU("INTERNAL-" + sku)
                .productName(faker.commerce().productName())
                .unitCode(unitCode)
                .unitName(unitCodeToName(unitCode))
                .categories(List.of(new VietfulProduct.Category(
                        categoryCode + "001",
                        "Category " + categoryCode
                )))
                .quantity(faker.number().numberBetween(0, 1000))
                .color(faker.options().option(COLORS))
                .size(faker.options().option(SIZES))
                .description(faker.lorem().sentence(10))
                .avatarURL("https://via.placeholder.com/150/" + faker.color().hex().replace("#", "") + "/fff")
                .serialType(faker.number().numberBetween(0, 2))
                .assetType(faker.options().option(ASSET_TYPES))
                .isPhysical(faker.bool().bool())
                .productUnits(List.of(buildBaseUnit(unitCode)))
                .version(version)
                .build();
    }

    /**
     * Sinh một product mới hoàn toàn random (ID và SKU tự sinh).
     */
    public VietfulProduct createRandom() {
        String id = "VF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sku = faker.commerce().material().toUpperCase().replaceAll("[^A-Z0-9]", "")
                + faker.number().numberBetween(100, 999);
        return createWithId(id, sku, 1L);
    }

    /**
     * Tạo phiên bản updated của product (tăng version, thay đổi quantity/price).
     * Dùng trong emulator để simulate Vietful update.
     */
    public VietfulProduct createUpdated(VietfulProduct existing) {
        return VietfulProduct.builder()
                .id(existing.id())
                .sku(existing.sku())
                .partnerSKU(existing.partnerSKU())
                .productName(existing.productName())
                .unitCode(existing.unitCode())
                .unitName(existing.unitName())
                .categories(existing.categories())
                .quantity(faker.number().numberBetween(0, 1000))   // quantity thay đổi
                .color(existing.color())
                .size(existing.size())
                .description(existing.description())
                .avatarURL(existing.avatarURL())
                .serialType(existing.serialType())
                .assetType(existing.assetType())
                .isPhysical(existing.isPhysical())
                .productUnits(List.of(buildBaseUnit(existing.unitCode()))) // price thay đổi
                .version(existing.version() + 1)                   // ← version tăng
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private VietfulProduct.ProductUnit buildBaseUnit(String unitCode) {
        return new VietfulProduct.ProductUnit(
                unitCode,
                unitCodeToName(unitCode),
                BigDecimal.valueOf(faker.number().randomDouble(2, 10000, 5000000))
                        .setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(faker.number().randomDouble(2, 5000, 3000000))
                        .setScale(2, RoundingMode.HALF_UP),
                true
        );
    }

    private String unitCodeToName(String code) {
        return switch (code) {
            case "CAI"   -> "Cái";
            case "HOP"   -> "Hộp";
            case "KG"    -> "Kilogram";
            case "GOI"   -> "Gói";
            case "CHIEC" -> "Chiếc";
            default      -> code;
        };
    }
}
