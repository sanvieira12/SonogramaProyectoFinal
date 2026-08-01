package com.sonograma.config;

import com.sonograma.security.GoogleOAuthFailureHandler;
import com.sonograma.security.GoogleOAuthSuccessHandler;
import com.sonograma.security.JwtAuthenticationFilter;
import com.sonograma.security.JwtTokenProvider;
import com.sonograma.security.UserDetailsServiceImpl;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final GoogleOAuthSuccessHandler googleOAuthSuccessHandler;
    private final GoogleOAuthFailureHandler googleOAuthFailureHandler;

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            // Keep API failures as HTTP responses even when OAuth login is configured.
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.FORBIDDEN)))
            // OAuth state uses a short-lived HttpSession. API authentication remains JWT-based.
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/registro").hasRole("ADMIN")
                .requestMatchers(
                    "/auth/login",
                    "/auth/google/exchange",
                    "/oauth2/authorization/**",
                    "/login/oauth2/code/**",
                    "/health",
                    "/actuator/**",
                    "/qr/descargar/**",
                    "/importar/vinylfuture/media/**",
                    "/importaciones/vinylfuture/media/**",
                    "/importaciones/discogs/covers/**",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        ClientRegistrationRepository googleRegistration = clientRegistrations.getIfAvailable();
        if (googleRegistration != null) {
            http.oauth2Login(oauth -> oauth
                    .clientRegistrationRepository(googleRegistration)
                    .successHandler(googleOAuthSuccessHandler)
                    .failureHandler(googleOAuthFailureHandler));
        }

        return http.build();
    }

    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
