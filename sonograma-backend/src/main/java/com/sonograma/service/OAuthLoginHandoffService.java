package com.sonograma.service;

import com.sonograma.dto.LoginResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OAuthLoginHandoffService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Map<String, Handoff> handoffs = new ConcurrentHashMap<>();
    private final Duration timeToLive;
    private final Clock clock;

    @Autowired
    public OAuthLoginHandoffService(
            @Value("${sonograma.google.handoff-ttl-seconds:60}") long ttlSeconds) {
        this(Duration.ofSeconds(ttlSeconds), Clock.systemUTC());
    }

    OAuthLoginHandoffService(Duration timeToLive, Clock clock) {
        this.timeToLive = timeToLive;
        this.clock = clock;
    }

    public String issueSuccess(LoginResponse loginResponse) {
        return issue(new Handoff(loginResponse, HttpStatus.OK, null, expiresAt()));
    }

    public String issueFailure(HttpStatus status, String safeMessage) {
        return issue(new Handoff(null, status, safeMessage, expiresAt()));
    }

    public Optional<Handoff> consume(String code) {
        purgeExpired();
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Handoff handoff = handoffs.remove(code);
        if (handoff == null || !handoff.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(handoff);
    }

    private String issue(Handoff handoff) {
        purgeExpired();
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        handoffs.put(code, handoff);
        return code;
    }

    private Instant expiresAt() {
        return clock.instant().plus(timeToLive);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        handoffs.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public record Handoff(
            LoginResponse loginResponse,
            HttpStatus status,
            String safeMessage,
            Instant expiresAt) {

        public boolean successful() {
            return loginResponse != null && status.is2xxSuccessful();
        }
    }
}
