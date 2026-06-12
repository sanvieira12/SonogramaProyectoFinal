package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PdfInvoiceParserTest {

    private final PdfInvoiceParser parser = new PdfInvoiceParser();

    // ── helpers ───────────────────────────────────────────────────────────────

    private byte[] buildPdf(String... pages) throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String pageText : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    float y = 740;
                    cs.newLineAtOffset(40, y);
                    for (String line : pageText.split("\n")) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -18);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ── Test 1: single page – prices, quantities, invoice total ──────────────

    @Test
    void parsesItemPricesQuantitiesAndInvoiceTotal() throws Exception {
        byte[] pdf = buildPdf(
            "C020 - Woody McBride- Basketball Heroes     10,29   2   20,58\n" +
            "Invoice Total: 20,58 EUR"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);
        assertEquals(1, invoice.items().size());

        InvoiceItem item = invoice.items().getFirst();
        assertEquals("C020", item.codigoCatalogo());
        assertEquals(new BigDecimal("10.29"), item.precioUnitario());
        assertEquals(2, item.cantidad());
        assertEquals(new BigDecimal("20.58"), item.subtotal());
        assertEquals(new BigDecimal("20.58"), invoice.total());
    }

    // ── Test 2: multi-page — all items included, merged duplicates, summary row

    @Test
    void parsesAllPagesAndMergesRepeatedCodes() throws Exception {
        byte[] pdf = buildPdf(
            // page 1
            "AV004 - ACE VISION- TRAVEL CONTINUUM EP     11,79   2   23,58\n" +
            "AV004 - ACE VISION- TRAVEL CONTINUUM EP     11,79   1   11,79\n" +
            "SOO TROIS - B.L. Underwood- Selected Works 96-97 LP     11,89   3   35,67",
            // page 2
            "ZAZu002 - Witness of Venus- Lost Found LP     11,29   5   56,45\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "49  157,93  40,40  803,94  0,00  0,00  803,94"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);

        // All items across pages are present
        assertEquals(3, invoice.items().size());

        // Merged AV004: 2+1 = 3 qty
        InvoiceItem merged = invoice.items().stream()
            .filter(i -> "AV004".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals(3, merged.cantidad());
        assertEquals(new BigDecimal("35.37"), merged.subtotal());
        assertEquals(new BigDecimal("11.79"), merged.precioUnitario());

        // Code with space works
        InvoiceItem spacedCode = invoice.items().stream()
            .filter(i -> "SOO TROIS".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals(3, spacedCode.cantidad());

        // Second-page item is included
        InvoiceItem secondPage = invoice.items().stream()
            .filter(i -> "ZAZu002".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("56.45"), secondPage.subtotal());

        // Summary row extracted
        assertEquals(new BigDecimal("803.94"), invoice.total());
        assertEquals(Integer.valueOf(49), invoice.cantidadTotalPdf());
        assertEquals(new BigDecimal("157.93"), invoice.franqueo());
        assertEquals(new BigDecimal("40.40"), invoice.tarifas());
        assertEquals(new BigDecimal("803.94"), invoice.neto());
    }

    // ── Test 3: SUM(item qty) matches cantidadTotalPdf ───────────────────────

    @Test
    void sumOfItemQuantitiesMatchesTotalFromSummaryRow() throws Exception {
        byte[] pdf = buildPdf(
            "A001 - Artist One- Album One     10,00   3   30,00\n" +
            "B002 - Artist Two- Album Two     12,00   2   24,00\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "5  15,00  5,00  54,00  0,00  0,00  54,00"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);

        int sumQty = invoice.items().stream().mapToInt(InvoiceItem::cantidad).sum();
        assertEquals(5, sumQty);
        assertEquals(Integer.valueOf(5), invoice.cantidadTotalPdf());
        assertEquals(sumQty, invoice.cantidadTotalPdf());
    }

    // ── Test 4: Single vs Double format detection ─────────────────────────────

    @Test
    void detectsDoubleFormatFromAlbumTitle() throws Exception {
        byte[] pdf = buildPdf(
            "WARPLP30 - Aphex Twin- I Care Because You Do 2x12     32,49   1   32,49\n" +
            "ZEN001 - Various- Compilation 2xLP     15,00   1   15,00\n" +
            "NRM01 - Normal Artist- Single Album LP     11,00   1   11,00\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "3  5,00  2,00  63,49  0,00  0,00  63,49"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);
        assertEquals(3, invoice.items().size());

        InvoiceItem double12 = invoice.items().stream()
            .filter(i -> "WARPLP30".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals("Double", double12.formato());

        InvoiceItem doubleLp = invoice.items().stream()
            .filter(i -> "ZEN001".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals("Double", doubleLp.formato());

        InvoiceItem single = invoice.items().stream()
            .filter(i -> "NRM01".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals("Single", single.formato());
    }

    // ── Test 5: postage / fees / net / total all come from summary row ────────

    @Test
    void extractsPostageFeesNetTotalFromSummaryRow() throws Exception {
        byte[] pdf = buildPdf(
            "X001 - DJ Sample- Test Track     20,00   2   40,00\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "2  12,50  3,75  56,25  0,00  0,00  56,25"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);

        assertEquals(new BigDecimal("12.50"), invoice.franqueo());
        assertEquals(new BigDecimal("3.75"),  invoice.tarifas());
        assertEquals(new BigDecimal("56.25"), invoice.neto());
        assertEquals(new BigDecimal("56.25"), invoice.total());
        assertEquals(Integer.valueOf(2), invoice.cantidadTotalPdf());
    }
}
