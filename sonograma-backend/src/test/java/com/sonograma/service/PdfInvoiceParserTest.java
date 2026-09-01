package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.InvoiceParseResult;
import com.sonograma.dto.ParsedInvoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private byte[] buildPositionedTablePdf(PositionedRow... rows) throws Exception {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                writePositionedText(cs, font, 40, 740, "Description");
                writePositionedText(cs, font, 390, 740, "Unit Price");
                writePositionedText(cs, font, 460, 740, "Quantity");
                writePositionedText(cs, font, 520, 740, "Sum");

                float y = 715;
                for (PositionedRow row : rows) {
                    writePositionedText(cs, font, 40, y, row.description());
                    writePositionedText(cs, font, row.unitPriceX(), y, row.unitPrice());
                    writePositionedText(cs, font, 475, y, row.quantity());
                    writePositionedText(cs, font, 530, y, row.lineTotal());
                    y -= 20;
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void writePositionedText(PDPageContentStream cs, PDType1Font font,
                                     float x, float y, String text) throws Exception {
        cs.beginText();
        cs.setFont(font, 9);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private record PositionedRow(String description, String unitPrice, String quantity,
                                 String lineTotal, float unitPriceX) {}

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

    @Test
    void keepsLineWhenPrintedTotalIsInconsistentAndUsesUnitPriceTimesQuantity() throws Exception {
        byte[] pdf = buildPdf(
            "DUP01 - Artist- Repeated Release     10,00   2   0,00\n" +
            "DUP01 - Artist- Repeated Release     10,00   1   10,00"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);

        assertEquals(2, invoice.items().size());
        assertEquals(new BigDecimal("20.00"), invoice.items().get(0).subtotal());
        assertEquals(new BigDecimal("10.00"), invoice.items().get(1).subtotal());
    }

    // ── Test 2: multi-page — all item lines preserved in order, summary row

    @Test
    void parsesAllPagesAndPreservesRepeatedLinesInOrder() throws Exception {
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
        assertEquals(4, invoice.items().size());
        assertEquals("AV004", invoice.items().get(0).codigoCatalogo());
        assertEquals(2, invoice.items().get(0).cantidad());
        assertEquals("AV004", invoice.items().get(1).codigoCatalogo());
        assertEquals(1, invoice.items().get(1).cantidad());

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
        assertEquals("2x12", double12.formato());
        assertEquals("I Care Because You Do", double12.album());

        InvoiceItem doubleLp = invoice.items().stream()
            .filter(i -> "ZEN001".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals("2xLP", doubleLp.formato());
        assertEquals("Compilation", doubleLp.album());

        InvoiceItem single = invoice.items().stream()
            .filter(i -> "NRM01".equals(i.codigoCatalogo())).findFirst().orElseThrow();
        assertEquals("", single.formato());
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

    @Test
    void extractsInvoiceMetadataAndRawText() throws Exception {
        byte[] pdf = buildPdf(
            "deejay.de\n" +
            "Invoice No.: INV-42\n" +
            "Invoice Date: 12.06.2026\n" +
            "Shipping Method: DHL Express\n" +
            "Payment Method: Credit Card\n" +
            "Total Weight: 1,25 kg\n" +
            "Currency: EUR\n" +
            "Terms of Sale: DAP\n" +
            "Customs Tariff Number: 8523.80.90\n" +
            "EORI Number: DE123456\n" +
            "A001 - Artist- Album     10,00   1   10,00"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);

        assertEquals("INV-42", invoice.numeroFactura());
        assertEquals(java.time.LocalDate.of(2026, 6, 12), invoice.fechaFactura());
        assertEquals("deejay.de", invoice.proveedor());
        assertEquals("DHL Express", invoice.envio());
        assertEquals("Credit Card", invoice.pago());
        assertEquals(new BigDecimal("1.25"), invoice.pesoTotalKg());
        assertEquals("kg", invoice.unidadPeso());
        assertEquals("EUR", invoice.moneda());
        assertEquals("DAP", invoice.terminosVenta());
        assertEquals("8523.80.90", invoice.codigoArancel());
        assertEquals("DE123456", invoice.eoriNo());
        assertTrue(invoice.rawExtractText().contains("Invoice No.: INV-42"));
    }

    @Test
    void parsesSampleDeejayInvoiceRowsAcrossTwoPagesStrictly() throws Exception {
        byte[] pdf = buildPdf(
            "deejay.de GmbH & Co. KG\n" +
            "Invoice No.: 0031-188471\n" +
            "Date: 30.06.2026\n" +
            "Currency: EUR\n" +
            "Page: 1\n" +
            "Description Unit Price Quantity Sum\n" +
            "AOP010 - Krijka- E-Tribes EP     11,39   2   22,78\n" +
            "COMMUNIQUE011 - Invisible- The Next EP     10,49   1   10,49\n" +
            "DBH-011 - Basic Bastard- Basic Bastard Vol. 3     10,49   1   10,49\n" +
            "DE-338 - Hackney Electronica- Synaptic Shadows     12,99   3   38,97\n" +
            "DHS999 - Dimensional Holofonic Sound aka Dhs- Holofonic Cuts (reissue)     15,29   5   76,45\n" +
            "ED012 - djfix & Jek- unknown species     13,49   2   26,98\n" +
            "GND054 - DJ Garth / Eti- Twenty Minutes Of Disco Glory (30th Anniversary reissue)     14,99   2   29,98\n" +
            "GND054 - DJ Garth / Eti- Twenty Minutes Of Disco Glory (30th Anniversary reissue)     14,99   1   14,99\n" +
            "HYPER02 - Caim- Dream Ritual EP     10,99   2   21,98\n" +
            "MELCURE014 - Droxal- Spore Symphony EP     11,49   2   22,98\n" +
            "MS05 - Chris Carrier- Parallel Effect     22,49   2   44,98\n" +
            "NTCLASS006 - Orlando Voorn- Tronics     6,99   1   6,99\n" +
            "PLNK006 - Natural Goofy & TC80- No Plan EP     10,99   1   10,99\n" +
            "RX15 - BASIC BASTARD- DETROIT EP     11,49   2   22,98\n" +
            "SMI-021 - SYT- Echo System / School Of Thought     14,49   2   28,98\n" +
            "note next page",
            "Page: 2\n" +
            "Description Unit Price Quantity Sum\n" +
            "SMI-021 - SYT- Echo System / School Of Thought     14,49   2   28,98\n" +
            "TM029 - Jane Fitz, David Fogarty- Mysterious Vastness     22,89   1   22,89\n" +
            "TRANS1006 - Señor Coconut- El Baile Alemán     24,29   2   48,58\n" +
            "USR038 - Various- Various VII     11,09   2   22,18\n" +
            "UTS16V - Lee Burton- Sinewaves     13,99   1   13,99\n" +
            "VISLP001R - Random Factor- Too Fast Into The Future LP 2x12\"     26,79   1   26,79\n" +
            "WRECKS303 - Various- WRECKS303     11,49   2   22,98\n" +
            "ZAZù002 - Witness of Venus- Lost & Found LP     11,29   5   56,45\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "45  158,15  40,17  832,17  0,00  0,00  832,17\n" +
            "Shipper's Signature\n" +
            "EORI-No.: DE123456"
        );

        ParsedInvoice invoice = parser.parseInvoice(pdf);

        assertEquals(23, invoice.items().size());
        assertEquals(45, invoice.items().stream().mapToInt(InvoiceItem::cantidad).sum());
        assertEquals(Integer.valueOf(45), invoice.cantidadTotalPdf());
        assertEquals("EUR", invoice.moneda());
        assertEquals("0031-188471", invoice.numeroFactura());

        InvoiceItem first = invoice.items().getFirst();
        assertEquals("AOP010", first.codigoCatalogo());
        assertEquals(new BigDecimal("11.39"), first.precioUnitario());
        assertEquals(new BigDecimal("22.78"), first.subtotal());

        int gndQty = invoice.items().stream()
            .filter(item -> "GND054".equals(item.codigoCatalogo()))
            .mapToInt(InvoiceItem::cantidad)
            .sum();
        int smiQty = invoice.items().stream()
            .filter(item -> "SMI-021".equals(item.codigoCatalogo()))
            .mapToInt(InvoiceItem::cantidad)
            .sum();
        assertEquals(3, gndQty);
        assertEquals(4, smiQty);

        InvoiceItem unicode = invoice.items().stream()
            .filter(item -> "TRANS1006".equals(item.codigoCatalogo()))
            .findFirst()
            .orElseThrow();
        assertEquals("Señor Coconut", unicode.artista());
        assertEquals("El Baile Alemán", unicode.album());

        InvoiceItem doubleFormat = invoice.items().stream()
            .filter(item -> "VISLP001R".equals(item.codigoCatalogo()))
            .findFirst()
            .orElseThrow();
        assertEquals("2x12\"", doubleFormat.formato());
        assertEquals("Too Fast Into The Future LP", doubleFormat.album());
        assertTrue(invoice.items().stream().noneMatch(item -> "Postage".equalsIgnoreCase(item.codigoCatalogo())));
    }

    @Test
    void preservesUnparseableCandidateWithPageTextAndSpanishReason() throws Exception {
        byte[] pdf = buildPdf(
            "Invoice No.: INV-ERROR\n" +
            "OK01 - Artista- Título     10,00   1   10,00",
            "BAD02 - Otro Artista- Otro Título     12,00   X   12,00\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "2  0,00  0,00  22,00  0,00  0,00  22,00"
        );

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);

        assertEquals(1, result.invoice().items().size());
        assertEquals(2, result.sourceRows().size());
        assertEquals("REVIEW_REQUIRED", result.sourceRows().get(1).status());
        assertEquals(2, result.sourceRows().get(1).pageNumber());
        assertTrue(result.sourceRows().get(1).sourceText().contains("BAD02"));
        assertEquals("No se pudo determinar la cantidad.", result.sourceRows().get(1).reason());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("declarada (2)")));
    }

    @Test
    void positionalFallbackParsesMissingWhitespaceBetweenFormatAndUnitPrice() throws Exception {
        byte[] pdf = buildPositionedTablePdf(new PositionedRow(
            "GEN22611 - LONG ARTIST- SYNTHESIZING TEN PATTERNS TO A DANCE BEAT LP 2x12\"",
            "48,79", "2", "97,58", 418
        ));

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);

        assertTrue(result.sourceRows().getFirst().sourceText().contains("2x12\"48,79 2 97,58"));
        assertEquals(1, result.invoice().items().size());
        InvoiceItem item = result.invoice().items().getFirst();
        assertEquals("GEN22611", item.codigoCatalogo());
        assertEquals("LONG ARTIST", item.artista());
        assertEquals("SYNTHESIZING TEN PATTERNS TO A DANCE BEAT LP", item.album());
        assertEquals("2x12\"", item.formato());
        assertEquals(new BigDecimal("48.79"), item.precioUnitario());
        assertEquals(2, item.cantidad());
        assertEquals("PARSED", result.sourceRows().getFirst().status());
    }

    @Test
    void positionalFallbackRemovesOverlappingPriceGlyphsFromLongDescription() throws Exception {
        byte[] pdf = buildPositionedTablePdf(new PositionedRow(
            "LONGBOX77 - Long Artist Collective- A Very Long Anniversary Collection With Restored Mixes And Bonus Material Box Edition (3x12\")",
            "50,99", "1", "50,99", 418
        ));

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);

        InvoiceItem item = result.invoice().items().getFirst();
        assertEquals("LONGBOX77", item.codigoCatalogo());
        assertEquals("Long Artist Collective", item.artista());
        assertEquals("A Very Long Anniversary Collection With Restored Mixes And Bonus Material Box Edition (3x12\")",
            item.album());
        assertFalse(item.album().contains("50,99"));
        assertFalse(item.album().contains("5102,9"));
        assertFalse(result.sourceRows().getFirst().sourceText()
            .contains("(3x12\") 50,99 1 50,99"));
    }

    @Test
    void candidateWithOnlyFlattenedLineTotalUsesPositionalUnitPrice() throws Exception {
        byte[] pdf = buildPositionedTablePdf(new PositionedRow(
            "OVERLAP88 - Another Artist Group- Description Extending Across The Unit Price Column Friends EP",
            "10,49", "1", "10,49", 418
        ));

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);
        String flattened = result.sourceRows().getFirst().sourceText();
        java.util.regex.Matcher money = java.util.regex.Pattern.compile("\\d+[,.]\\d{2}").matcher(flattened);
        int moneyTokens = 0;
        while (money.find()) moneyTokens++;

        assertEquals(1, moneyTokens);
        assertEquals(1, result.invoice().items().size());
        assertEquals(new BigDecimal("10.49"), result.invoice().items().getFirst().precioUnitario());
        assertEquals("Description Extending Across The Unit Price Column Friends EP",
            result.invoice().items().getFirst().album());
    }

    @Test
    void positionalFallbackRequiresArithmeticConsistency() throws Exception {
        byte[] pdf = buildPositionedTablePdf(new PositionedRow(
            "ARITHMETIC9 - Generic Artist- A Description Long Enough To Overlap The Price Column With Additional Words Beyond It",
            "10,00", "2", "20,00", 418
        ));

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);

        assertEquals(1, result.invoice().items().size());
        assertEquals(new BigDecimal("20.00"), result.invoice().items().getFirst().subtotal());
        assertEquals("PARSED", result.sourceRows().getFirst().status());
    }

    @Test
    void ambiguousPositionalRecoveryRemainsReviewRequired() throws Exception {
        byte[] pdf = buildPositionedTablePdf(new PositionedRow(
            "AMBIGUOUS9 - Generic Artist- A Description Long Enough To Overlap The Price Column With Additional Words Beyond It",
            "10,00", "2", "25,00", 418
        ));

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);

        assertTrue(result.invoice().items().isEmpty());
        assertEquals(1, result.sourceRows().size());
        assertEquals("REVIEW_REQUIRED", result.sourceRows().getFirst().status());
        assertEquals("El precio por la cantidad no coincide con el total de la línea recuperada.",
            result.sourceRows().getFirst().reason());
    }

    @Test
    void simpleProductRowsContinueThroughOriginalRegexParser() throws Exception {
        byte[] pdf = buildPdf("SIMPLE77 - Artist- Uncomplicated Release     12,50   2   0,00");

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);

        assertEquals(1, result.invoice().items().size());
        assertEquals(new BigDecimal("25.00"), result.invoice().items().getFirst().subtotal());
        assertEquals("PARSED", result.sourceRows().getFirst().status());
    }

    @Test
    void realVinylFutureRegressionPdfReconcilesAllPhysicalCopies() throws Exception {
        String defaultFixture = Path.of(
            System.getProperty("user.home"), "Downloads", "0036-188471.pdf"
        ).toString();
        Path fixture = Path.of(System.getProperty("vinylfuture.real-pdf", defaultFixture));
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(fixture),
            "Fixture real no disponible: " + fixture);

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(Files.readAllBytes(fixture));
        ParsedInvoice invoice = result.invoice();

        assertEquals(32, invoice.cantidadTotalPdf());
        assertEquals(32, invoice.items().stream().mapToInt(InvoiceItem::cantidad).sum());
        assertTrue(result.sourceRows().stream().allMatch(row -> "PARSED".equals(row.status())));
        assertTrue(result.errors().isEmpty());
        assertEquals(2, quantityFor(invoice, "LITA22611"));
        assertEquals(1, quantityFor(invoice, "K7046XXXLP"));
        assertEquals(1, quantityFor(invoice, "MMV004"));
        assertEquals(4, quantityFor(invoice, "OYSTER80"));
        assertEquals(3, quantityFor(invoice, "RCM101120LP"));
        assertEquals(2, quantityFor(invoice, "TOKO6"));

        InvoiceItem boxSet = invoice.items().stream()
            .filter(item -> "K7046XXXLP".equals(item.codigoCatalogo()))
            .findFirst().orElseThrow();
        assertEquals("DJ-Kicks Kruder & Dorfmeister (30th Anniversary Box) (3x12\")", boxSet.album());
        assertFalse(boxSet.album().contains("50,99"));

        InvoiceItem longEp = invoice.items().stream()
            .filter(item -> "MMV004".equals(item.codigoCatalogo()))
            .findFirst().orElseThrow();
        assertEquals("Manuel De Lorenzi & Friends Ep", longEp.album());
        assertFalse(longEp.album().contains("10,49"));
    }

    @Test
    void knownRowsFromInvoice0036188471KeepExactRepeatedQuantitiesWithoutInventingMissingRows() throws Exception {
        byte[] pdf = buildPdf(
            "Invoice No.: 0036-188471\n" +
            "OYSTER80 - Michelle- Unfailing Love     10,00   1   10,00\n" +
            "OYSTER80 - Michelle- Unfailing Love     10,00   2   20,00\n" +
            "OYSTER80 - Michelle- Unfailing Love     10,00   1   10,00\n" +
            "RCM101120LP - John Frusciante- The Empyrean     10,00   1   10,00\n" +
            "RCM101120LP - John Frusciante- The Empyrean     10,00   1   10,00",
            "RCM101120LP - John Frusciante- The Empyrean     10,00   1   10,00\n" +
            "TOKO6 - KLARKY CAT- GUMBO     10,00   1   10,00\n" +
            "TOKO6 - KLARKY CAT- GUMBO     10,00   1   10,00\n" +
            "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:\n" +
            "32  0,00  0,00  90,00  0,00  0,00  90,00"
        );

        InvoiceParseResult result = parser.parseInvoiceWithDiagnostics(pdf);
        ParsedInvoice invoice = result.invoice();

        assertEquals("0036-188471", invoice.numeroFactura());
        assertEquals(4, quantityFor(invoice, "OYSTER80"));
        assertEquals(3, quantityFor(invoice, "RCM101120LP"));
        assertEquals(2, quantityFor(invoice, "TOKO6"));
        assertEquals(9, invoice.items().stream().mapToInt(InvoiceItem::cantidad).sum());
        assertEquals(32, invoice.cantidadTotalPdf());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("(32)") && error.contains("(9)")));
    }

    private int quantityFor(ParsedInvoice invoice, String code) {
        return invoice.items().stream()
            .filter(item -> code.equals(item.codigoCatalogo()))
            .mapToInt(InvoiceItem::cantidad)
            .sum();
    }
}
