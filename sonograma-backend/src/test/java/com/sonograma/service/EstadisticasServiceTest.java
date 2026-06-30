package com.sonograma.service;

import com.sonograma.dto.EstadisticasResponseDTO;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstadisticasServiceTest {

    @Test
    void dashboardAgrupaVentasSinSumarCostoDeEnvio() {
        VentaRepository ventaRepository = mock(VentaRepository.class);
        DiscoRepository discoRepository = mock(DiscoRepository.class);
        EstadisticasService service = new EstadisticasService(ventaRepository, discoRepository);

        Venta venta = Venta.builder()
                .fechaVenta(LocalDateTime.of(2026, 6, 15, 10, 0))
                .estado(EstadoVenta.COMPLETADA)
                .precioVenta(new BigDecimal("3000"))
                .costoEnvio(new BigDecimal("250"))
                .totalFinal(new BigDecimal("3250"))
                .build();
        when(ventaRepository.findAll()).thenReturn(List.of(venta));
        when(discoRepository.findAll()).thenReturn(List.of());

        EstadisticasResponseDTO response = service.obtenerCatalogoInventarioVentas();

        assertThat(response.getVentasPorMes()).hasSize(1);
        assertThat(response.getVentasPorMes().get(0).getTotalMonto()).isEqualByComparingTo("3000.00");
    }
}
