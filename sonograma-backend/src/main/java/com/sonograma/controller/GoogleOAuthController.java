package com.sonograma.controller;

import com.sonograma.dto.GoogleOAuthErrorResponse;
import com.sonograma.dto.GoogleOAuthExchangeRequest;
import com.sonograma.security.GoogleOAuthRedirects;
import com.sonograma.service.OAuthLoginHandoffService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class GoogleOAuthController {

    private final OAuthLoginHandoffService handoffService;
    private final GoogleOAuthRedirects redirects;

    // Reached only when the OAuth client is disabled because credentials are missing.
    @GetMapping("/oauth2/authorization/google")
    public void unavailable(HttpServletResponse response) throws IOException {
        response.sendRedirect(redirects.withConfigurationError());
    }

    @PostMapping("/auth/google/exchange")
    public ResponseEntity<?> exchange(@Valid @RequestBody GoogleOAuthExchangeRequest request) {
        return handoffService.consume(request.code())
                .map(handoff -> handoff.successful()
                        ? ResponseEntity.ok(handoff.loginResponse())
                        : ResponseEntity.status(handoff.status())
                                .body(new GoogleOAuthErrorResponse(handoff.safeMessage())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new GoogleOAuthErrorResponse(
                                "El enlace de ingreso con Google venció o ya fue utilizado.")));
    }
}
