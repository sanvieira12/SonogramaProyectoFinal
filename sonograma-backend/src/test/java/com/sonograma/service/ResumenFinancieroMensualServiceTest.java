package com.sonograma.service;

import com.sonograma.entity.DetalleVenta;
import com.sonograma.entity.Deuda;
import com.sonograma.entity.GastoTienda;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.enums.EstadoPago;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.GastoTiendaRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumenFinancieroMensualServiceTest {

    private VentaRepository ventaRepository;
    private PagoDeudaRepository pagoRepository;
    private GastoTiendaRepository gastoRepository;
    private DeudaRepository deudaRepository;
    private ResumenFinancieroMensualService service;

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        pagoRepository = mock(PagoDeudaRepository.class);
        gastoRepository = mock(GastoTiendaRepository.class);
        deudaRepository = mock(DeudaRepository.class);
        service = new ResumenFinancieroMensualService(
                ventaRepository, pagoRepository, gastoRepository,
                new ProfitCalculationService(ventaRepository,
                        mock(com.sonograma.repository.PedidoRepository.class),
                        mock(com.sonograma.repository.PedidoItemRepository.class),
                        mock(CatalogPricingService.class)), new IngresoLibroCalculator(deudaRepository),
                new BusinessTime(Clock.systemUTC()), new FinancialMovementPolicy());
        when(ventaRepository.findAllForProfitPeriod(any(), any())).thenReturn(List.of(
                sale("2026-06-10T10:00:00", "1000", "500", "400", 1, EstadoPago.PARCIAL),
                sale("2026-06-12T10:00:00", "500", null, null, 1, EstadoPago.PENDIENTE)));
        when(pagoRepository.findEntre(any(), any())).thenReturn(List.of(
                PagoDeuda.builder().monto(new BigDecimal("250")).fechaPago(LocalDate.of(2026, 6, 20)).build()));
        when(gastoRepository.findByFechaBetweenOrderByFechaAscIdGastoAsc(any(), any())).thenReturn(List.of(
                GastoTienda.builder().idGasto(1L).fecha(LocalDate.of(2026, 6, 15)).descripcion("Luz").monto(new BigDecimal("100")).build()));
    }

    @Test
    void calculaResumenSeparandoIngresosGananciaYBalance() {
        var result = service.obtener("2026-06");

        assertThat(result.getPeriodo()).isEqualTo("2026-06");
        assertThat(result.getCantidadVentas()).isEqualTo(2L);
        assertThat(result.getCantidadItems()).isEqualTo(2L);
        assertThat(result.getTotalVentas()).isEqualByComparingTo("1500.00");
        assertThat(result.getIngresosRegistrados()).isEqualByComparingTo("750.00");
        assertThat(result.getGananciaItems()).isEqualByComparingTo("600.00");
        assertThat(result.getGastos()).isEqualByComparingTo("100.00");
        assertThat(result.getBalanceFinal()).isEqualByComparingTo("750.00");
        assertThat(result.getBalanceFinal()).isEqualByComparingTo(result.getIngresosRegistrados());
        assertThat(result.getItemsGananciaNoDisponible()).isEqualTo(1);
        assertThat(result.getAdvertenciaGanancia()).contains("1 ítem");
    }

    @Test
    void elPagoDeDeudaSumaIngresoPeroNoVentaItemNiGanancia() {
        var result = service.obtener("2026-06");

        assertThat(result.getVentas()).hasSize(2);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getVentas().get(0).getMontoRecibido()).isEqualByComparingTo("500.00");
        assertThat(result.getVentas().get(0).getGananciaNeta()).isEqualByComparingTo("600.00");
    }

    @Test
    void unMesSinMovimientosDevuelveCerosYListasVacias() {
        when(ventaRepository.findAllForProfitPeriod(any(), any())).thenReturn(List.of());
        when(pagoRepository.findEntre(any(), any())).thenReturn(List.of());
        when(gastoRepository.findByFechaBetweenOrderByFechaAscIdGastoAsc(any(), any())).thenReturn(List.of());

        var result = service.obtener("2026-05");

        assertThat(result.getCantidadVentas()).isZero();
        assertThat(result.getCantidadItems()).isZero();
        assertThat(result.getIngresosRegistrados()).isEqualByComparingTo("0.00");
        assertThat(result.getBalanceFinal()).isEqualByComparingTo("0.00");
        assertThat(result.getVentas()).isEmpty();
    }

    @Test
    void noDuplicaVentaAcumuladaConPagoPosterior() {
        Venta venta = sale("2026-06-10T10:00:00", "1000", "1000", "400", 1, EstadoPago.PAGADO);
        Deuda deuda = Deuda.builder().idDeuda(90L).venta(venta)
                .montoTotal(new BigDecimal("1000"))
                .montoPagadoInicial(new BigDecimal("400"))
                .montoPagado(new BigDecimal("1000"))
                .montoPendiente(BigDecimal.ZERO)
                .build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(91L).deuda(deuda)
                .monto(new BigDecimal("600"))
                .fechaPago(LocalDate.of(2026, 6, 20))
                .build();
        when(ventaRepository.findAllForProfitPeriod(any(), any())).thenReturn(List.of(venta));
        when(pagoRepository.findEntre(any(), any())).thenReturn(List.of(pago));
        when(gastoRepository.findByFechaBetweenOrderByFechaAscIdGastoAsc(any(), any())).thenReturn(List.of());
        when(deudaRepository.findByVentaIdVenta(venta.getIdVenta())).thenReturn(java.util.Optional.of(deuda));

        var result = service.obtener("2026-06");

        assertThat(result.getIngresosRegistrados()).isEqualByComparingTo("1000");
        assertThat(result.getVentas().get(0).getMontoRecibido()).isEqualByComparingTo("400");
    }

    @Test
    void mantieneIngresoRealYDeudaPendienteEnPagoParcial() {
        Venta venta = sale("2026-06-10T10:00:00", "1000", "500", "300", 1, EstadoPago.PARCIAL);
        Deuda deuda = Deuda.builder().idDeuda(92L).venta(venta)
                .montoTotal(new BigDecimal("1000"))
                .montoPagadoInicial(new BigDecimal("300"))
                .montoPagado(new BigDecimal("500"))
                .montoPendiente(new BigDecimal("500"))
                .build();
        PagoDeuda pago = PagoDeuda.builder().idPagoDeuda(93L).deuda(deuda)
                .monto(new BigDecimal("200"))
                .fechaPago(LocalDate.of(2026, 6, 20))
                .build();
        when(ventaRepository.findAllForProfitPeriod(any(), any())).thenReturn(List.of(venta));
        when(pagoRepository.findEntre(any(), any())).thenReturn(List.of(pago));
        when(gastoRepository.findByFechaBetweenOrderByFechaAscIdGastoAsc(any(), any())).thenReturn(List.of());
        when(deudaRepository.findByVentaIdVenta(venta.getIdVenta())).thenReturn(java.util.Optional.of(deuda));

        var result = service.obtener("2026-06");

        assertThat(result.getIngresosRegistrados()).isEqualByComparingTo("500");
        assertThat(result.getVentas().get(0).getMontoRecibido()).isEqualByComparingTo("300");
        assertThat(result.getVentas().get(0).getDeudaPendiente()).isEqualByComparingTo("500");
    }

    @Test
    void incluyePagosValidosAunqueLaDeudaEsteInactivaOPertenezcaAVentaCancelada() {
        Venta cancelada = sale("2026-06-10T10:00:00", "1000", "1000", "400", 1, EstadoPago.PAGADO);
        cancelada.setEstado(EstadoVenta.CANCELADA);
        Deuda inactiva = Deuda.builder().idDeuda(95L).activa(false).venta(cancelada).build();
        PagoDeuda historicoInactivo = PagoDeuda.builder().idPagoDeuda(96L).deuda(inactiva)
                .monto(new BigDecimal("200")).fechaPago(LocalDate.of(2026, 6, 20)).build();
        PagoDeuda anulado = PagoDeuda.builder().idPagoDeuda(97L).deuda(inactiva)
                .monto(new BigDecimal("300")).fechaPago(LocalDate.of(2026, 6, 21)).anulado(true).build();

        when(ventaRepository.findAllForProfitPeriod(any(), any())).thenReturn(List.of());
        when(pagoRepository.findEntre(any(), any())).thenReturn(List.of(historicoInactivo, anulado));
        when(gastoRepository.findByFechaBetweenOrderByFechaAscIdGastoAsc(any(), any())).thenReturn(List.of());

        var result = service.obtener("2026-06");

        assertThat(result.getIngresosRegistrados()).isEqualByComparingTo("200");
    }

    private Venta sale(String date, String price, String paid, String cost, int quantity, EstadoPago paymentState) {
        DetalleVenta detail = DetalleVenta.builder()
                .precioUnitario(new BigDecimal(price))
                .costoAdquisicionUnitario(cost == null ? null : new BigDecimal(cost))
                .cantidad(quantity)
                .build();
        Venta sale = Venta.builder()
                .idVenta((long) price.hashCode())
                .fechaVenta(LocalDateTime.parse(date))
                .estado(EstadoVenta.COMPLETADA)
                .estadoPago(paymentState)
                .montoPagado(paid == null ? null : new BigDecimal(paid))
                .montoDeuda(paid == null ? new BigDecimal(price) : new BigDecimal(price).subtract(new BigDecimal(paid)))
                .detalles(List.of(detail))
                .build();
        detail.setVenta(sale);
        return sale;
    }
}
