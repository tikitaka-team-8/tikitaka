package com.tikitaka.paymentnotification.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentNotificationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TIKITAKA Payment & Notification API")
                        .description("결제와 알림 API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }

    @Bean
    public GroupedOpenApi paymentNotificationPublicApi() {
        return GroupedOpenApi.builder()
                .group("payment-notification")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude("/api/v1/internal/**")
                .build();
    }
}
