package com.sonograma.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoTimestampLifecycleTest {

    @Test
    void newProductKeepsNormalCreationAndUpdateTimestampLifecycle() {
        Disco disco = Disco.builder()
            .artista("Artista")
            .album("Álbum")
            .build();
        LocalDateTime beforePersist = LocalDateTime.now();

        disco.onPrePersist();

        assertThat(disco.getFechaIngreso()).isAfterOrEqualTo(beforePersist);
        assertThat(disco.getFechaActualizacion()).isEqualTo(disco.getFechaIngreso());
    }
}
