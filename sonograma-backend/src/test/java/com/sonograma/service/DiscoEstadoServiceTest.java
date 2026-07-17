package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.enums.EstadoDisco;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoEstadoServiceTest {

    private final DiscoQrCopyService copies = mock(DiscoQrCopyService.class);
    private final DiscoEstadoService service = new DiscoEstadoService(copies);

    @Test
    void marcaComoVendidoCuandoNoHayDisponiblesPeroHayCopiasVendidas() {
        Disco disco = Disco.builder().idDisco(7L).estado(EstadoDisco.SIN_STOCK).build();
        when(copies.countAvailableCopies(7L)).thenReturn(0L);
        when(copies.soldCopies(7L)).thenReturn(1);

        service.aplicar(disco);

        assertThat(disco.getEstado()).isEqualTo(EstadoDisco.VENDIDO);
        assertThat(disco.getCantidadCopias()).isZero();
    }

    @Test
    void conservaReservadoMientrasQuedeUnaCopiaDisponible() {
        Disco disco = Disco.builder().idDisco(8L).estado(EstadoDisco.RESERVADO).build();
        when(copies.countAvailableCopies(8L)).thenReturn(1L);
        when(copies.soldCopies(8L)).thenReturn(0);

        service.aplicar(disco);

        assertThat(disco.getEstado()).isEqualTo(EstadoDisco.RESERVADO);
    }

    @Test
    void distingueSinStockSinCopiasVendidas() {
        Disco disco = Disco.builder().idDisco(9L).estado(EstadoDisco.DISPONIBLE).build();
        when(copies.countAvailableCopies(9L)).thenReturn(0L);
        when(copies.soldCopies(9L)).thenReturn(0);

        service.aplicar(disco);

        assertThat(disco.getEstado()).isEqualTo(EstadoDisco.SIN_STOCK);
    }
}
