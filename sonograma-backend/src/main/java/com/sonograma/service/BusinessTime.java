package com.sonograma.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single source for current Sonograma business date/time. */
@Component
public class BusinessTime {

    public static final ZoneId MONTEVIDEO = ZoneId.of("America/Montevideo");

    private final Clock clock;

    public BusinessTime(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(MONTEVIDEO));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(MONTEVIDEO));
    }
}
