package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.InvoiceParseResult;
import com.sonograma.dto.InvoiceSourceRowDTO;
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
import org.apache.pdfbox.text.TextPosition;
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

    private static final Pattern DESCRIPTION_FIELDS = Pattern.compile(
        "^(.{2,40}?)\\s+-\\s+(.+?)-\\s+(.+?)\\s*$"
    );

    private static final Pattern PRODUCT_PREFIX = Pattern.compile(
        "^([\\p{L}\\p{N}][\\p{L}\\p{N}._/+#'&() \\-]{1,39})\\s+-\\s+(.+)$"
    );

    private static final Pattern PRODUCT_TAIL = Pattern.compile(
        ".*\\s+(\\d{1,4})\\s+(\\d+[,.]\\d{2})\\s*$"
    );

    private static final BigDecimal LINE_TOTAL_TOLERANCE = new BigDecimal("0.02");

    private static final Pattern TOTAL_LINE = Pattern.compile(
        "(?i)^.*?\\b(?:grand\\s+total|invoice\\s+total|total)\\b[^\\d]*([\\d.,]+)\\s*(?:EUR|€)?\\s*$"
    );

    private static final Pattern MONEY_TOKEN = Pattern.compile("\\d+[,.]\\d{2}");

    private static final Pattern MONEY_VALUE = Pattern.compile("^\\d+[,.]\\d{2}$");

    private static final Pattern MONEY_SUFFIX = Pattern.compile("(\\d+[,.]\\d{2})\\s*$");

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
    private static final Pattern RECIPIENT = Pattern.compile(
        "(?im)^recipient\\s*:\\s*(.+)$"
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

    private static final List<String> IGNORED_PREFIXES = List.of(
        "invoice no", "date:", "invoice date", "page:", "from", "recipient", "shipping:",
        "shipping method:", "payment:", "payment method:", "total weight:", "currency:",
        "terms of sale:", "customs tariff", "eori", "description unit price quantity sum",
        "note next page", "quantity", "postage:", "fees:", "net:", "vat.", "total:",
        "shipper's signature", "bank", "iban", "bic", "ust-id", "commercial register"
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
        return parseInvoiceWithDiagnostics(pdfBytes).invoice();
    }

    /**
     * Parses the invoice while preserving every line that looks like a product row.
     * The legacy {@link #parseInvoice(byte[])} method delegates here so existing
     * callers keep the same successfully parsed item behavior.
     */
    public InvoiceParseResult parseInvoiceWithDiagnostics(byte[] pdfBytes) throws IOException {
        List<InvoiceItem> items = new ArrayList<>();
        List<InvoiceSourceRowDTO> sourceRows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            StringBuilder fullText = new StringBuilder();
            boolean inSummary = false;
            int sourceRowNumber = 0;
            for (int pageNumber = 1; pageNumber <= doc.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String pageText = stripper.getText(doc);
                PageLayout pageLayout = null;
                List<String> productLinesBeforeFallback = new ArrayList<>();
                if (!fullText.isEmpty() && !pageText.startsWith("\n")) fullText.append('\n');
                fullText.append(pageText);
                for (String raw : pageText.split("\\R")) {
                    String line = raw.strip();
                    if (line.isEmpty()) continue;
                    if (isSummaryHeader(line)) {
                        inSummary = true;
                        continue;
                    }
                    if (inSummary || shouldIgnoreLine(line)) continue;

                    ParseAttempt attempt = tryParseDetailed(line);
                    if (attempt.item() != null) {
                        if (pageLayout == null) {
                            productLinesBeforeFallback.add(line);
                        } else {
                            pageLayout.claim(line);
                        }
                        sourceRowNumber++;
                        items.add(attempt.item());
                        sourceRows.add(new InvoiceSourceRowDTO(
                            sourceRowNumber, pageNumber, line, "PARSED",
                            attempt.item().cantidad(), null, attempt.item()
                        ));
                    } else if (hasProductStructure(line)) {
                        if (pageLayout == null) {
                            pageLayout = extractPageLayout(doc, pageNumber);
                            for (String previousProductLine : productLinesBeforeFallback) {
                                pageLayout.claim(previousProductLine);
                            }
                        }
                        PositionedProductRow positionedRow = pageLayout.claim(line);
                        if (!looksLikeProductRow(line) && positionedRow == null) continue;
                        ParseAttempt positionalAttempt = tryParsePositionally(positionedRow);
                        if (positionalAttempt.item() != null) {
                            sourceRowNumber++;
                            items.add(positionalAttempt.item());
                            sourceRows.add(new InvoiceSourceRowDTO(
                                sourceRowNumber, pageNumber, line, "PARSED",
                                positionalAttempt.item().cantidad(), null, positionalAttempt.item()
                            ));
                            continue;
                        }
                        sourceRowNumber++;
                        String reason = positionedRow != null && positionalAttempt.reason() != null
                            ? positionalAttempt.reason()
                            : attempt.reason() != null
                                ? attempt.reason()
                                : "No se pudieron resolver los campos obligatorios de la línea de producto.";
                        Integer estimatedQuantity = estimateQuantity(line);
                        sourceRows.add(new InvoiceSourceRowDTO(
                            sourceRowNumber, pageNumber, line, "REVIEW_REQUIRED",
                            estimatedQuantity, reason, null
                        ));
                        errors.add("No se pudo interpretar una línea de producto en la página "
                            + pageNumber + ": " + reason);
                    }
                }
            }
            String text = fullText.toString();

            log.debug("PDF text ({} chars). First 400: '{}'", text.length(),
                text.substring(0, Math.min(400, text.length())).replace("\n", "\\n"));

            String[] lines = text.split("\\R");

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

            ParsedInvoice invoice = new ParsedInvoice(
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
                text,
                header.destinatario(),
                summary != null ? summary.iva7() : null,
                summary != null ? summary.iva19() : null
            );
            int parsedQuantity = items.stream()
                .map(InvoiceItem::cantidad)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
            if (sourceRows.isEmpty()) {
                errors.add("No se detectaron líneas de producto en la factura.");
            }
            if (summary == null || summary.cantidadTotal() == null) {
                warnings.add("No se encontró la cantidad oficial declarada en la factura.");
            } else if (summary.cantidadTotal() != parsedQuantity) {
                errors.add("La cantidad declarada (" + summary.cantidadTotal()
                    + ") no coincide con las copias interpretadas (" + parsedQuantity + ").");
            }
            return new InvoiceParseResult(
                invoice,
                List.copyOf(sourceRows),
                List.copyOf(warnings),
                List.copyOf(errors)
            );
        }
    }

    public List<String> extractLinks(byte[] pdfBytes) throws IOException {
        return parseInvoice(pdfBytes).productLinks();
    }

    // ── Item parsing ──────────────────────────────────────────────────────────

    private InvoiceItem tryParse(String line) {
        return tryParseDetailed(line).item();
    }

    private ParseAttempt tryParseDetailed(String line) {
        if (shouldIgnoreLine(line)) return new ParseAttempt(null, null);
        Matcher m = ITEM_LINE.matcher(line);
        if (m.matches()) return buildValidatedItemDetailed(m, line);
        Matcher ml = ITEM_LINE_LOOSE.matcher(line);
        if (ml.matches()) return buildValidatedItemDetailed(ml, line);
        String reason = looksLikeProductRow(line)
            ? diagnoseUnparsedLine(line)
            : null;
        return new ParseAttempt(null, reason);
    }

    private InvoiceItem buildValidatedItem(Matcher matcher, String sourceLine) {
        return buildValidatedItemDetailed(matcher, sourceLine).item();
    }

    private ParseAttempt buildValidatedItemDetailed(Matcher matcher, String sourceLine) {
        BigDecimal unitPrice = parseMoney(matcher.group(4));
        Integer quantity = parseQuantity(matcher.group(5));
        BigDecimal lineTotal = parseMoney(matcher.group(6));
        if (unitPrice == null || quantity == null || quantity <= 0 || lineTotal == null) {
            log.debug("Línea de producto omitida por precio/cantidad inválida: {}", sourceLine);
            String reason = quantity == null || quantity <= 0
                ? "No se pudo determinar una cantidad válida."
                : "No se pudo determinar el precio o el total de la línea.";
            return new ParseAttempt(null, reason);
        }
        BigDecimal expected = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (expected.subtract(lineTotal).abs().compareTo(LINE_TOTAL_TOLERANCE) > 0) {
            log.warn("Línea de producto con total inconsistente; se conserva y se normaliza: line='{}', expected={}, parsed={}",
                sourceLine, expected, lineTotal);
        }
        if (shouldIgnoreLine(matcher.group(1)) || shouldIgnoreLine(matcher.group(2))) {
            log.debug("Línea de producto omitida por prefijo reservado: {}", sourceLine);
            return new ParseAttempt(null, "La línea coincide con un encabezado o resumen reservado.");
        }
        String descripcion = clean(matcher.group(3));
        String formato = detectFormato(descripcion);
        String album = removeFormato(descripcion, formato);
        return new ParseAttempt(new InvoiceItem(
            clean(matcher.group(1)),
            clean(matcher.group(2)),
            album,
            formato,
            unitPrice,
            quantity,
            expected
        ), null);
    }

    private boolean looksLikeProductRow(String line) {
        if (!hasProductStructure(line)) return false;
        if (PRODUCT_TAIL.matcher(line).matches()) return true;
        int moneyTokens = 0;
        Matcher moneyMatcher = MONEY_TOKEN.matcher(line);
        while (moneyMatcher.find()) moneyTokens++;
        return moneyTokens >= 2;
    }

    private boolean hasProductStructure(String line) {
        if (line == null || shouldIgnoreLine(line)) return false;
        Matcher prefix = PRODUCT_PREFIX.matcher(line);
        return prefix.matches() && prefix.group(2).contains("-");
    }

    private String diagnoseUnparsedLine(String line) {
        String[] tokens = line.strip().split("\\s+");
        if (tokens.length < 3 || parseQuantity(tokens[tokens.length - 2]) == null) {
            return "No se pudo determinar la cantidad.";
        }
        long separators = line.chars().filter(character -> character == '-').count();
        if (separators < 2) {
            return "No se pudieron separar el código, el artista y el título.";
        }
        return "La estructura de la línea no coincide con el formato esperado de Vinyl Future.";
    }

    private Integer estimateQuantity(String line) {
        Matcher tail = PRODUCT_TAIL.matcher(line == null ? "" : line);
        return tail.matches() ? parseQuantity(tail.group(1)) : null;
    }

    private ParseAttempt tryParsePositionally(PositionedProductRow row) {
        if (row == null) {
            return new ParseAttempt(null,
                "No se pudo ubicar la línea de producto dentro de las columnas de la tabla.");
        }
        PositionalRecovery recovery = row.recovery();
        if (recovery == null) {
            return new ParseAttempt(null,
                "No se pudieron separar de forma inequívoca las columnas de precio, cantidad y total.");
        }

        Matcher description = DESCRIPTION_FIELDS.matcher(recovery.description());
        if (!description.matches()) {
            return new ParseAttempt(null,
                "No se pudieron separar el código, el artista y el título mediante la columna de descripción.");
        }

        BigDecimal unitPrice = parseMoney(recovery.unitPrice());
        Integer quantity = parseQuantity(recovery.quantity());
        BigDecimal lineTotal = parseMoney(recovery.lineTotal());
        if (unitPrice == null || quantity == null || quantity <= 0 || lineTotal == null) {
            return new ParseAttempt(null,
                "Las columnas recuperadas no contienen un precio, una cantidad y un total válidos.");
        }

        BigDecimal expected = unitPrice.multiply(BigDecimal.valueOf(quantity));
        if (expected.subtract(lineTotal).abs().compareTo(LINE_TOTAL_TOLERANCE) > 0) {
            return new ParseAttempt(null,
                "El precio por la cantidad no coincide con el total de la línea recuperada.");
        }

        if (shouldIgnoreLine(description.group(1)) || shouldIgnoreLine(description.group(2))) {
            return new ParseAttempt(null, "La línea coincide con un encabezado o resumen reservado.");
        }

        String descripcion = clean(description.group(3));
        String formato = detectFormato(descripcion);
        String album = removeFormato(descripcion, formato);
        return new ParseAttempt(new InvoiceItem(
            clean(description.group(1)),
            clean(description.group(2)),
            album,
            formato,
            unitPrice,
            quantity,
            expected
        ), null);
    }

    private record ParseAttempt(InvoiceItem item, String reason) {}

    private Integer parseQuantity(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    // ── Positional fallback for damaged flattened rows ───────────────────────

    private PageLayout extractPageLayout(PDDocument document, int pageNumber) throws IOException {
        PositionCaptureStripper capture = new PositionCaptureStripper();
        capture.setStartPage(pageNumber);
        capture.setEndPage(pageNumber);
        capture.getText(document);
        return buildPageLayout(capture.positions());
    }

    private PageLayout buildPageLayout(List<PositionedGlyph> glyphs) {
        Map<Integer, List<PositionedGlyph>> glyphsByRow = new TreeMap<>();
        for (PositionedGlyph glyph : glyphs) {
            int rowKey = Math.round(glyph.y() * 2.0f);
            glyphsByRow.computeIfAbsent(rowKey, ignored -> new ArrayList<>()).add(glyph);
        }

        List<List<PositionedGlyph>> rows = new ArrayList<>(glyphsByRow.values());
        List<PositionedGlyph> header = rows.stream()
            .filter(this::isProductTableHeader)
            .findFirst()
            .orElse(null);
        if (header == null) return new PageLayout(List.of());

        Float unitPriceStart = findTextStart(header, "Unit");
        Float quantityStart = findTextStart(header, "Quantity");
        Float sumStart = findTextStart(header, "Sum");
        if (unitPriceStart == null || quantityStart == null || sumStart == null
            || unitPriceStart >= quantityStart || quantityStart >= sumStart) {
            return new PageLayout(List.of());
        }

        float headerY = header.getFirst().y();
        List<PositionedProductRow> productRows = new ArrayList<>();
        for (List<PositionedGlyph> rowGlyphs : rows) {
            if (rowGlyphs.getFirst().y() <= headerY + 1.0f) continue;
            PositionedProductRow row = new PositionedProductRow(
                splitIntoSegments(rowGlyphs), unitPriceStart, quantityStart, sumStart
            );
            if (row.isProbableProductRow()) productRows.add(row);
        }
        return new PageLayout(productRows);
    }

    private boolean isProductTableHeader(List<PositionedGlyph> glyphs) {
        String text = textByPosition(glyphs).toLowerCase(Locale.ROOT);
        return text.contains("description") && text.contains("unit") && text.contains("price")
            && text.contains("quantity") && text.contains("sum");
    }

    private Float findTextStart(List<PositionedGlyph> glyphs, String expected) {
        List<PositionedGlyph> ordered = glyphs.stream()
            .sorted(Comparator.comparing(PositionedGlyph::x))
            .toList();
        StringBuilder text = new StringBuilder();
        List<PositionedGlyph> sources = new ArrayList<>();
        for (PositionedGlyph glyph : ordered) {
            String value = glyph.text();
            for (int i = 0; i < value.length(); i++) {
                text.append(value.charAt(i));
                sources.add(glyph);
            }
        }
        int index = text.toString().toLowerCase(Locale.ROOT)
            .indexOf(expected.toLowerCase(Locale.ROOT));
        return index >= 0 && index < sources.size() ? sources.get(index).x() : null;
    }

    private String textByPosition(List<PositionedGlyph> glyphs) {
        return glyphs.stream()
            .sorted(Comparator.comparing(PositionedGlyph::x))
            .map(PositionedGlyph::text)
            .reduce("", String::concat);
    }

    private List<PositionedSegment> splitIntoSegments(List<PositionedGlyph> rowGlyphs) {
        List<PositionedGlyph> ordered = rowGlyphs.stream()
            .sorted(Comparator.comparingInt(PositionedGlyph::order))
            .toList();
        List<List<PositionedGlyph>> groups = new ArrayList<>();
        List<PositionedGlyph> current = new ArrayList<>();
        PositionedGlyph previous = null;
        for (PositionedGlyph glyph : ordered) {
            if (previous != null && !current.isEmpty()) {
                float gap = glyph.x() - previous.endX();
                boolean newColumn = gap > 12.0f || gap < -0.25f
                    || glyph.x() < previous.x() - 0.25f;
                if (newColumn) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            }
            current.add(glyph);
            previous = glyph;
        }
        if (!current.isEmpty()) groups.add(current);
        return groups.stream().map(PositionedSegment::new).toList();
    }

    private static final class PositionCaptureStripper extends PDFTextStripper {
        private final List<PositionedGlyph> positions = new ArrayList<>();
        private int order;

        private PositionCaptureStripper() throws IOException {
            setSortByPosition(false);
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            String value = text.getUnicode();
            if (value != null && !value.isEmpty()) {
                positions.add(new PositionedGlyph(
                    value,
                    text.getXDirAdj(),
                    text.getXDirAdj() + text.getWidthDirAdj(),
                    text.getYDirAdj(),
                    order++
                ));
            }
            super.processTextPosition(text);
        }

        private List<PositionedGlyph> positions() {
            return List.copyOf(positions);
        }
    }

    private record PositionedGlyph(String text, float x, float endX, float y, int order) {}

    private record PositionedSegment(List<PositionedGlyph> glyphs) {
        private String rawText() {
            return glyphs.stream().map(PositionedGlyph::text).reduce("", String::concat);
        }

        private float x() {
            return glyphs.stream().map(PositionedGlyph::x).min(Float::compare).orElse(0.0f);
        }

        private float endX() {
            return glyphs.stream().map(PositionedGlyph::endX).max(Float::compare).orElse(0.0f);
        }

        private float xAtTextIndex(int textIndex) {
            int offset = 0;
            for (PositionedGlyph glyph : glyphs) {
                int nextOffset = offset + glyph.text().length();
                if (textIndex < nextOffset) return glyph.x();
                offset = nextOffset;
            }
            return endX();
        }
    }

    private record PositionalField(int segmentIndex, String value, String descriptionPrefix) {}

    private record PositionalRecovery(String description, String unitPrice,
                                      String quantity, String lineTotal) {}

    private final class PositionedProductRow {
        private final List<PositionedSegment> segments;
        private final float unitPriceStart;
        private final float quantityStart;
        private final float sumStart;

        private PositionedProductRow(List<PositionedSegment> segments, float unitPriceStart,
                                     float quantityStart, float sumStart) {
            this.segments = segments;
            this.unitPriceStart = unitPriceStart;
            this.quantityStart = quantityStart;
            this.sumStart = sumStart;
        }

        private boolean isProbableProductRow() {
            if (segments.isEmpty()) return false;
            Matcher prefix = PRODUCT_PREFIX.matcher(clean(segments.getFirst().rawText()));
            if (!prefix.matches() || !prefix.group(2).contains("-")) return false;
            return quantityFields().size() == 1 && totalFields().size() == 1;
        }

        private String catalogueCode() {
            if (segments.isEmpty()) return null;
            Matcher prefix = PRODUCT_PREFIX.matcher(clean(segments.getFirst().rawText()));
            return prefix.matches() ? clean(prefix.group(1)) : null;
        }

        private PositionalRecovery recovery() {
            List<PositionalField> quantities = quantityFields();
            List<PositionalField> totals = totalFields();
            if (quantities.size() != 1 || totals.size() != 1) return null;

            PositionalField quantity = quantities.getFirst();
            List<PositionalField> unitPrices = unitPriceFields(quantity.segmentIndex());
            if (unitPrices.size() != 1) return null;
            PositionalField unitPrice = unitPrices.getFirst();

            List<String> descriptionParts = new ArrayList<>();
            for (int i = 0; i < unitPrice.segmentIndex(); i++) {
                String part = clean(segments.get(i).rawText());
                if (!part.isBlank()) descriptionParts.add(part);
            }
            if (unitPrice.descriptionPrefix() != null && !unitPrice.descriptionPrefix().isBlank()) {
                descriptionParts.add(clean(unitPrice.descriptionPrefix()));
            }
            String description = clean(String.join(" ", descriptionParts));
            if (description.isBlank()) return null;
            return new PositionalRecovery(
                description, unitPrice.value(), quantity.value(), totals.getFirst().value()
            );
        }

        private List<PositionalField> quantityFields() {
            List<PositionalField> result = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                PositionedSegment segment = segments.get(i);
                String value = clean(segment.rawText());
                if (value.matches("\\d{1,4}")
                    && segment.x() >= quantityStart - 2.0f && segment.endX() <= sumStart + 2.0f) {
                    result.add(new PositionalField(i, value, null));
                }
            }
            return result;
        }

        private List<PositionalField> totalFields() {
            List<PositionalField> result = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                PositionedSegment segment = segments.get(i);
                String value = clean(segment.rawText());
                if (MONEY_VALUE.matcher(value).matches() && segment.x() >= sumStart - 2.0f) {
                    result.add(new PositionalField(i, value, null));
                }
            }
            return result;
        }

        private List<PositionalField> unitPriceFields(int quantitySegmentIndex) {
            List<PositionalField> result = new ArrayList<>();
            for (int i = 0; i < quantitySegmentIndex; i++) {
                PositionedSegment segment = segments.get(i);
                String raw = segment.rawText();
                String value = clean(raw);
                if (MONEY_VALUE.matcher(value).matches()
                    && segment.x() >= unitPriceStart - 2.0f
                    && segment.endX() <= quantityStart + 2.0f) {
                    result.add(new PositionalField(i, value, null));
                    continue;
                }

                Matcher suffix = MONEY_SUFFIX.matcher(raw);
                if (suffix.find()) {
                    float suffixX = segment.xAtTextIndex(suffix.start(1));
                    if (suffixX >= unitPriceStart - 2.0f && suffixX < quantityStart) {
                        result.add(new PositionalField(
                            i, suffix.group(1), raw.substring(0, suffix.start(1))
                        ));
                    }
                }
            }
            return result;
        }
    }

    private final class PageLayout {
        private final List<PositionedProductRow> rows;
        private final Set<PositionedProductRow> claimed =
            Collections.newSetFromMap(new IdentityHashMap<>());

        private PageLayout(List<PositionedProductRow> rows) {
            this.rows = rows;
        }

        private PositionedProductRow claim(String flattenedLine) {
            Matcher prefix = PRODUCT_PREFIX.matcher(flattenedLine == null ? "" : flattenedLine);
            if (!prefix.matches()) return null;
            String code = clean(prefix.group(1));
            for (PositionedProductRow row : rows) {
                if (!claimed.contains(row) && code.equalsIgnoreCase(row.catalogueCode())) {
                    claimed.add(row);
                    return row;
                }
            }
            return null;
        }
    }

    // ── Summary row extraction ────────────────────────────────────────────────

    private record SummaryData(Integer cantidadTotal, BigDecimal franqueo,
                               BigDecimal tarifas, BigDecimal neto, BigDecimal iva,
                               BigDecimal iva7, BigDecimal iva19, BigDecimal total) {}

    /**
     * Finds a row with labels "Quantity … Postage … Fees … Net … Total"
     * then reads the next non-empty row for numeric values:
     * qty postage fees net vat7 vat19 total
     */
    private SummaryData extractSummary(String[] lines) {
        for (int i = 0; i < lines.length - 1; i++) {
            String lower = lines[i].strip().toLowerCase(Locale.ROOT);
            if (isSummaryHeader(lower)) {
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
                            BigDecimal iva7 = parts.length >= 6 ? parseMoney(parts[4]) : null;
                            BigDecimal iva19 = parts.length >= 7 ? parseMoney(parts[5]) : null;
                            BigDecimal iva = nvl(iva7).add(nvl(iva19));
                            BigDecimal total    = parts.length >= 7
                                ? parseMoney(parts[6])
                                : parseMoney(parts[parts.length - 1]);
                            return new SummaryData(qty, franqueo, tarifas, neto, iva, iva7, iva19, total);
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

    private boolean isSummaryHeader(String line) {
        String lower = line == null ? "" : line.strip().toLowerCase(Locale.ROOT);
        return lower.contains("quantity") && lower.contains("postage")
            && lower.contains("fees") && lower.contains("net")
            && lower.contains("total");
    }

    private boolean shouldIgnoreLine(String line) {
        if (line == null || line.isBlank()) return true;
        String lower = clean(line).toLowerCase(Locale.ROOT);
        if (lower.matches("^page\\s+\\d+.*")) return true;
        return IGNORED_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    // ── Header extraction (best-effort) ──────────────────────────────────────

    private record HeaderData(String numeroFactura, LocalDate fechaFactura,
                              String proveedor, String envio, String pago,
                              BigDecimal pesoTotal, String unidadPeso, String moneda,
                              String terminosVenta, String codigoArancel, String eoriNo,
                              String destinatario) {}

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
        String destinatario = firstMatchTrimmed(RECIPIENT, text);
        return new HeaderData(numeroFactura, fechaFactura, proveedor, envio, pago,
            pesoTotal, unidadPeso, moneda, terminosVenta, codigoArancel, eoriNo, destinatario);
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

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
