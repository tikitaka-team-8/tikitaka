package com.tikitaka.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class GatewaySwaggerConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void 기본_프로파일에서는_Swagger를_비활성화한다() throws IOException {
        PropertySource<?> properties = load("application.yaml");

        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
    }

    @Test
    void local과_docker_프로파일에서만_통합_Swagger를_활성화한다() throws IOException {
        for (String resource : List.of("application-local.yaml", "application-docker.yaml")) {
            PropertySource<?> properties = load(resource);

            assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo(true);
            assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(true);
            assertThat(properties.getProperty("springdoc.swagger-ui.urls[0].url")).isEqualTo("/openapi/platform");
            assertThat(properties.getProperty("springdoc.swagger-ui.urls[1].url")).isEqualTo("/openapi/ticketing");
            assertThat(properties.getProperty("springdoc.swagger-ui.urls[2].url"))
                    .isEqualTo("/openapi/payment-notification");
        }
    }

    @Test
    void staging_프로파일은_Swagger를_활성화하지_않는다() throws IOException {
        PropertySource<?> properties = load("application-staging.yaml");

        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isNull();
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isNull();
    }

    private PropertySource<?> load(String resource) throws IOException {
        return loader.load(resource, new ClassPathResource(resource)).getFirst();
    }
}
