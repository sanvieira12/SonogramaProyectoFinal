package com.sonograma.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class GoogleOAuthRedirects {

    private final String frontendBaseUrl;

    public GoogleOAuthRedirects(
            @Value("${sonograma.frontend.base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public String withHandoffCode(String code) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/login")
                .queryParam("oauth_code", code)
                .build()
                .toUriString();
    }

    public String withConfigurationError() {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/login")
                .queryParam("oauth_error", "configuration")
                .build()
                .toUriString();
    }
}
