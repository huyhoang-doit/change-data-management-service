package com.cdms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding cho {@code vietful.*} trong application.yaml.
 *
 * <p>Thay vì inject từng property bằng {@code @Value("${vietful.base-url}")},
 * dùng {@code @ConfigurationProperties} để:
 * <ul>
 *   <li>Group tất cả config vào 1 chỗ</li>
 *   <li>IDE autocomplete khi gõ trong application.yaml</li>
 *   <li>Fail-fast khi startup nếu config sai kiểu dữ liệu</li>
 *   <li>Dễ inject vào test với {@code @TestPropertySource}</li>
 * </ul>
 *
 * <p>Được đăng ký tự động bởi Spring Boot qua {@code @SpringBootApplication}
 * (scan {@code @ConfigurationProperties} trong cùng package và sub-packages).
 */
@ConfigurationProperties(prefix = "vietful")
public record VietfulProperties(

        /** Base URL của Vietful service (hoặc emulator). */
        String baseUrl,

        /** Connect timeout tính bằng milliseconds. */
        int connectTimeoutMs,

        /** Read timeout tính bằng milliseconds. */
        int readTimeoutMs,

        /** Số lần retry tối đa khi gặp timeout. */
        int maxRetries
) {}
