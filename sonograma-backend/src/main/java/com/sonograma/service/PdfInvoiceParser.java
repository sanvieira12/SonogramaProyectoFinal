package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.ParsedInvoice;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
     * Groups: 1 = catalog code, 2 = artist, 3 = album,
     * 4 = unit price, 5 = quantity, 6 = subtotal.
     */
    private static final Pattern ITEM_LINE = Pattern.compile(
        "^([A-Z0-9][A-Z0-9\\-\\.]{1,29})\\s+-\\s+(.+?)-\\s+(.+?)\\s{2,}([\\d.,]+)\\s+(\\d{1,4})\\s+([\\d.,]+)\\s*$"
    );

    /**
     * Loose fallback for lines where columns were jammed together by PDFBox extraction.
     * Tries to find an item line by just requiring code + " - " + artist + "- " + album
     * followed by anything containing a price.
     */
    private static final Pattern ITEM_LINE_LOOSE = Pattern.compile(
        "^([A-Z0-9][A-Z0-9\\-\\.]{1,29})\\s+-\\s+(.+?)-\\s+(.+?)\\s+([\\d.,]+)\\s+(\\d{1,4})\\s+([\\d.,]+).*$"
    );

    private static final Pattern TOTAL_LINE = Pattern.compile(
        "(?i)^.*?\\b(?:grand\\s+total|invoice\\s+total|total)\\b[^\\d]*([\\d.,]+)\\s*(?:EUR|€)?\\s*$"
    );

    public List<InvoiceItem> parse(MultipartFile file) throws IOException {
        return parse(file.getBytes());
    }

    public List<InvoiceItem> parse(byte[] pdfBytes) throws IOException {
        return parseInvoice(pdfBytes).items();
    }

    public ParsedInvoice parseInvoice(byte[] pdfBytes) throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        BigDecimal total = null;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);

            log.debug("PDF text extracted ({} chars). First 300: '{}'", text.length(),
                text.substring(0, Math.min(300, text.length())).replace("\n", "\\n"));

            String[] lines = text.split("\\R");
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

            for (int i = lines.length - 1; i >= 0 && total == null; i--) {
                Matcher totalMatcher = TOTAL_LINE.matcher(lines[i].strip());
                if (totalMatcher.matches()) {
                    total = parseMoney(totalMatcher.group(1));
                }
            }

            for (PDPage page : doc.getPages()) {
                for (PDAnnotation ann : page.getAnnotations()) {
                    if (ann instanceof PDAnnotationLink link) {
                        PDAction action = link.getAction();
                        if (action instanceof PDActionURI uriAction) {
                            String url = uriAction.getURI();
                            if (url != null && !url.isBlank()) {
                                urls.add(url.strip());
                            }
                        }
                    }
                }
            }
        }

        log.info("PDF parsing complete. Unique items: {}, links: {}, total: {}",
            items.size(), urls.size(), total);
        return new ParsedInvoice(items, List.copyOf(urls), total);
    }

    /**
     * Extracts all HTTP/HTTPS hyperlinks embedded as annotation links in the PDF,
     * in page order. One link per product line is the expected deejay.de layout.
     */
    public List<String> extractLinks(byte[] pdfBytes) throws IOException {
        return parseInvoice(pdfBytes).productLinks();
    }

    private InvoiceItem tryParse(String line) {
        Matcher m = ITEM_LINE.matcher(line);
        if (m.matches()) {
            return buildItem(m);
        }
        Matcher ml = ITEM_LINE_LOOSE.matcher(line);
        if (ml.matches()) {
            return buildItem(ml);
        }
        return null;
    }

    private InvoiceItem buildItem(Matcher matcher) {
        return new InvoiceItem(
            clean(matcher.group(1)),
            clean(matcher.group(2)),
            clean(matcher.group(3)),
            parseMoney(matcher.group(4)),
            Integer.valueOf(matcher.group(5)),
            parseMoney(matcher.group(6))
        );
    }

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.strip().replace(" ", "");
        int comma = normalized.lastIndexOf(',');
        int dot = normalized.lastIndexOf('.');
        if (comma > dot) {
            normalized = normalized.replace(".", "").replace(',', '.');
        } else if (dot > comma) {
            normalized = normalized.replace(",", "");
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            log.debug("No se pudo interpretar importe '{}'", raw);
            return null;
        }
    }

    private String clean(String s) {
        return s.strip().replaceAll("\\s+", " ");
    }
}
