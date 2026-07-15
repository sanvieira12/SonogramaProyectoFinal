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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstadisticasServiceTest {

    @Test
    void dashboardIncluyeCobroPreVentaEnMesYSemanaSinDuplicarlo() {
        Fixture fixture = new Fixture();
        Venta cobro = venta(30L, LocalDateTime.of(2026, 7, 15, 16, 0), "1800", "1800", EstadoVenta.COMPLETADA);
        cobro.setOrigen("PRE_VENTA"); cobro.setIdPreVentaOrigen(9L); cobro.setTotalFinal(new BigDecimal("1800"));
        fixture.stub(List.of(cobro), List.of());

        var response = fixture.service.obtenerCatalogoInventarioVentas();

        assertThat(response.getVentasPorMes()).extracting(i -> i.getClave() + ":" + i.getTotalMonto())
                .containsExactly("2026-07:1800");
        assertThat(response.getVentasPorSemana()).extracting(i -> i.getClave() + ":" + i.getTotalMonto())
                .containsExactly("2026-S29:1800");
        assertThat(response.getVentasPorMes().get(0).getCantidad()).isEqualTo(1);
    }

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
                .containsExactly("2026-07:5000");
        assertThat(response.getVentasPorMes().get(0).getCantidad()).isZero();
        assertThat(response.getVentasPorMes().get(0).getCantidadPagosDeuda()).isEqualTo(1);
    }

    @Test
    void dashboardCuentaVentaInmediataPeroNoVentaTotalmenteACredito() {
        Fixture fixture = new Fixture();
        Venta inmediata = venta(1L, LocalDateTime.of(2026, 7, 1, 9, 0), "2500", "2500", EstadoVenta.COMPLETADA);
        Venta credito = venta(2L, LocalDateTime.of(2026, 7, 2, 9, 0), "4000", "0", EstadoVenta.COMPLETADA);
        fixture.stub(List.of(inmediata, credito), List.of());

        var julio = fixture.service.obtenerCatalogoInventarioVentas().getVentasPorMes().get(0);

        assertThat(julio.getTotalMonto()).isEqualByComparingTo("2500");
        assertThat(julio.getCantidad()).isEqualTo(1);
        assertThat(julio.getCantidadPagosDeuda()).isZero();
    }

    @Test
    void dashboardCuentaPagosParcialesYCompletosComoTransaccionesIndependientes() {
        Fixture fixture = new Fixture();
        Venta venta = venta(1L, LocalDateTime.of(2026, 7, 1, 9, 0), "5000", "5000", EstadoVenta.COMPLETADA);
        Deuda deuda = Deuda.builder().idDeuda(2L).venta(venta).build();
        PagoDeuda parcial = pago(10L, deuda, "1500", LocalDate.of(2026, 7, 6));
        PagoDeuda finalPago = pago(11L, deuda, "3500", LocalDate.of(2026, 7, 7));
        fixture.stub(List.of(venta), List.of(parcial, finalPago));

        var response = fixture.service.obtenerCatalogoInventarioVentas();
        var julio = response.getVentasPorMes().get(0);

        assertThat(julio.getTotalMonto()).isEqualByComparingTo("5000");
        assertThat(julio.getCantidad()).isZero();
        assertThat(julio.getCantidadPagosDeuda()).isEqualTo(2);
        assertThat(response.getVentasPorSemana()).extracting(i -> i.getClave() + ":" + i.getTotalMonto())
                .containsExactly("2026-S28:5000");
    }

    @Test
    void dashboardUsaFechaEfectivaDelPagoEnLimitesMensualesYSemanales() {
        Fixture fixture = new Fixture();
        Deuda deuda = Deuda.builder().idDeuda(2L).build();
        PagoDeuda junio = pago(10L, deuda, "100", LocalDate.of(2026, 6, 30));
        junio.setCreatedAt(LocalDateTime.of(2026, 7, 1, 1, 0));
        PagoDeuda julio = pago(11L, deuda, "200", LocalDate.of(2026, 7, 1));
        fixture.stub(List.of(), List.of(junio, julio));

        var response = fixture.service.obtenerCatalogoInventarioVentas();

        assertThat(response.getVentasPorMes()).extracting(i -> i.getClave() + ":" + i.getTotalMonto())
                .containsExactly("2026-06:100", "2026-07:200");
        assertThat(response.getVentasPorSemana()).extracting(i -> i.getClave() + ":" + i.getTotalMonto())
                .containsExactly("2026-S27:300");
    }

    @Test
    void dashboardExcluyeVentasCanceladasYSusPagos() {
        Fixture fixture = new Fixture();
        Venta cancelada = venta(1L, LocalDateTime.of(2026, 7, 1, 9, 0), "3000", "3000", EstadoVenta.CANCELADA);
        Deuda deuda = Deuda.builder().idDeuda(2L).venta(cancelada).build();
        fixture.stub(List.of(cancelada), List.of(pago(10L, deuda, "3000", LocalDate.of(2026, 7, 2))));

        var response = fixture.service.obtenerCatalogoInventarioVentas();

        assertThat(response.getVentasPorMes()).isEmpty();
        assertThat(response.getVentasPorSemana()).isEmpty();
    }

    private static Venta venta(Long id, LocalDateTime fecha, String total, String pagado, EstadoVenta estado) {
        return Venta.builder().idVenta(id).fechaVenta(fecha).estado(estado)
                .precioVenta(new BigDecimal(total)).montoPagado(new BigDecimal(pagado)).build();
    }

    private static PagoDeuda pago(Long id, Deuda deuda, String monto, LocalDate fecha) {
        return PagoDeuda.builder().idPagoDeuda(id).deuda(deuda).monto(new BigDecimal(monto))
                .fechaPago(fecha).createdAt(fecha.atTime(12, 0)).build();
    }

    private static class Fixture {
        private final VentaRepository ventas = mock(VentaRepository.class);
        private final DiscoRepository discos = mock(DiscoRepository.class);
        private final PagoDeudaRepository pagos = mock(PagoDeudaRepository.class);
        private final EstadisticasService service = new EstadisticasService(ventas, discos, pagos);

        private void stub(List<Venta> ventasResult, List<PagoDeuda> pagosResult) {
            when(ventas.findAll()).thenReturn(ventasResult);
            when(discos.findAll()).thenReturn(List.of());
            when(pagos.findAll()).thenReturn(pagosResult);
        }
    }
}
