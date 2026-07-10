package com.sonograma.service;

import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.repository.PedidoRepository;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvoiceControlWorkbookServiceTest {

    @Test
    void fillsTemplateWithoutChangingStructure() throws Exception {
        PedidoRepository repository = mock(PedidoRepository.class);
        Pedido pedido = pedidoFixture();
        when(repository.findById(1L)).thenReturn(Optional.of(pedido));

        InvoiceControlWorkbookService service = new InvoiceControlWorkbookService(repository);
        var generated = service.generate(1L, templateFixture());

        assertEquals("INV-42_control.xlsx", generated.filename());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(generated.content()))) {
            var items = workbook.getSheet("Items");
            var summary = workbook.getSheet("Summary");
            var raw = workbook.getSheet("Raw Extract");

            assertTrue(items.getRow(5).getZeroHeight());
            assertEquals("Artist", items.getRow(1).getCell(6).getStringCellValue());
            assertEquals("Aphex Twin", items.getRow(2).getCell(6).getStringCellValue());
            assertEquals("I Care Because You Do", items.getRow(2).getCell(7).getStringCellValue());
            assertEquals("2x12", items.getRow(2).getCell(8).getStringCellValue());
            assertEquals("K3*L3", items.getRow(2).getCell(12).getCellFormula());
            assertEquals("IF(LEFT(I3,2)=\"2x\",\"Double\",\"Single\")",
                items.getRow(2).getCell(13).getCellFormula());
            assertEquals(CellType.FORMULA, items.getRow(2).getCell(18).getCellType());
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            for (int col = 12; col <= 18; col++) {
                assertNotEquals(CellType.ERROR, items.getRow(2).getCell(col).getCachedFormulaResultType());
            }
            assertEquals("Double", items.getRow(2).getCell(13).getStringCellValue());
            assertEquals(2805.957, items.getRow(2).getCell(18).getNumericCellValue(), 0.001);

            assertEquals("INV-42", summary.getRow(3).getCell(1).getStringCellValue());
            assertEquals(LocalDate.of(2026, 6, 12), summary.getRow(4).getCell(1).getLocalDateTimeCellValue().toLocalDate());
            assertEquals(49.5, summary.getRow(34).getCell(1).getNumericCellValue());
            assertEquals(5, summary.getRow(35).getCell(1).getNumericCellValue());
            assertEquals("SUM(Items!M3:M3)", summary.getRow(25).getCell(1).getCellFormula());
            assertEquals("SUM(Items!L3:L3)", summary.getRow(26).getCell(1).getCellFormula());

            assertEquals("invoice.pdf", raw.getRow(1).getCell(0).getStringCellValue());
            assertEquals("full raw text", raw.getRow(1).getCell(1).getStringCellValue());
        }
    }

    private Pedido pedidoFixture() {
        Pedido pedido = Pedido.builder()
            .idPedido(1L)
            .numeroFactura("INV-42")
            .fechaFactura(LocalDate.of(2026, 6, 12))
            .proveedor("deejay.de")
            .envio("DHL")
            .pago("Credit Card")
            .unidadPeso("kg")
            .moneda("EUR")
            .pesoTotalKg(new BigDecimal("1.250"))
            .terminosVenta("DAP")
            .codigoArancel("85238090")
            .eoriNo("DE123")
            .nombreArchivo("invoice.pdf")
            .textoExtraido("full raw text")
            .franqueo(new BigDecimal("12.50"))
            .tarifas(new BigDecimal("3.75"))
            .neto(new BigDecimal("48.74"))
            .cantidadTotalPdf(1)
            .build();
        PedidoItem item = PedidoItem.builder()
            .pedido(pedido)
            .codigo("WARPLP30")
            .artista("Aphex Twin")
            .titulo("I Care Because You Do")
            .formato("2x12")
            .precioUnitarioEur(new BigDecimal("32.49"))
            .cantidad(1)
            .totalLineaEur(new BigDecimal("32.49"))
            .build();
        pedido.setItems(List.of(item));
        return pedido;
    }

    private MockMultipartFile templateFixture() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var items = workbook.createSheet("Items");
            var header = items.createRow(1);
            header.createCell(6).setCellValue("Artist");
            header.createCell(7).setCellValue("Title");
            var templateRow = items.createRow(2);
            for (int col = 6; col <= 18; col++) templateRow.createCell(col);
            templateRow.getCell(12).setCellFormula("K3*L3");
            items.createRow(5).setZeroHeight(true);

            var summary = workbook.createSheet("Summary");
            summary.createRow(25).createCell(0).setCellValue("Merchandise total");
            summary.getRow(25).createCell(1).setCellFormula("SUM(Items!M3:M99)");
            summary.createRow(26).createCell(0).setCellValue("Quantity check");
            summary.getRow(26).createCell(1).setCellFormula("SUM(Items!L3:L99)");

            var raw = workbook.createSheet("Raw Extract");
            raw.createRow(0).createCell(0).setCellValue("Source File");
            raw.getRow(0).createCell(1).setCellValue("Extracted Text");

            workbook.write(output);
            return new MockMultipartFile(
                "template", "template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                output.toByteArray());
        }
    }
}
