package com.tikitaka.platform.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;

class OpenApiConfigTest {

    @Test
    void 공개_명세에서_내부_API를_제외한다() {
        GroupedOpenApi api = new OpenApiConfig().platformPublicApi();

        assertThat(api.getGroup()).isEqualTo("platform");
        assertThat(api.getPathsToMatch()).containsExactly("/api/v1/**");
        assertThat(api.getPathsToExclude()).containsExactly("/api/v1/internal/**");
    }
}
