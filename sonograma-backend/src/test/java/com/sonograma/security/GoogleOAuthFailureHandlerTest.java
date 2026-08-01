package com.sonograma.security;

import com.sonograma.service.OAuthLoginHandoffService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthFailureHandlerTest {

    @Test
    void cancelledGoogleLoginReturnsToLoginWithoutInternalDetails() throws Exception {
        OAuthLoginHandoffService handoffs = new OAuthLoginHandoffService(60);
        GoogleOAuthFailureHandler handler = new GoogleOAuthFailureHandler(
                handoffs,
                new GoogleOAuthRedirects("https://tiendasonograma.com"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied")));

        assertThat(response.getRedirectedUrl()).startsWith(
                "https://tiendasonograma.com/login?oauth_code=");
        String code = UriComponentsBuilder.fromUriString(response.getRedirectedUrl())
                .build()
                .getQueryParams()
                .getFirst("oauth_code");
        assertThat(handoffs.consume(code)).get().satisfies(handoff -> {
            assertThat(handoff.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(handoff.safeMessage()).isEqualTo("El ingreso con Google fue cancelado.");
        });
        assertThat(response.getRedirectedUrl()).doesNotContain("access_denied");
    }
}
