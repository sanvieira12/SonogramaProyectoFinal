package com.sonograma.security;

import com.sonograma.service.OAuthLoginHandoffService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GoogleOAuthFailureHandler implements AuthenticationFailureHandler {

    private final OAuthLoginHandoffService handoffService;
    private final GoogleOAuthRedirects redirects;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        boolean cancelled = exception instanceof OAuth2AuthenticationException oauthException
                && "access_denied".equals(oauthException.getError().getErrorCode());
        String message = cancelled
                ? "El ingreso con Google fue cancelado."
                : "No se pudo completar el ingreso con Google. Intentá nuevamente.";
        HttpStatus status = cancelled ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        String code = handoffService.issueFailure(status, message);

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(redirects.withHandoffCode(code));
    }
}
