package com.sonograma.security;

import com.sonograma.dto.LoginResponse;
import com.sonograma.service.GoogleOAuthAuthenticationService;
import com.sonograma.service.OAuthLoginHandoffService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {

    private final GoogleOAuthAuthenticationService googleAuthenticationService;
    private final OAuthLoginHandoffService handoffService;
    private final GoogleOAuthRedirects redirects;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        String code;
        try {
            OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();
            String email = googleUser.getAttribute("email");
            boolean emailVerified = Boolean.TRUE.equals(googleUser.getAttribute("email_verified"));
            LoginResponse loginResponse = googleAuthenticationService.authenticate(email, emailVerified);
            code = handoffService.issueSuccess(loginResponse);
        } catch (GoogleOAuthAuthenticationException exception) {
            code = handoffService.issueFailure(exception.getStatus(), exception.getSafeMessage());
        } catch (RuntimeException exception) {
            log.error("Google authentication could not create the local administrator session", exception);
            code = handoffService.issueFailure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo crear la sesión de Sonograma. Intentá nuevamente.");
        }

        invalidateTemporarySession(request);
        response.sendRedirect(redirects.withHandoffCode(code));
    }

    private void invalidateTemporarySession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
