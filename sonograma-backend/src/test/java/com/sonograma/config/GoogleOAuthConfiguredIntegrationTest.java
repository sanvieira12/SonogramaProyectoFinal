package com.sonograma.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "GOOGLE_CLIENT_ID=test-client-id",
        "GOOGLE_CLIENT_SECRET=test-client-secret",
        "sonograma.google.redirect-uri=https://tiendasonograma.com/api/login/oauth2/code/google",
        "spring.datasource.url=jdbc:h2:mem:sonograma-oauth-configured;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureMockMvc
class GoogleOAuthConfiguredIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authorizationEndpointStartsGoogleFlowWithExactProductionCallback() throws Exception {
        String location = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("redirect_uri"))
                .isEqualTo("https://tiendasonograma.com/api/login/oauth2/code/google");
    }
}
