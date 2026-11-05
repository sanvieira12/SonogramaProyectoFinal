package com.sonograma.service;

import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NumeroFacturaVentaServiceTest {

    @Test
    void generaElSiguienteNumeroDesdeElMayorAunqueHayaHuecos() {
        VentaRepository ventas = mock(VentaRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(null);
        when(ventas.findNumerosFacturaByAnio(2026)).thenReturn(List.of(
                "F-2026-001",
                "F-2026-238",
                "F-2026-239",
                "F-2026-INVALIDA"
        ));

        NumeroFacturaVentaService service = new NumeroFacturaVentaService(ventas, jdbc);

        assertThat(service.generar(2026)).isEqualTo("F-2026-240");
    }
}
