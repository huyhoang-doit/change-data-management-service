package com.cdms.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cấu hình RestClient cho Vietful HTTP client.
 *
 * <p>Spring Boot 4 dùng {@link RestClient} (synchronous, fluent API)
 * thay cho {@code RestTemplate} (deprecated) hoặc {@code WebClient} (reactive).
 *
 * <ul>
 *   <li>Connect timeout: nếu không kết nối được trong 3s → fail fast</li>
 *   <li>Read timeout: nếu đợi response quá 10s → fail, retry</li>
 * </ul>
 * Không có timeout → thread bị block mãi mãi → hung threads → app không phản hồi.
 */
@Configuration
@EnableConfigurationProperties(VietfulProperties.class)
public class RestClientConfig {

    /**
     * RestClient bean dùng để gọi Vietful API (hoặc emulator).
     *
     * <p>Bean này được inject vào {@link com.cdms.client.VietfulInventoryClient}.
     *
     * @param props vietful.* config từ application.yaml
     */
    @Bean
    public RestClient vietfulRestClient(VietfulProperties props) {
        // SimpleClientHttpRequestFactory = Java built-in HTTP (không cần thêm dependency)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeoutMs());
        factory.setReadTimeout(props.readTimeoutMs());

        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
