package com.cdms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Cấu hình Swagger / OpenAPI Documentation.
 *
 * <p>Tự động xuất bản tài liệu API tương tác tại:
 * <ul>
 *   <li>Swagger UI: {@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>OpenAPI JSON Spec: {@code http://localhost:8080/v3/api-docs}</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cdmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Change Data Management Service (CDMS) API")
                        .description("""
                                Hệ thống tiếp nhận, đồng bộ và quản lý thay đổi dữ liệu sản phẩm/kho hàng (Change Data Capture - CDC)
                                hỗ trợ tiếp nhận real-time qua Webhook, batch import qua Excel, và scheduled polling từ Vietful Inventory API.
                                
                                ### Các tính năng chính:
                                * **Exactly-Once Processing**: Idempotent processing dựa trên Unique Constraint & Event Deduplication.
                                * **Real-time Webhook**: Nhận event tức thì từ CDC Callback Client.
                                * **Batch Excel Import**: Import danh sách sản phẩm dung lượng lớn (.xlsx) với cơ chế partial failure.
                                * **Scheduled Polling**: Tự động đồng bộ định kỳ từ Vietful Inventory Service.
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("CDMS Development Team")
                                .email("support@cdms.local"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Server")
                ));
    }
}
