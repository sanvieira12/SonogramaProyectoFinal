package com.sonograma.config;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@Conditional(GoogleOAuthClientConfiguration.GoogleCredentialsPresentCondition.class)
public class GoogleOAuthClientConfiguration {

    @Bean
    ClientRegistrationRepository googleClientRegistrationRepository(Environment environment) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(environment.getRequiredProperty("GOOGLE_CLIENT_ID").trim())
                .clientSecret(environment.getRequiredProperty("GOOGLE_CLIENT_SECRET").trim())
                .scope("openid", "profile", "email")
                .redirectUri(environment.getRequiredProperty("sonograma.google.redirect-uri"))
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }

    @Bean
    OAuth2AuthorizedClientService googleAuthorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    static final class GoogleCredentialsPresentCondition extends SpringBootCondition {
        @Override
        public ConditionOutcome getMatchOutcome(
                ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            boolean configured = StringUtils.hasText(environment.getProperty("GOOGLE_CLIENT_ID"))
                    && StringUtils.hasText(environment.getProperty("GOOGLE_CLIENT_SECRET"));
            return configured
                    ? ConditionOutcome.match("Google OAuth credentials are configured")
                    : ConditionOutcome.noMatch("Google OAuth credentials are not configured");
        }
    }
}
