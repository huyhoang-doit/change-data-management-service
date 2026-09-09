package com.cdms.client;

import com.cdms.config.VietfulProperties;
import com.cdms.emulator.dto.VietfulProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

/**
 * HTTP client gọi Vietful Inventory API (hoặc emulator tại {@code /vietful/products}).
 *
 * <pre>
 *   Vietful API timeout → Log error → retry tối đa {maxRetries} lần → skip, đợi next schedule
 * </pre>
 *
 * <p><strong>Tại sao retry thủ công thay vì Spring Retry?</strong>
 * Retry đơn giản (2 lần) không cần thêm dependency {@code spring-retry}.
 * Giữ pom.xml gọn hơn. Nếu cần exponential backoff hoặc circuit breaker
 * thì mới cần Resilience4j.
 */
@Slf4j
@Component
public class VietfulInventoryClient {

    private final RestClient restClient;
    private final int maxRetries;

    public VietfulInventoryClient(
            @Qualifier("vietfulRestClient") RestClient restClient,
            VietfulProperties props) {
        this.restClient = restClient;
        this.maxRetries = props.maxRetries();
    }

    /**
     * Lấy toàn bộ danh sách sản phẩm từ Vietful.
     *
     * <p>Retry logic:
     * <pre>
     *   attempt 1 → timeout → wait 1s → attempt 2 → timeout → return []
     *   attempt 1 → success → return products
     * </pre>
     *
     * @return danh sách sản phẩm, hoặc list rỗng nếu tất cả retry thất bại
     */
    public List<VietfulProduct> fetchProducts() {
        int attempt = 0;

        while (attempt <= maxRetries) {
            try {
                List<VietfulProduct> products = restClient.get()
                        .uri("/products")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

                if (products == null) {
                    log.warn("Vietful returned null product list");
                    return Collections.emptyList();
                }

                log.debug("Vietful fetched {} products (attempt {})", products.size(), attempt + 1);
                return products;

            } catch (ResourceAccessException e) {
                // ResourceAccessException = timeout hoặc connection refused
                attempt++;
                if (attempt > maxRetries) {
                    log.error("Vietful fetch failed after {} attempts (timeout/unreachable): {}",
                            maxRetries + 1, e.getMessage());
                    return Collections.emptyList();  // skip, đợi next schedule
                }

                log.warn("Vietful timeout on attempt {}/{}, retrying in 1s...",
                        attempt, maxRetries + 1);
                sleepQuietly(1000);

            } catch (RestClientException e) {
                // Lỗi HTTP khác (4xx, 5xx) — không retry
                log.error("Vietful HTTP error (no retry): {}", e.getMessage());
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
