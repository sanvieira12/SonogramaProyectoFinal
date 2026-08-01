package com.sonograma.security;

import com.sonograma.dto.LoginResponse;
import com.sonograma.service.GoogleOAuthAuthenticationService;
import com.sonograma.service.OAuthLoginHandoffService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleOAuthSuccessHandlerTest {

    @Test
    void verifiedApprovedGoogleIdentityCreatesOneTimeSuccessHandoff() throws Exception {
        GoogleOAuthAuthenticationService authenticationService =
                mock(GoogleOAuthAuthenticationService.class);
        OAuthLoginHandoffService handoffs = new OAuthLoginHandoffService(60);
        GoogleOAuthSuccessHandler handler = new GoogleOAuthSuccessHandler(
                authenticationService,
                handoffs,
                new GoogleOAuthRedirects("https://tiendasonograma.com"));
        OAuth2User user = mock(OAuth2User.class);
        Authentication authentication = mock(Authentication.class);
        LoginResponse login = LoginResponse.builder().token("google-jwt").build();
        when(authentication.getPrincipal()).thenReturn(user);
        when(user.getAttribute("email"))
                .thenReturn("sonograma.tiendadediscos@gmail.com");
        when(user.getAttribute("email_verified")).thenReturn(Boolean.TRUE);
        when(authenticationService.authenticate(
                "sonograma.tiendadediscos@gmail.com", true)).thenReturn(login);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), response, authentication);

        String code = response.getRedirectedUrl().substring(
                response.getRedirectedUrl().indexOf("oauth_code=") + "oauth_code=".length());
        assertThat(response.getRedirectedUrl()).doesNotContain("google-jwt");
        assertThat(handoffs.consume(code)).get()
                .extracting(OAuthLoginHandoffService.Handoff::loginResponse)
                .isEqualTo(login);
    }
}
