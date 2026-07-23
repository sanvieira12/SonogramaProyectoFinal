package com.sonograma.service;

import com.sonograma.entity.Cliente;
import com.sonograma.entity.Venta;
import com.sonograma.dto.VentaResponseDTO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExcelExportServiceTest {

    @Test
    void libroVentasExportaTotalFinalSinCostoDeEnvio() throws Exception {
        ExcelExportService service = new ExcelExportService(new ProfitCalculationService(
                mock(com.sonograma.repository.VentaRepository.class),
                mock(com.sonograma.repository.PedidoRepository.class),
                mock(com.sonograma.repository.PedidoItemRepository.class),
                mock(CatalogPricingService.class)));
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente");

        Venta venta = Venta.builder()
                .numeroFactura("F-2026-001")
                .fechaVenta(LocalDateTime.of(2026, 6, 15, 10, 0))
                .cliente(cliente)
                .precioVenta(new BigDecimal("3000"))
                .costoEnvio(new BigDecimal("250"))
                .totalFinal(new BigDecimal("3250"))
                .build();

        byte[] bytes = service.exportarLibroVentas(List.of(venta));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(10).getNumericCellValue()).isEqualTo(3000.00);
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(10).getNumericCellValue()).isEqualTo(3000.00);
        }
    }

    @Test
    void libroMovimientosReemplazaMedioPagoPorGananciaNetaYNoAsignaGananciaAPagos() throws Exception {
        ExcelExportService service = new ExcelExportService(new ProfitCalculationService(
                mock(com.sonograma.repository.VentaRepository.class),
                mock(com.sonograma.repository.PedidoRepository.class),
                mock(com.sonograma.repository.PedidoItemRepository.class),
                mock(CatalogPricingService.class)));
        VentaResponseDTO venta = VentaResponseDTO.builder()
                .tipoMovimiento("VENTA")
                .descripcionMovimiento("Venta")
                .gananciaNeta(new BigDecimal("350"))
                .montoMovimiento(new BigDecimal("1000"))
                .build();
        VentaResponseDTO pago = VentaResponseDTO.builder()
                .tipoMovimiento("PAGO_DEUDA")
                .descripcionMovimiento("Pago de deuda")
                .montoMovimiento(new BigDecimal("250"))
                .build();

        byte[] bytes = service.exportarLibroMovimientos(List.of(venta, pago));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(7).getStringCellValue()).isEqualTo("Ganancia neta");
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(350.00);
            assertThat(sheet.getRow(2).getCell(7).getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
            assertThat(sheet.getRow(4).getCell(7).getNumericCellValue()).isEqualTo(350.00);
        }
    }
}
