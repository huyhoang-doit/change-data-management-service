package com.cdms.service;

import com.cdms.service.model.ChangeData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse file Excel (.xlsx) thành danh sách {@link ChangeData}.
 *
 * <p><strong>Cấu trúc file Excel kỳ vọng (row 1 = header):</strong>
 * <pre>
 * | eventId | productId | sku | name | quantity | price | version | color | size | description |
 * |---------|-----------|-----|------|----------|-------|---------|-------|------|-------------|
 * | EVT-001 | P-001     | ... | ...  | 100      | 99.9  | 1       | Red   | M    | ...         |
 * </pre>
 *
 * <p><strong>Nguyên tắc xử lý lỗi từng row:</strong>
 * <ul>
 *   <li>Row thiếu {@code eventId}, {@code productId}, hoặc {@code sku} → skip + ghi lỗi</li>
 *   <li>Row có giá trị số sai định dạng → gán null, tiếp tục</li>
 *   <li>KHÔNG throw exception → các row hợp lệ vẫn được xử lý</li>
 * </ul>
 */
@Slf4j
@Service
public class ExcelParserService {

    // Chỉ số cột (0-indexed) — phải khớp với header trong file Excel
    // Row 1 (header): eventId | productId | sku | partnerSku | name | unitCode | unitName
    //               | categoryCode | categoryName | quantity | price | color | size
    //               | description | avatarUrl | serialType | assetType | isPhysical | version
    private static final int COL_EVENT_ID       = 0;
    private static final int COL_PRODUCT_ID     = 1;
    private static final int COL_SKU            = 2;
    private static final int COL_PARTNER_SKU    = 3;
    private static final int COL_NAME           = 4;
    private static final int COL_UNIT_CODE      = 5;
    private static final int COL_UNIT_NAME      = 6;   // ← thêm mới
    private static final int COL_CATEGORY_CODE  = 7;
    private static final int COL_CATEGORY_NAME  = 8;   // ← thêm mới
    private static final int COL_QUANTITY        = 9;
    private static final int COL_PRICE           = 10;
    private static final int COL_COLOR           = 11;
    private static final int COL_SIZE            = 12;
    private static final int COL_DESCRIPTION     = 13;
    private static final int COL_AVATAR_URL      = 14;  // ← thêm mới
    private static final int COL_SERIAL_TYPE     = 15;  // ← thêm mới (0 hoặc 1)
    private static final int COL_ASSET_TYPE      = 16;  // ← thêm mới ("Single"/"Bundle")
    private static final int COL_IS_PHYSICAL     = 17;  // ← thêm mới (TRUE/FALSE)
    private static final int COL_VERSION         = 18;

    /**
     * Parse MultipartFile Excel thành danh sách ChangeData.
     *
     * @param file file .xlsx upload từ client
     * @return {@link ParseResult} chứa list data hợp lệ và list lỗi từng row
     * @throws IllegalArgumentException nếu file không phải .xlsx hoặc không đọc được
     */
    public ParseResult parse(MultipartFile file) {
        validateFile(file);

        List<ChangeData> items   = new ArrayList<>();
        List<String>    errors   = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null) {
                throw new IllegalArgumentException("Excel file has no sheets");
            }

            int totalRows = sheet.getLastRowNum(); // 0-indexed, không tính header
            log.info("Parsing Excel: {} data rows", totalRows);

            // Bắt đầu từ row 1 (row 0 là header)
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isEmptyRow(row)) continue;

                try {
                    ChangeData data = parseRow(row, rowIdx + 1); // +1 cho user-friendly row number
                    items.add(data);
                } catch (RowParseException e) {
                    String error = "Row " + (rowIdx + 1) + ": " + e.getMessage();
                    errors.add(error);
                    log.warn("Excel parse error — {}", error);
                }
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read Excel file: " + e.getMessage(), e);
        }

        log.info("Excel parsed: {} valid rows, {} errors", items.size(), errors.size());
        return new ParseResult(items, errors);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parse một row thành {@link ChangeData}.
     * Throw {@link RowParseException} nếu required fields bị thiếu.
     */
    private ChangeData parseRow(Row row, int displayRowNum) {
        String eventId   = getString(row, COL_EVENT_ID);
        String productId = getString(row, COL_PRODUCT_ID);
        String sku       = getString(row, COL_SKU);

        // Required field validation
        if (eventId == null || eventId.isBlank()) {
            throw new RowParseException("eventId is required");
        }
        if (productId == null || productId.isBlank()) {
            throw new RowParseException("productId is required");
        }
        if (sku == null || sku.isBlank()) {
            throw new RowParseException("sku is required");
        }

        // serialType: Excel lưu số (0 hoặc 1) → cast về Short
        Short serialType = null;
        Integer serialTypeRaw = getInteger(row, COL_SERIAL_TYPE);
        if (serialTypeRaw != null) serialType = serialTypeRaw.shortValue();

        // isPhysical: Excel lưu "TRUE"/"FALSE" hoặc 1/0
        Boolean isPhysical = getBoolean(row, COL_IS_PHYSICAL);

        return new ChangeData(
                eventId,
                productId,
                sku,
                getString(row, COL_PARTNER_SKU),
                getString(row, COL_NAME),
                getString(row, COL_UNIT_CODE),
                getString(row, COL_UNIT_NAME),      // unitName — cột riêng
                getString(row, COL_CATEGORY_CODE),  // categoryCode
                getString(row, COL_CATEGORY_NAME),  // categoryName
                getInteger(row, COL_QUANTITY),
                getBigDecimal(row, COL_PRICE),
                getString(row, COL_COLOR),
                getString(row, COL_SIZE),
                getString(row, COL_DESCRIPTION),
                getString(row, COL_AVATAR_URL),     // avatarUrl
                serialType,                         // serialType
                getString(row, COL_ASSET_TYPE),     // assetType ("Single"/"Bundle")
                isPhysical,                         // isPhysical
                getLong(row, COL_VERSION),
                ChangeData.SOURCE_EXCEL
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException(
                    "Only .xlsx files are supported. Got: " + filename);
        }
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    // ── Cell readers — null-safe ──────────────────────────────────────────────

    private String getString(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> null;
        };
    }

    private Integer getInteger(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (int) cell.getNumericCellValue();
                case STRING  -> Integer.parseInt(cell.getStringCellValue().trim());
                default      -> null;
            };
        } catch (NumberFormatException e) {
            return null; // sai định dạng → null, không crash
        }
    }

    private Long getLong(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> (long) cell.getNumericCellValue();
                case STRING  -> Long.parseLong(cell.getStringCellValue().trim());
                default      -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Đọc boolean từ cell — hỗ trợ nhiều format người dùng hay nhập:
     * - Excel BOOLEAN cell (checkbox): TRUE/FALSE
     * - String: "true"/"false"/"TRUE"/"FALSE"/"1"/"0"/"yes"/"no"
     * - Numeric: 1.0 = true, 0.0 = false
     */
    private Boolean getBoolean(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> cell.getNumericCellValue() != 0;
            case STRING  -> {
                String val = cell.getStringCellValue().trim().toLowerCase();
                yield switch (val) {
                    case "true", "1", "yes"  -> true;
                    case "false", "0", "no"  -> false;
                    default                  -> null; // không nhận dạng được → null
                };
            }
            default -> null;
        };
    }

    private BigDecimal getBigDecimal(Row row, int colIdx) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING  -> new BigDecimal(cell.getStringCellValue().trim());
                default      -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Kết quả parse — trả về cả data hợp lệ và errors để controller tổng hợp response.
     */
    public record ParseResult(List<ChangeData> items, List<String> errors) {}

    /** Exception nội bộ cho lỗi từng row — không phải system error. */
    private static class RowParseException extends RuntimeException {
        RowParseException(String message) { super(message); }
    }
}
