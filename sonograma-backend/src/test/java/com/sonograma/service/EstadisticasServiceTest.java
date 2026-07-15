package com.sonograma.service;

import com.sonograma.dto.EstadisticasResponseDTO;
import com.sonograma.entity.Venta;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.VentaRepository;
import com.sonograma.repository.PagoDeudaRepository;
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
        PagoDeudaRepository pagoDeudaRepository = mock(PagoDeudaRepository.class);
        EstadisticasService service = new EstadisticasService(ventaRepository, discoRepository, pagoDeudaRepository);

        Venta venta = Venta.builder()
                .fechaVenta(LocalDateTime.of(2026, 6, 15, 10, 0))
                .estado(EstadoVenta.COMPLETADA)
                .precioVenta(new BigDecimal("3000"))
                .costoEnvio(new BigDecimal("250"))
                .totalFinal(new BigDecimal("3250"))
                .build();
        when(ventaRepository.findAll()).thenReturn(List.of(venta));
        when(discoRepository.findAll()).thenReturn(List.of());
        when(pagoDeudaRepository.findAll()).thenReturn(List.of());

        EstadisticasResponseDTO response = service.obtenerCatalogoInventarioVentas();

        assertThat(response.getVentasPorMes()).hasSize(1);
        assertThat(response.getVentasPorMes().get(0).getTotalMonto()).isEqualByComparingTo("3000.00");
    }

    @Test
    void dashboardCuentaCobroInicialYPagoDeDeudaUnaSolaVezEnFechaDelPago() {
        VentaRepository ventaRepository = mock(VentaRepository.class);
        DiscoRepository discoRepository = mock(DiscoRepository.class);
        PagoDeudaRepository pagoDeudaRepository = mock(PagoDeudaRepository.class);
        EstadisticasService service = new EstadisticasService(ventaRepository, discoRepository, pagoDeudaRepository);

        Venta venta = Venta.builder().idVenta(1L)
                .fechaVenta(LocalDateTime.of(2026, 6, 15, 10, 0))
                .estado(EstadoVenta.COMPLETADA).precioVenta(new BigDecimal("8000"))
                .montoPagado(new BigDecimal("5000")).build();
        Deuda deuda = Deuda.builder().idDeuda(2L).venta(venta).build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(3L).deuda(deuda)
                .monto(new BigDecimal("5000")).fechaPago(java.time.LocalDate.of(2026, 7, 10))
                .createdAt(LocalDateTime.of(2026, 7, 10, 12, 0)).build();
        when(ventaRepository.findAll()).thenReturn(List.of(venta));
        when(discoRepository.findAll()).thenReturn(List.of());
        when(pagoDeudaRepository.findAll()).thenReturn(List.of(pago));

        EstadisticasResponseDTO response = service.obtenerCatalogoInventarioVentas();

        assertThat(response.getVentasPorMes()).extracting(i -> i.getClave() + ":" + i.getTotalMonto())
                .containsExactly("2026-06:0", "2026-07:5000");
    }
}
