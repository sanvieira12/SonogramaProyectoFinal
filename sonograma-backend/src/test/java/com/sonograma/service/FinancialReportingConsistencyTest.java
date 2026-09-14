package com.sonograma.service;

import com.sonograma.entity.Deuda;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.PagoDeuda;
import com.sonograma.entity.Venta;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.enums.EstadoVenta;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DetalleVentaRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.repository.DireccionClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.EnvioRepository;
import com.sonograma.repository.GastoTiendaRepository;
import com.sonograma.repository.PagoDeudaRepository;
import com.sonograma.repository.VentaRepository;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinancialReportingConsistencyTest {

    @Test
    void libroDashboardYResumenUsanLaMismaPoblacionDePagos() {
        VentaRepository ventas = mock(VentaRepository.class);
        PagoDeudaRepository pagos = mock(PagoDeudaRepository.class);
        DeudaRepository deudas = mock(DeudaRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        GastoTiendaRepository gastos = mock(GastoTiendaRepository.class);
        List<PagoDeuda> population = List.of(
                payment(1L, Deuda.builder().idDeuda(1L).activa(true).build(), "100", false),
                payment(2L, Deuda.builder().idDeuda(2L).activa(false)
                        .venta(Venta.builder().estado(EstadoVenta.CANCELADA).build()).build(), "200", false),
                payment(3L, Deuda.builder().idDeuda(3L).activa(true).build(), "900", true));

        when(ventas.findAllByOrderByFechaVentaDesc()).thenReturn(List.of());
        when(ventas.findAll()).thenReturn(List.of());
        when(ventas.findAllForProfitPeriod(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(pagos.findAll()).thenReturn(population);
        when(pagos.findEntre(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(population);
        when(gastos.findByFechaBetweenOrderByFechaAscIdGastoAsc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(discos.findAll()).thenReturn(List.of());

        FinancialMovementPolicy policy = new FinancialMovementPolicy();
        IngresoLibroCalculator calculator = new IngresoLibroCalculator(deudas);
        BusinessTime time = new BusinessTime(Clock.systemUTC());
        VentaService libroService = new VentaService(
                ventas, mock(EnvioRepository.class), mock(ClienteRepository.class), discos,
                mock(DireccionClienteRepository.class), deudas, mock(DetalleVentaRepository.class), pagos,
                mock(ClienteService.class), mock(DeudaService.class), mock(CostosVentaService.class),
                mock(ProfitCalculationService.class), mock(DiscoQrCopyService.class), mock(DiscoEstadoService.class),
                calculator, time, policy);
        EstadisticasService dashboardService = new EstadisticasService(ventas, discos, pagos, calculator, time, policy);
        ResumenFinancieroMensualService summaryService = new ResumenFinancieroMensualService(
                ventas, pagos, gastos, mock(ProfitCalculationService.class), calculator, time, policy);

        BigDecimal libroTotal = libroService.obtenerLibro("2026-09-01", "2026-09-30", null, null).stream()
                .map(row -> row.getMontoMovimiento())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dashboardTotal = dashboardService.obtenerCatalogoInventarioVentas().getVentasPorMes().stream()
                .map(row -> row.getTotalMonto())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal summaryTotal = summaryService.obtener("2026-09").getIngresosRegistrados();

        assertThat(libroTotal).isEqualByComparingTo("300");
        assertThat(dashboardTotal).isEqualByComparingTo("300");
        assertThat(summaryTotal).isEqualByComparingTo("300");
    }

    @Test
    void datasetRepresentativoReconcilesLibroDashboardResumenYExportacion() throws Exception {
        VentaRepository ventas = mock(VentaRepository.class);
        PagoDeudaRepository pagos = mock(PagoDeudaRepository.class);
        DeudaRepository deudas = mock(DeudaRepository.class);
        DiscoRepository discos = mock(DiscoRepository.class);
        GastoTiendaRepository gastos = mock(GastoTiendaRepository.class);
        var envios = mock(EnvioRepository.class);
        var profit = mock(ProfitCalculationService.class);
        BusinessTime time = new BusinessTime(Clock.fixed(
                LocalDate.of(2026, 9, 14).atTime(12, 0).atZone(BusinessTime.MONTEVIDEO).toInstant(),
                java.time.ZoneOffset.UTC));
        FinancialMovementPolicy policy = new FinancialMovementPolicy();
        IngresoLibroCalculator calculator = new IngresoLibroCalculator(deudas);

        Cliente cliente = new Cliente();
        cliente.setIdCliente(1L);
        cliente.setNombre("Auditor");
        cliente.setActivo(true);
        Venta normal = sale(10L, cliente, "1000", "1000", EstadoVenta.COMPLETADA);
        Venta linkedFull = sale(11L, cliente, "1000", "1000", EstadoVenta.COMPLETADA);
        Venta partial = sale(12L, cliente, "1000", "400", EstadoVenta.COMPLETADA);
        Venta cancelled = sale(13L, cliente, "1000", "600", EstadoVenta.CANCELADA);

        Deuda fullDebt = Deuda.builder().idDeuda(21L).venta(linkedFull).cliente(cliente)
                .montoTotal(new BigDecimal("1000")).montoPagadoInicial(new BigDecimal("400"))
                .activa(true).build();
        Deuda partialDebt = Deuda.builder().idDeuda(22L).venta(partial).cliente(cliente)
                .montoTotal(new BigDecimal("1000")).montoPagadoInicial(new BigDecimal("200"))
                .activa(true).build();
        Deuda manualDebt = Deuda.builder().idDeuda(23L).cliente(cliente).activa(true).build();
        Deuda inactiveDebt = Deuda.builder().idDeuda(24L).cliente(cliente).activa(false).build();
        Deuda cancelledDebt = Deuda.builder().idDeuda(25L).venta(cancelled).cliente(cliente).activa(false).build();

        List<PagoDeuda> population = List.of(
                payment(31L, fullDebt, "600", false),
                payment(32L, partialDebt, "300", true),
                payment(33L, partialDebt, "200", false),
                payment(34L, manualDebt, "300", false),
                payment(35L, inactiveDebt, "150", false),
                payment(36L, cancelledDebt, "250", false),
                payment(37L, manualDebt, "900", true));
        List<Venta> allSales = List.of(normal, linkedFull, partial, cancelled);

        when(ventas.findAllByOrderByFechaVentaDesc()).thenReturn(allSales);
        when(ventas.findAll()).thenReturn(allSales);
        when(ventas.findAllForProfitPeriod(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(normal, linkedFull, partial));
        when(pagos.findAll()).thenReturn(population);
        when(pagos.findEntre(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(population);
        when(gastos.findByFechaBetweenOrderByFechaAscIdGastoAsc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(discos.findAll()).thenReturn(List.of());
        when(envios.findByVentaIdVenta(org.mockito.ArgumentMatchers.anyLong())).thenReturn(Optional.empty());
        when(profit.netProfitForSale(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProfitResult(BigDecimal.ZERO, ProfitStatus.ZERO, 0, List.of()));
        when(deudas.findByVentaIdVenta(11L)).thenReturn(Optional.of(fullDebt));
        when(deudas.findByVentaIdVenta(12L)).thenReturn(Optional.of(partialDebt));

        VentaService libroService = new VentaService(
                ventas, envios, mock(ClienteRepository.class), discos,
                mock(DireccionClienteRepository.class), deudas, mock(DetalleVentaRepository.class), pagos,
                mock(ClienteService.class), mock(DeudaService.class), mock(CostosVentaService.class), profit,
                mock(DiscoQrCopyService.class), mock(DiscoEstadoService.class), calculator, time, policy);
        EstadisticasService dashboardService = new EstadisticasService(ventas, discos, pagos, calculator, time, policy);
        ResumenFinancieroMensualService summaryService = new ResumenFinancieroMensualService(
                ventas, pagos, gastos, profit, calculator, time, policy);

        List<VentaResponseDTO> libro = libroService.obtenerLibro("2026-09-01", "2026-09-30", null, null);
        BigDecimal libroTotal = libro.stream().map(VentaResponseDTO::getMontoMovimiento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dashboardTotal = dashboardService.obtenerCatalogoInventarioVentas().getVentasPorMes().stream()
                .map(row -> row.getTotalMonto()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal summaryTotal = summaryService.obtener("2026-09").getIngresosRegistrados();
        byte[] export = new ExcelExportService(profit).exportarLibroMovimientos(libro);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            var sheet = workbook.getSheetAt(0);
            double exportTotal = java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                    .filter(row -> row.getCell(6) != null && "TOTALES".equals(row.getCell(6).getStringCellValue()))
                    .findFirst().orElseThrow().getCell(8).getNumericCellValue();
            assertThat(exportTotal).isEqualTo(3100.0);
        }

        assertThat(libro).hasSize(8);
        assertThat(libroTotal).isEqualByComparingTo("3100");
        assertThat(dashboardTotal).isEqualByComparingTo("3100");
        assertThat(summaryTotal).isEqualByComparingTo("3100");
    }

    private static Venta sale(Long id, Cliente cliente, String total, String paid, EstadoVenta state) {
        return Venta.builder().idVenta(id).cliente(cliente)
                .fechaVenta(LocalDate.of(2026, 9, 14).atTime(10, 0))
                .precioVenta(new BigDecimal(total)).totalFinal(new BigDecimal(total))
                .montoPagado(new BigDecimal(paid)).estado(state).build();
    }

    private static PagoDeuda payment(Long id, Deuda debt, String amount, boolean annulled) {
        return PagoDeuda.builder().idPagoDeuda(id).deuda(debt)
                .monto(new BigDecimal(amount)).fechaPago(LocalDate.of(2026, 9, 14))
                .createdAt(LocalDateTime.of(2026, 9, 14, 12, 0)).anulado(annulled).build();
    }

}
