package com.cdms.controller;

import com.cdms.dto.response.ExcelImportResponse;
import com.cdms.dto.response.ProcessingResult;
import com.cdms.service.ChangeProcessingService;
import com.cdms.service.ExcelParserService;
import com.cdms.service.ExcelParserService.ParseResult;
import com.cdms.service.model.ChangeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Excel Import endpoint — nhận file .xlsx, xử lý batch từng row.
 *
 * <p><strong>Contract:</strong>
 * <pre>
 *   POST /api/v1/imports/products
 *   Content-Type: multipart/form-data
 *   Body:         file=products.xlsx
 *
 *   Response HTTP 200:
 *   {
 *     "total": 100,
 *     "processed": 80,
 *     "duplicate": 15,
 *     "old": 3,
 *     "failed": 2,
 *     "errors": [
 *       "Row 5: eventId is required",
 *       "Row 12: sku is required"
 *     ]
 *   }
 * </pre>
 *
 * <p><strong>Partial failure — không abort toàn bộ batch:</strong>
 * Row 5 lỗi không làm hỏng row 6, 7, 8...
 * Response luôn trả HTTP 200 với summary đầy đủ kể cả khi có failed rows.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ExcelImportController {

    private final ExcelParserService excelParserService;
    private final ChangeProcessingService changeProcessingService;

    /**
     * Upload và xử lý file Excel chứa danh sách sản phẩm.
     *
     * @param file multipart file .xlsx
     * @return summary kết quả xử lý từng row
     */
    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExcelImportResponse> importProducts(
            @RequestParam("file") MultipartFile file) {

        log.info("Excel import started: file={}, size={}KB",
                file.getOriginalFilename(),
                file.getSize() / 1024);

        // Step 1: Parse Excel → ChangeData list + parse errors
        ParseResult parseResult = excelParserService.parse(file);
        List<ChangeData> items  = parseResult.items();
        List<String> parseErrors = parseResult.errors();

        // Step 2: Build response builder với total count
        // total = rows thành công parse + rows parse lỗi
        int total = items.size() + parseErrors.size();
        ExcelImportResponse.Builder builder = ExcelImportResponse.builder().total(total);

        // Ghi parse errors vào builder trước (failed do parse)
        parseErrors.forEach(builder::addError);

        // Step 3: Xử lý từng row đã parse thành công
        // processBatch() xử lý từng item độc lập — lỗi 1 item không stop batch
        List<ProcessingResult> results =
                changeProcessingService.processBatch(items);

        for (int i = 0; i < results.size(); i++) {
            ProcessingResult result = results.get(i);
            if (result == ProcessingResult.FAILED) {
                builder.addError("Row " + (i + 2) + ": processing failed for sku="
                        + items.get(i).sku());
            } else {
                builder.record(result);
            }
        }

        ExcelImportResponse response = builder.build();

        log.info("Excel import done: total={} processed={} duplicate={} old={} failed={}",
                response.total(), response.processed(), response.duplicate(),
                response.old(), response.failed());

        return ResponseEntity.ok(response);
    }
}
