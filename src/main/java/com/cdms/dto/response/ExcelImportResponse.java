package com.cdms.dto.response;

import java.util.List;

/**
 * Summary response cho Excel Import endpoint: {@code POST /api/v1/imports/products}.
 *
 * <p>Example response:
 * <pre>
 * {
 *   "total":     100,
 *   "processed":  80,
 *   "duplicate":  15,
 *   "old":         3,
 *   "failed":      2,
 *   "errors": [
 *     "Row 5: eventId is required",
 *     "Row 12: invalid quantity value"
 *   ]
 * }
 * </pre>
 *
 * <p>Partial failure không abort toàn bộ batch —
 * các row hợp lệ vẫn được xử lý, row lỗi được ghi vào {@code errors}.
 */
public record ExcelImportResponse(
        int total,
        int processed,
        int duplicate,
        int old,
        int failed,
        List<String> errors
) {

    /** Builder để accumulate kết quả từng row. */
    public static class Builder {
        private int total;
        private int processed;
        private int duplicate;
        private int old;
        private int failed;
        private final java.util.List<String> errors = new java.util.ArrayList<>();

        public Builder total(int total) {
            this.total = total;
            return this;
        }

        public Builder incrementProcessed() {
            this.processed++;
            return this;
        }

        public Builder incrementDuplicate() {
            this.duplicate++;
            return this;
        }

        public Builder incrementOld() {
            this.old++;
            return this;
        }

        public Builder incrementFailed() {
            this.failed++;
            return this;
        }

        public Builder addError(String error) {
            this.errors.add(error);
            this.failed++;
            return this;
        }

        public void record(ProcessingResult result) {
            switch (result) {
                case PROCESSED -> incrementProcessed();
                case DUPLICATE -> incrementDuplicate();
                case OLD_DATA  -> incrementOld();
                case FAILED    -> incrementFailed();
            }
        }

        public ExcelImportResponse build() {
            return new ExcelImportResponse(total, processed, duplicate, old, failed,
                    java.util.Collections.unmodifiableList(errors));
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
