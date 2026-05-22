package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses deejay.de invoice PDFs.
 *
 * Real format of each item line (as exported by PDFBox/pymupdf):
 *   "C020 - Woody McBride- Basketball Heroes                10,29   2    20,58"
 *   "1111031 - Various / Ost- Pulp Fiction Soundtrack       22,29   1    22,29"
 *   "WARPLP30 - Aphex Twin- I Care Because You Do 2x12\"   32,49   1    32,49"
 *
 * Pattern: {catalogCode} " - " {artist} "- " {album} <spaces> {unitPrice} <spaces> {qty} <spaces> {sum}
 *
 * Notes:
 *  - The separator between artist and album is "- " (no leading space) which distinguishes it
 *    from the " - " (with spaces) that separates code from artist.
 *  - Catalog codes can contain uppercase letters, digits, hyphens, and dots.
 *  - Album titles may contain hyphens, slashes, apostrophes, parentheses, etc.
 *  - Prices use a comma decimal separator.
 */
@Slf4j
@Service
public class PdfInvoiceParser {

    /**
     * Strict pattern: code, " - ", artist, "- ", album, spaces, price, spaces, qty, spaces, sum.
     * Groups: 1 = catalog code, 2 = artist, 3 = album.
     */
    private static final Pattern ITEM_LINE = Pattern.compile(
        "^([A-Z0-9][A-Z0-9\\-\\.]{1,29})\\s+-\\s+(.+?)-\\s+(.+?)\\s{2,}\\d{1,4}[,.]\\d{2}\\s+\\d{1,4}\\s+\\d{1,4}[,.]\\d{2}\\s*$"
    );

    /**
     * Loose fallback for lines where columns were jammed together by PDFBox extraction.
     * Tries to find an item line by just requiring code + " - " + artist + "- " + album
     * followed by anything containing a price.
     */
    private static final Pattern ITEM_LINE_LOOSE = Pattern.compile(
        "^([A-Z0-9][A-Z0-9\\-\\.]{1,29})\\s+-\\s+(.+?)-\\s+(.+?)\\s+\\d{1,4}[,.]\\d{2}.*$"
    );

    public List<InvoiceItem> parse(MultipartFile file) throws IOException {
        List<InvoiceItem> items = new ArrayList<>();

        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);

            log.debug("PDF text extracted ({} chars). First 300: '{}'", text.length(),
                text.substring(0, Math.min(300, text.length())).replace("\n", "\\n"));

            String[] lines = text.split("\\r?\\n");
            for (String raw : lines) {
                String line = raw.strip();
                if (line.isEmpty()) continue;

                InvoiceItem item = tryParse(line);
                if (item != null) {
                    boolean alreadySeen = items.stream().anyMatch(i ->
                        i.codigoCatalogo().equalsIgnoreCase(item.codigoCatalogo())
                    );
                    if (!alreadySeen) {
                        items.add(item);
                        log.debug("Parsed item: {}", item);
                    }
                }
            }
        }

        log.info("PDF parsing complete. Unique items found: {}", items.size());
        return items;
    }

    private InvoiceItem tryParse(String line) {
        Matcher m = ITEM_LINE.matcher(line);
        if (m.matches()) {
            return new InvoiceItem(clean(m.group(1)), clean(m.group(2)), clean(m.group(3)));
        }
        Matcher ml = ITEM_LINE_LOOSE.matcher(line);
        if (ml.matches()) {
            return new InvoiceItem(clean(ml.group(1)), clean(ml.group(2)), clean(ml.group(3)));
        }
        return null;
    }

    private String clean(String s) {
        return s.strip().replaceAll("\\s+", " ");
    }
}
