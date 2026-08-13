package com.sonograma.controller;

import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.service.DeudaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeudaControllerTest {

    private DeudaService deudaService;
    private DeudaController controller;

    @BeforeEach
    void setUp() {
        deudaService = mock(DeudaService.class);
        controller = new DeudaController(deudaService, mock(ClienteRepository.class));
    }

    @Test
    void rechazaEliminacionSinConfirmacionExplicita() {
        assertThatThrownBy(() -> controller.eliminar(42L, null))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Escribí ELIMINAR para confirmar la eliminación definitiva");

        verify(deudaService, never()).eliminar(42L);
    }

    @Test
    void eliminaSolamenteConConfirmacionExplicita() {
        controller.eliminar(42L, "ELIMINAR");

        verify(deudaService).eliminar(42L);
    }
}
