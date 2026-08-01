package com.sonograma.service;

import com.sonograma.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthLoginHandoffServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void successfulHandoffIsSingleUseAndContainsTheJwtOnlyInServerMemory() {
        OAuthLoginHandoffService service = new OAuthLoginHandoffService(
                Duration.ofSeconds(60), CLOCK);
        LoginResponse login = LoginResponse.builder().token("jwt-secret").build();

        String code = service.issueSuccess(login);

        assertThat(code).doesNotContain("jwt-secret");
        assertThat(service.consume(code)).get()
                .extracting(OAuthLoginHandoffService.Handoff::loginResponse)
                .isEqualTo(login);
        assertThat(service.consume(code)).isEmpty();
    }

    @Test
    void failureHandoffPreservesASafe403ForAnUnauthorizedGoogleAccount() {
        OAuthLoginHandoffService service = new OAuthLoginHandoffService(
                Duration.ofSeconds(60), CLOCK);

        String code = service.issueFailure(
                HttpStatus.FORBIDDEN,
                "Esta cuenta de Google no está autorizada para ingresar a Sonograma.");

        assertThat(service.consume(code)).get().satisfies(handoff -> {
            assertThat(handoff.status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(handoff.safeMessage()).doesNotContain("token");
        });
    }

    @Test
    void expiredHandoffCannotBeExchanged() {
        OAuthLoginHandoffService service = new OAuthLoginHandoffService(
                Duration.ZERO, CLOCK);
        String code = service.issueSuccess(LoginResponse.builder().token("jwt").build());

        assertThat(service.consume(code)).isEmpty();
    }
}
