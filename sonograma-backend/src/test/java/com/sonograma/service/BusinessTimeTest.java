package com.sonograma.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeTest {

    @Test
    void mapsMontevideoBoundariesWithAFixedClock() {
        assertBusinessTime("2026-09-14T20:00:00", "2026-09-14");
        assertBusinessTime("2026-09-14T21:30:00", "2026-09-14");
        assertBusinessTime("2026-09-14T23:59:59", "2026-09-14");
        assertBusinessTime("2026-09-15T00:00:00", "2026-09-15");
        assertBusinessTime("2026-09-30T23:59:00", "2026-09-30");
        assertBusinessTime("2026-10-01T00:00:00", "2026-10-01");
        assertBusinessTime("2026-12-31T23:59:00", "2026-12-31");
        assertBusinessTime("2027-01-01T00:00:00", "2027-01-01");
    }

    @Test
    void appliesMontevideoEvenWhenTheClockHasAnotherZone() {
        Instant instant = LocalDateTime.of(2026, 9, 14, 23, 59)
                .atZone(BusinessTime.MONTEVIDEO)
                .toInstant();

        assertThat(new BusinessTime(Clock.fixed(instant, ZoneOffset.UTC)).today())
                .isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(new BusinessTime(Clock.fixed(instant, java.time.ZoneId.of("Asia/Tokyo"))).today())
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    private void assertBusinessTime(String businessLocal, String expectedDate) {
        Instant instant = LocalDateTime.parse(businessLocal)
                .atZone(BusinessTime.MONTEVIDEO)
                .toInstant();
        BusinessTime time = new BusinessTime(Clock.fixed(instant, ZoneOffset.UTC));

        assertThat(time.now()).isEqualTo(LocalDateTime.parse(businessLocal));
        assertThat(time.today()).isEqualTo(LocalDate.parse(expectedDate));
    }
}
