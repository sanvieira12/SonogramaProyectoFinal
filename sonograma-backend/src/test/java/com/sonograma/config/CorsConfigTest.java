package com.sonograma.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private static final String PRODUCTION_ORIGINS =
            "https://tiendasonograma.com,https://www.tiendasonograma.com";

    @Test
    void allowsOnlyTheConfiguredProductionOrigins() {
        CorsConfiguration configuration = configurationFor(PRODUCTION_ORIGINS);

        assertThat(configuration.checkOrigin("https://tiendasonograma.com"))
                .isEqualTo("https://tiendasonograma.com");
        assertThat(configuration.checkOrigin("https://www.tiendasonograma.com"))
                .isEqualTo("https://www.tiendasonograma.com");
        assertThat(configuration.checkOrigin("http://tiendasonograma.com")).isNull();
        assertThat(configuration.checkOrigin("https://example.com")).isNull();
    }

    @Test
    void allowsTheApplicationMethodsAndHeadersWithoutWildcards() {
        CorsConfiguration configuration = configurationFor(PRODUCTION_ORIGINS);

        assertThat(configuration.getAllowedOriginPatterns()).isNull();
        assertThat(configuration.getAllowedMethods())
                .containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept");
        assertThat(configuration.getExposedHeaders())
                .containsExactly("Content-Disposition", "X-Pedido-Id");
    }

    private CorsConfiguration configurationFor(String origins) {
        CorsConfigurationSource source = new CorsConfig(origins).corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");

        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertThat(configuration).isNotNull();
        return configuration;
    }
}
