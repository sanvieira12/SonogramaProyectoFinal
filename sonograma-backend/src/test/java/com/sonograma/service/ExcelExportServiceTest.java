package com.sonograma.service;

import com.sonograma.entity.Cliente;
import com.sonograma.entity.Venta;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelExportServiceTest {

    @Test
    void libroVentasExportaTotalFinalSinCostoDeEnvio() throws Exception {
        ExcelExportService service = new ExcelExportService();
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
}
