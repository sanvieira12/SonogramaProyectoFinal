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

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfInvoiceParserTest {

    private final PdfInvoiceParser parser = new PdfInvoiceParser();

    @Test
    void parsesItemPricesQuantitiesAndInvoiceTotal() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.newLineAtOffset(40, 740);
                content.showText("C020 - Woody McBride- Basketball Heroes     10,29   2   20,58");
                content.newLineAtOffset(0, -18);
                content.showText("Invoice Total: 20,58 EUR");
                content.endText();
            }
            document.save(output);
            pdf = output.toByteArray();
        }

        ParsedInvoice invoice = parser.parseInvoice(pdf);
        InvoiceItem item = invoice.items().getFirst();

        assertEquals(1, invoice.items().size());
        assertEquals("C020", item.codigoCatalogo());
        assertEquals(new BigDecimal("10.29"), item.precioUnitario());
        assertEquals(2, item.cantidad());
        assertEquals(new BigDecimal("20.58"), item.subtotal());
        assertEquals(new BigDecimal("20.58"), invoice.total());
    }
}
