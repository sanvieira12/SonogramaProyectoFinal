package com.sonograma.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuthClientConfigurationTest {

    @Test
    void productionRegistrationUsesTheExactConfiguredCallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("GOOGLE_CLIENT_ID", "client-id")
                .withProperty("GOOGLE_CLIENT_SECRET", "client-secret")
                .withProperty(
                        "sonograma.google.redirect-uri",
                        "https://tiendasonograma.com/api/login/oauth2/code/google");

        ClientRegistrationRepository repository = new GoogleOAuthClientConfiguration()
                .googleClientRegistrationRepository(environment);
        ClientRegistration google = repository.findByRegistrationId("google");

        assertThat(google).isNotNull();
        assertThat(google.getRedirectUri()).isEqualTo(
                "https://tiendasonograma.com/api/login/oauth2/code/google");
        assertThat(google.getScopes()).contains("openid", "profile", "email");
    }
}
