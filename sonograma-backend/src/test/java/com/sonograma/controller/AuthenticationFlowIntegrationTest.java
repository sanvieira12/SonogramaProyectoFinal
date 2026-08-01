package com.sonograma.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.sonograma.service.OAuthLoginHandoffService;
import org.springframework.http.HttpStatus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OAuthLoginHandoffService handoffService;

    @Test
    void existingAdminPasswordLoginStillSucceedsAndJwtAuthenticatesSession() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreUsuario":"admin","contrasenia":"admin123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nombreUsuario").value("admin"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode login = objectMapper.readTree(body);
        mockMvc.perform(get("/auth/session")
                        .header("Authorization", "Bearer " + login.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("admin"));
    }

    @Test
    void invalidPasswordStillFails() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreUsuario":"admin","contrasenia":"incorrecta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedRoutesAndRegistrationRemainClosedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/auth/session"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreUsuario":"publico","email":"publico@example.com","contrasenia":"password"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOAuthConfigurationReturnsSafelyToLogin() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:5173/login?oauth_error=configuration"));
    }

    @Test
    void unauthorizedGoogleAccountExchangeReturns403WithSafeMessage() throws Exception {
        String code = handoffService.issueFailure(
                HttpStatus.FORBIDDEN,
                "Esta cuenta de Google no está autorizada para ingresar a Sonograma.");

        mockMvc.perform(post("/auth/google/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExchangeRequest(code))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Esta cuenta de Google no está autorizada para ingresar a Sonograma."));
    }

    private record ExchangeRequest(String code) {
    }
}
