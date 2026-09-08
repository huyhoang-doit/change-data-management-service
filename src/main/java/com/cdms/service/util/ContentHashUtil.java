package com.cdms.service.util;

import com.cdms.service.model.ChangeData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Tính SHA-256 hash từ các field "content" của một ChangeData.
 *
 * <p><strong>Tại sao cần content hash?</strong>
 * <p>Scheduler poll Vietful mỗi 60 giây. Nếu không có gì thay đổi, toàn bộ
 * danh sách product vẫn được trả về. Nếu CDMS xử lý tất cả mỗi lần poll
 * → tốn DB write không cần thiết.
 *
 * <p>Content hash giải quyết: nếu hash mới == hash cũ → data không đổi → bỏ qua.
 *
 * <p><strong>Các field đưa vào hash:</strong>
 * Chỉ hash các field "business content" — những thứ thực sự ảnh hưởng đến
 * trạng thái inventory. KHÔNG hash id, timestamps, source.
 *
 * <pre>
 *   name | sku | quantity | price | version | color | size
 * </pre>
 */
public final class ContentHashUtil {

    private ContentHashUtil() {}

    /**
     * Tính SHA-256 hash của các content fields trong {@link ChangeData}.
     *
     * @param data incoming change data
     * @return 64-char hex string (SHA-256)
     */
    public static String compute(ChangeData data) {
        String raw = buildRawString(data);
        return sha256(raw);
    }

    /**
     * Nối các field thành một string duy nhất để hash.
     * Dùng "|" làm separator để tránh collision (e.g. "AB" + "C" ≠ "A" + "BC").
     */
    private static String buildRawString(ChangeData data) {
        return String.join("|",
                nullSafe(data.sku()),
                nullSafe(data.name()),
                nullSafe(data.quantity()),
                nullSafe(data.price()),
                nullSafe(data.version()),
                nullSafe(data.color()),
                nullSafe(data.size()),
                nullSafe(data.categoryCode()),
                nullSafe(data.unitCode())
        );
    }

    private static String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn có trong Java — không bao giờ xảy ra
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
