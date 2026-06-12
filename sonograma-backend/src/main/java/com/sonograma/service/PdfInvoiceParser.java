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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses deejay.de / VinylFuture invoice PDFs (all pages).
 *
 * Item line format:
 *   "{code} - {artist}- {album}    {unitPrice}   {qty}   {lineTotal}"
 *
 * Summary row (last page):
 *   "Quantity  Postage:  Fees:  Net:  VAT. 7%:  VAT. 19%:  Total:"
 *   "{qty}  {postage}  {fees}  {net}  {vat7}  {vat19}  {total}"
 */
@Slf4j
@Service
public class PdfInvoiceParser {

    private static final Pattern ITEM_LINE = Pattern.compile(
        "^(.{2,40}?)\\s+-\\s+(.+?)-\\s+(.+?)\\s{2,}([\\d.,]+)\\s+(\\d{1,4})\\s+([\\d.,]+)\\s*$"
    );

    private static final Pattern ITEM_LINE_LOOSE = Pattern.compile(
        "^(.{2,40}?)\\s+-\\s+(.+?)-\\s+(.+?)\\s+([\\d.,]+)\\s+(\\d{1,4})\\s+([\\d.,]+).*$"
    );

    private static final Pattern TOTAL_LINE = Pattern.compile(
        "(?i)^.*?\\b(?:grand\\s+total|invoice\\s+total|total)\\b[^\\d]*([\\d.,]+)\\s*(?:EUR|€)?\\s*$"
    );

    private static final Pattern MONEY_TOKEN = Pattern.compile("\\d+[,.]\\d{2}");

    private static final Pattern FORMAT_DOUBLE = Pattern.compile(
        "(?i)(?<!\\w)(2x(?:\\s*[\\p{L}\\p{N}]+)?(?:[\"”″])?(?:\\s+Box)?)"
    );

    // Header field patterns (best-effort)
    private static final Pattern INVOICE_NO = Pattern.compile(
        "(?i)invoice\\s*(?:no\\.?|number|#)[:\\s]+([A-Z0-9\\-]+)"
    );
    private static final Pattern INVOICE_DATE_LABEL = Pattern.compile(
        "(?i)(?:invoice\\s+)?date[:\\s]+([\\d]{1,2}[./\\-][\\d]{1,2}[./\\-][\\d]{2,4})"
    );
    private static final Pattern PESO = Pattern.compile(
        "(?i)(?:total\\s+)?weight[:\\s]+([\\d.,]+)\\s*([a-zA-Z]+)"
    );
    private static final Pattern CUSTOMS_TARIFF = Pattern.compile(
        "(?i)customs\\s+tariff[\\s\\w]*:[\\s]+([\\d.]+)"
    );
    private static final Pattern EORI = Pattern.compile(
        "(?i)eori[\\s\\w]*:[\\s]+([A-Z0-9]+)"
    );
    private static final Pattern PAYMENT_METHOD = Pattern.compile(
        "(?i)payment[\\s\\w]*:[\\s]+(.+)"
    );
    private static final Pattern SHIPPING_METHOD = Pattern.compile(
        "(?im)^(?:shipping|shipment)(?:\\s+method)?\\s*:\\s*(.+)$|^delivery\\s+method\\s*:\\s*(.+)$"
    );
    private static final Pattern TERMS_OF_SALE = Pattern.compile(
        "(?i)terms\\s+of\\s+(?:sale|delivery)[:\\s]+(.+)"
    );
    private static final Pattern CURRENCY = Pattern.compile(
        "(?i)currency\\s*:\\s*([A-Z]{3})"
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
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

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);

            log.debug("PDF text ({} chars). First 400: '{}'", text.length(),
                text.substring(0, Math.min(400, text.length())).replace("\n", "\\n"));

            String[] lines = text.split("\\R");

            for (String raw : lines) {
                String line = raw.strip();
                if (line.isEmpty()) continue;
                InvoiceItem item = tryParse(line);
                if (item != null) {
                    items.add(item);
                }
            }

            SummaryData summary = extractSummary(lines);
            HeaderData header = extractHeader(text);

            for (PDPage page : doc.getPages()) {
                for (PDAnnotation ann : page.getAnnotations()) {
                    if (ann instanceof PDAnnotationLink link) {
                        PDAction action = link.getAction();
                        if (action instanceof PDActionURI uriAction) {
                            String url = uriAction.getURI();
                            if (url != null && !url.isBlank()) urls.add(url.strip());
                        }
                    }
                }
            }

            BigDecimal total = summary != null ? summary.total() : parseSummaryTotal(lines);

            log.info("PDF parsed: {} items, links={}, total={}, qty={}",
                items.size(), urls.size(), total,
                summary != null ? summary.cantidadTotal() : null);

            return new ParsedInvoice(
                items,
                List.copyOf(urls),
                total,
                summary != null ? summary.cantidadTotal() : null,
                summary != null ? summary.franqueo() : null,
                summary != null ? summary.tarifas() : null,
                summary != null ? summary.neto() : null,
                header.numeroFactura(),
                header.fechaFactura(),
                header.proveedor(),
                header.envio(),
                header.pago(),
                header.unidadPeso(),
                header.moneda(),
                header.pesoTotal(),
                header.terminosVenta(),
                header.codigoArancel(),
                header.eoriNo(),
                summary != null ? summary.iva() : null,
                text
            );
        }
    }

    public List<String> extractLinks(byte[] pdfBytes) throws IOException {
        return parseInvoice(pdfBytes).productLinks();
    }

    // ── Item parsing ──────────────────────────────────────────────────────────

    private InvoiceItem tryParse(String line) {
        Matcher m = ITEM_LINE.matcher(line);
        if (m.matches()) return buildItem(m);
        Matcher ml = ITEM_LINE_LOOSE.matcher(line);
        if (ml.matches()) return buildItem(ml);
        return null;
    }

    private InvoiceItem buildItem(Matcher matcher) {
        String descripcion = clean(matcher.group(3));
        String formato = detectFormato(descripcion);
        String album = removeFormato(descripcion, formato);
        return new InvoiceItem(
            clean(matcher.group(1)),
            clean(matcher.group(2)),
            album,
            formato,
            parseMoney(matcher.group(4)),
            Integer.valueOf(matcher.group(5)),
            parseMoney(matcher.group(6))
        );
    }

    private String detectFormato(String album) {
        if (album == null) return "";
        Matcher matcher = FORMAT_DOUBLE.matcher(album);
        return matcher.find() ? clean(matcher.group(1)) : "";
    }

    private String removeFormato(String descripcion, String formato) {
        if (descripcion == null || formato == null || formato.isBlank()) return descripcion;
        return clean(descripcion.replaceFirst("(?i)\\Q" + formato + "\\E", ""));
    }

    // ── Summary row extraction ────────────────────────────────────────────────

    private record SummaryData(Integer cantidadTotal, BigDecimal franqueo,
                               BigDecimal tarifas, BigDecimal neto, BigDecimal iva,
                               BigDecimal total) {}

    /**
     * Finds a row with labels "Quantity … Postage … Fees … Net … Total"
     * then reads the next non-empty row for numeric values:
     * qty postage fees net vat7 vat19 total
     */
    private SummaryData extractSummary(String[] lines) {
        for (int i = 0; i < lines.length - 1; i++) {
            String lower = lines[i].strip().toLowerCase(Locale.ROOT);
            if (lower.contains("quantity") && lower.contains("postage")
                    && lower.contains("fees") && lower.contains("net")
                    && lower.contains("total")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String val = lines[j].strip();
                    if (val.isEmpty()) continue;
                    String[] parts = val.split("\\s+");
                    if (parts.length >= 4) {
                        try {
                            int qty = Integer.parseInt(parts[0]);
                            BigDecimal franqueo = parseMoney(parts[1]);
                            BigDecimal tarifas  = parseMoney(parts[2]);
                            BigDecimal neto     = parseMoney(parts[3]);
                            BigDecimal iva = BigDecimal.ZERO;
                            for (int k = 4; k < parts.length - 1; k++) {
                                BigDecimal value = parseMoney(parts[k]);
                                if (value != null) iva = iva.add(value);
                            }
                            BigDecimal total    = parts.length >= 7
                                ? parseMoney(parts[6])
                                : parseMoney(parts[parts.length - 1]);
                            return new SummaryData(qty, franqueo, tarifas, neto, iva, total);
                        } catch (NumberFormatException e) {
                            log.debug("Línea de summary no parseable: {}", val);
                        }
                    }
                    break;
                }
            }
        }
        return null;
    }

    // ── Header extraction (best-effort) ──────────────────────────────────────

    private record HeaderData(String numeroFactura, LocalDate fechaFactura,
                              String proveedor, String envio, String pago,
                              BigDecimal pesoTotal, String unidadPeso, String moneda,
                              String terminosVenta, String codigoArancel, String eoriNo) {}

    private HeaderData extractHeader(String text) {
        String numeroFactura = firstMatch(INVOICE_NO, text);
        LocalDate fechaFactura = parseDate(firstMatch(INVOICE_DATE_LABEL, text));
        String proveedor = detectProveedor(text);
        String envio = firstMatchTrimmed(SHIPPING_METHOD, text);
        String pago = firstMatchTrimmed(PAYMENT_METHOD, text);
        Matcher pesoMatcher = PESO.matcher(text);
        BigDecimal pesoTotal = null;
        String unidadPeso = null;
        if (pesoMatcher.find()) {
            pesoTotal = parseDecimal(pesoMatcher.group(1));
            unidadPeso = pesoMatcher.group(2).strip();
        }
        String moneda = firstMatch(CURRENCY, text);
        if (moneda == null && (text.contains(" EUR") || text.contains("EUR "))) moneda = "EUR";
        String terminosVenta = firstMatchTrimmed(TERMS_OF_SALE, text);
        String codigoArancel = firstMatch(CUSTOMS_TARIFF, text);
        String eoriNo = firstMatch(EORI, text);
        return new HeaderData(numeroFactura, fechaFactura, proveedor, envio, pago,
            pesoTotal, unidadPeso, moneda, terminosVenta, codigoArancel, eoriNo);
    }

    private String detectProveedor(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("vinylfuture") || lower.contains("vinyl future")) return "Vinyl Future";
        if (lower.contains("deejay.de")) return "deejay.de";
        return null;
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        for (int group = 1; group <= m.groupCount(); group++) {
            if (m.group(group) != null) return m.group(group).strip();
        }
        return null;
    }

    private String firstMatchTrimmed(Pattern pattern, String text) {
        String val = firstMatch(pattern, text);
        if (val == null) return null;
        // Trim at newline or excessive whitespace
        int newline = val.indexOf('\n');
        return newline >= 0 ? val.substring(0, newline).strip() : val;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.strip(), fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private BigDecimal parseDecimal(String raw) {
        if (raw == null) return null;
        try {
            return new BigDecimal(raw.strip().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Fallback total extraction ─────────────────────────────────────────────

    private BigDecimal parseSummaryTotal(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            Matcher totalMatcher = TOTAL_LINE.matcher(lines[i].strip());
            if (totalMatcher.matches()) return parseMoney(totalMatcher.group(1));
        }
        for (int i = lines.length - 1; i >= 1; i--) {
            String current = lines[i].strip();
            if (current.isEmpty()) continue;
            String previous = lines[i - 1].strip().toLowerCase(Locale.ROOT);
            if (!previous.contains("total")) continue;
            Matcher matcher = MONEY_TOKEN.matcher(current);
            BigDecimal lastAmount = null;
            while (matcher.find()) lastAmount = parseMoney(matcher.group());
            if (lastAmount != null) return lastAmount;
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal parseMoney(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.strip().replace(" ", "");
        int comma = normalized.lastIndexOf(',');
        int dot   = normalized.lastIndexOf('.');
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
        return s == null ? null : s.strip().replace(' ', ' ').replaceAll("\\s+", " ");
    }
}
