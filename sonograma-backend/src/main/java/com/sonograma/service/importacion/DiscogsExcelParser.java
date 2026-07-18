package com.sonograma.service.importacion;

import com.sonograma.enums.DiscogsImportRowStatus;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class DiscogsExcelParser {

    private final DiscogsLinkParser linkParser;
    private static final Pattern DISCOGS_TEXT_SPLIT = Pattern.compile("\\s+[–—-]\\s+");

    public ParsedSheet parse(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IOException("El Excel no contiene hojas");
            }
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            HeaderInfo header = detectHeader(sheet, evaluator);
            List<ParsedRow> rows = new ArrayList<>();
            int ignoredBlankRows = 0;

            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (!rowHasMeaningfulData(row, header.columns(), evaluator)) {
                    ignoredBlankRows++;
                    continue;
                }
                rows.add(parseRow(row, rowIndex + 1, header.columns(), evaluator));
            }
            return new ParsedSheet(
                    sheet.getSheetName(),
                    sheet.getLastRowNum() + 1,
                    ignoredBlankRows,
                    rows,
                    header.extraColumns()
            );
        }
    }

    private ParsedRow parseRow(
            Row row,
            int excelRowNumber,
            Map<String, Integer> columns,
            FormulaEvaluator evaluator
    ) throws IOException {
        List<String> warnings = new ArrayList<>();
        String artist = value(row, columns.get("artist"), evaluator, warnings);
        String title = value(row, columns.get("title"), evaluator, warnings);
        String rawCondition = value(row, columns.get("condition"), evaluator, warnings);
        String manualCondition = blank(rawCondition) ? null : rawCondition.trim();
        String rawPrice = value(row, columns.get("price"), evaluator, warnings);
        PriceParse price = parsePrice(rawPrice, excelRowNumber, columns.get("price"));
        String manualGenre = value(row, columns.get("genre"), evaluator, warnings);
        String sourceStatus = detectStatus(row, columns, evaluator, warnings);
        String internalCode = value(row, columns.get("code"), evaluator, warnings);
        String buyer = value(row, columns.get("buyer"), evaluator, warnings);
        if (Set.of("roto", "rayado").contains(normalize(buyer))) buyer = null;
        String observation = observation(row, columns, evaluator, warnings, buyer);
        String hyperlinkUrl = null;
        String visibleCellValue = null;
        String urlSource = null;
        DiscogsLinkParser.DiscogsLink link = null;

        if (row != null) {
            for (Cell cell : row) {
                Hyperlink hyperlink = cell.getHyperlink();
                if (hyperlink == null) continue;
                if (hyperlinkUrl == null) {
                    hyperlinkUrl = hyperlink.getAddress();
                    visibleCellValue = cellValue(cell, evaluator, warnings).value();
                }
                Optional<DiscogsLinkParser.DiscogsLink> parsed = linkParser.parse(hyperlink.getAddress());
                if (parsed.isPresent()) {
                    hyperlinkUrl = hyperlink.getAddress();
                    visibleCellValue = cellValue(cell, evaluator, warnings).value();
                    link = parsed.get();
                    urlSource = "hyperlink";
                    break;
                }
            }
        }

        if (link == null && row != null) {
            for (Cell cell : row) {
                String formulaTarget = formulaHyperlinkTarget(cell);
                if (formulaTarget != null) {
                    Optional<DiscogsLinkParser.DiscogsLink> parsed = linkParser.parse(formulaTarget);
                    if (parsed.isPresent()) {
                        hyperlinkUrl = formulaTarget;
                        visibleCellValue = cellValue(cell, evaluator, warnings).value();
                        link = parsed.get();
                        urlSource = "hyperlink_formula";
                        break;
                    }
                }
                String visible = cellValue(cell, evaluator, warnings).value();
                Optional<DiscogsLinkParser.DiscogsLink> parsed = linkParser.parse(visible);
                if (parsed.isPresent()) {
                    visibleCellValue = visible;
                    link = parsed.get();
                    urlSource = sourceFromVisibleValue(visible);
                    break;
                }
            }
        }

        if (link != null) {
            ArtistTitle extracted = extractArtistTitleFromDiscogsText(visibleCellValue);
            return new ParsedRow(
                    excelRowNumber,
                    visibleCellValue,
                    hyperlinkUrl,
                    link.normalizedUrl(),
                    urlSource,
                    link.type(),
                    link.id(),
                    firstNonBlank(artist, extracted.artist()),
                    firstNonBlank(title, extracted.title()),
                    rawCondition,
                    manualCondition,
                    rawPrice,
                    price.value(),
                    manualGenre,
                    observation,
                    sourceStatus,
                    internalCode,
                    rowStatusForSourceStatus(sourceStatus),
                    joinWarnings(warnings, price.warning())
            );
        }

        if (!blank(artist) || !blank(title)) {
            return new ParsedRow(
                    excelRowNumber,
                    firstNonBlank(artist, title),
                    null,
                    null,
                    null,
                    null,
                    null,
                    artist,
                    title,
                    rawCondition,
                    manualCondition,
                    rawPrice,
                    price.value(),
                    manualGenre,
                    observation,
                    sourceStatus,
                    internalCode,
                    DiscogsImportRowStatus.NEEDS_MANUAL_MATCH,
                    appendWarning("Fila con datos, pero sin URL de Discogs", joinWarnings(warnings, price.warning()))
            );
        }

        return new ParsedRow(
                excelRowNumber,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                rawCondition,
                manualCondition,
                rawPrice,
                price.value(),
                manualGenre,
                observation,
                sourceStatus,
                internalCode,
                DiscogsImportRowStatus.IGNORED,
                appendWarning("Fila ignorada: no contiene un link Discogs válido", joinWarnings(warnings, price.warning()))
            );
    }

    private HeaderInfo detectHeader(Sheet sheet, FormulaEvaluator evaluator) throws IOException {
        int limit = Math.min(sheet.getLastRowNum(), 30);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= limit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            List<String> extraColumns = new ArrayList<>();
            for (Cell cell : row) {
                String rawHeader = cellValue(cell, evaluator, new ArrayList<>()).value().trim();
                String header = normalize(rawHeader);
                if (matchesAlias(header, "artista", "artist", "autor")) {
                    columns.putIfAbsent("artist", cell.getColumnIndex());
                } else if (matchesAlias(header, "album", "titulo", "title")) {
                    columns.putIfAbsent("title", cell.getColumnIndex());
                } else if (matchesAlias(header, "linkdediscogs", "linkdiscogs", "discogs", "discogsurl", "urldiscogs", "enlacediscogs", "link", "url")) {
                    columns.putIfAbsent("url", cell.getColumnIndex());
                } else if (matchesAlias(header, "condicion", "condition", "estadodeldisco", "mediacondition")) {
                    columns.putIfAbsent("condition", cell.getColumnIndex());
                } else if (matchesAlias(header, "precio", "precioventa", "preciodeventa", "saleprice", "price")) {
                    columns.putIfAbsent("price", cell.getColumnIndex());
                } else if (matchesAlias(header, "genero", "genre", "estilo", "style")) {
                    columns.putIfAbsent("genre", cell.getColumnIndex());
                } else if (matchesAlias(header, "observaciones", "observacion", "notas", "nota", "comments", "comment", "description", "descripcion")) {
                    columns.putIfAbsent("observation", cell.getColumnIndex());
                } else if (matchesAlias(header, "estado", "status")) {
                    columns.putIfAbsent("status", cell.getColumnIndex());
                } else if (matchesAlias(header, "codigo", "codigodeldisco", "sku", "internalcode", "code")) {
                    columns.putIfAbsent("code", cell.getColumnIndex());
                } else if (matchesAlias(header, "comprador", "cliente", "buyer", "customer")) {
                    columns.putIfAbsent("buyer", cell.getColumnIndex());
                } else if (!rawHeader.isBlank()) {
                    extraColumns.add(rawHeader);
                }
            }
            if (columns.containsKey("url") || columns.containsKey("artist") || columns.containsKey("title")) {
                return new HeaderInfo(rowIndex, columns,
                        extraColumns.stream().distinct().toList());
            }
        }
        throw new IOException("No se pudo detectar la fila de encabezados del Excel");
    }

    private boolean rowHasMeaningfulData(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator) {
        if (row == null) return false;
        for (Cell cell : row) {
            if (cell.getHyperlink() != null) return true;
            if (formulaHyperlinkTarget(cell) != null) return true;
        }
        for (String field : List.of("url", "artist", "title", "condition", "price", "genre", "status", "observation", "buyer")) {
            Integer column = columns.get(field);
            if (column != null && !cellValue(row.getCell(column), evaluator, new ArrayList<>()).value().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private String value(Row row, Integer column, FormulaEvaluator evaluator, List<String> warnings) {
        if (row == null || column == null) return null;
        String value = cellValue(row.getCell(column), evaluator, warnings).value().trim();
        return value.isBlank() ? null : value;
    }

    private CellRead cellValue(Cell cell, FormulaEvaluator evaluator, List<String> warnings) {
        if (cell == null) return new CellRead("", null);
        if (cell.getCellType() == CellType.FORMULA) {
            CellType cachedType = cell.getCachedFormulaResultType();
            if (cachedType != CellType.BLANK && cachedType != CellType.ERROR) {
                return new CellRead(typedCellValue(cell, cachedType), null);
            }
            String fallback = ifErrorFallback(cell.getCellFormula());
            if (cell.getCellFormula().toLowerCase(Locale.ROOT).contains("_xlfn.")) {
                String warning = formulaWarning(cell, fallback);
                warnings.add(warning);
                return new CellRead(fallback, warning);
            }
            try {
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated != null && evaluated.getCellType() != CellType.ERROR
                        && evaluated.getCellType() != CellType.BLANK) {
                    return new CellRead(typedFormulaValue(evaluated), null);
                }
            } catch (RuntimeException ex) {
                // Excel formulas from add-ins and external functions are not evaluable by POI.
            }
            String warning = formulaWarning(cell, fallback);
            warnings.add(warning);
            return new CellRead(fallback, warning);
        }
        if (cell.getCellType() == CellType.ERROR) {
            String warning = "Fila " + (cell.getRowIndex() + 1) + ": la celda " + columnName(cell)
                    + " contiene un error de Excel y se dejó vacía.";
            warnings.add(warning);
            return new CellRead("", warning);
        }
        return new CellRead(typedCellValue(cell, cell.getCellType()), null);
    }

    private String typedCellValue(Cell cell, CellType type) {
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue())
                        .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String typedFormulaValue(CellValue value) {
        return switch (value.getCellType()) {
            case STRING -> value.getStringValue() == null ? "" : value.getStringValue().trim();
            case NUMERIC -> BigDecimal.valueOf(value.getNumberValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(value.getBooleanValue());
            default -> "";
        };
    }

    private String observation(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator,
                               List<String> warnings, String buyer) {
        List<String> values = new ArrayList<>();
        String explicit = value(row, columns.get("observation"), evaluator, warnings);
        if (!blank(explicit)) values.add(explicit);
        if (!blank(buyer)) values.add("Comprador Excel: " + buyer);

        String misplacedStatus = null;
        for (Cell cell : row) {
            String extra = cellValue(cell, evaluator, warnings).value().trim();
            String normalizedExtra = normalize(extra);
            if (Set.of("roto", "rayado", "vendido", "reservado").contains(normalizedExtra)) {
                misplacedStatus = extra;
                if ("roto".equals(normalizedExtra) || "rayado".equals(normalizedExtra)) {
                    values.add("Observación Excel: " + extra);
                }
            }
        }
        if ("DISPONIBLE".equals(normalizeStatus(value(row, columns.get("status"), evaluator, warnings)))
                && ("vendido".equals(normalize(misplacedStatus)) || "reservado".equals(normalize(misplacedStatus)))) {
            // Status columns are preferred, but a misplaced sold/reserved marker is still honored.
            values.add("Estado Excel detectado fuera de su columna: " + misplacedStatus);
        }

        Set<Integer> mappedColumns = new HashSet<>(columns.values());
        for (Cell cell : row) {
            if (mappedColumns.contains(cell.getColumnIndex())) continue;
            String extra = cellValue(cell, evaluator, warnings).value().trim();
            if (!extra.isBlank()) {
                String normalizedExtra = normalize(extra);
                if (!Set.of("roto", "rayado", "vendido", "reservado").contains(normalizedExtra)) {
                    values.add(columnName(cell) + (cell.getRowIndex() + 1) + ": " + extra);
                }
            }
        }
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private String columnName(Cell cell) {
        return CellReference.convertNumToColString(cell.getColumnIndex());
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]", "");
    }

    private boolean matchesAlias(String value, String... candidates) {
        return Arrays.stream(candidates).anyMatch(value::equals);
    }

    private String formulaHyperlinkTarget(Cell cell) {
        if (cell == null || cell.getCellType() != CellType.FORMULA) return null;
        Matcher matcher = Pattern.compile("(?i)HYPERLINK\\s*\\(\\s*[\\\"]([^\\\"]+)[\\\"]").matcher(cell.getCellFormula());
        return matcher.find() ? matcher.group(1) : null;
    }

    private String ifErrorFallback(String formula) {
        Matcher matcher = Pattern.compile("(?i)IFERROR\\s*\\([^,]+,\\s*[\\\"]([^\\\"]*)[\\\"]\\s*\\)").matcher(formula);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String formulaWarning(Cell cell, String fallback) {
        String detail = fallback.isBlank()
                ? "no se pudo calcular la fórmula; se dejó vacía"
                : "no se pudo calcular la fórmula; se utilizó el texto alternativo \"" + fallback + "\"";
        return "Fila " + (cell.getRowIndex() + 1) + ": " + detail + ".";
    }

    private String joinWarnings(List<String> warnings, String additional) {
        LinkedHashSet<String> unique = new LinkedHashSet<>(warnings);
        if (!blank(additional)) unique.add(additional);
        return unique.isEmpty() ? null : String.join(" ", unique);
    }

    private String firstNonBlank(String first, String second) {
        return blank(first) ? second : first;
    }

    private String sourceFromVisibleValue(String visible) {
        if (visible == null) return "visible";
        String normalized = visible.toLowerCase(Locale.ROOT);
        if (normalized.matches(".*\\[\\s*r\\d+\\s*].*")) return "visible_r_id";
        if (normalized.matches(".*\\[\\s*m\\d+\\s*].*")) return "visible_m_id";
        if (normalized.contains("discogs.com/")) return "visible";
        if (normalized.contains("discogs")) return "visible_discogs_text";
        return "visible";
    }

    private ArtistTitle extractArtistTitleFromDiscogsText(String visible) {
        if (blank(visible) || !visible.toLowerCase(Locale.ROOT).contains("discogs")) {
            return new ArtistTitle(null, null);
        }
        String cleaned = visible
                .replaceFirst("(?i)\\s*\\|\\s*discogs\\s*$", "")
                .replaceAll("(?i)\\[\\s*[rm]\\d+\\s*]", "")
                .trim();
        String[] parts = DISCOGS_TEXT_SPLIT.split(cleaned, 3);
        if (parts.length < 2) {
            return new ArtistTitle(null, null);
        }
        return new ArtistTitle(blankToNull(parts[0]), blankToNull(parts[1]));
    }

    private PriceParse parsePrice(String rawPrice, int excelRowNumber, Integer columnIndex) {
        if (blank(rawPrice)) return new PriceParse(null, null);
        String normalized = rawPrice.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("SIN PRECIO") || normalized.equals("S/P")) {
            return new PriceParse(null, cellIssue(excelRowNumber, columnIndex, rawPrice,
                    "El precio no contiene un valor numérico."));
        }
        String numeric = rawPrice.replaceAll("[^0-9,.-]", "").trim();
        if (numeric.isBlank()) {
            return new PriceParse(null, cellIssue(excelRowNumber, columnIndex, rawPrice,
                    "El precio no contiene un valor numérico."));
        }
        try {
            return new PriceParse(new BigDecimal(normalizePriceNumber(numeric)), null);
        } catch (NumberFormatException ex) {
            return new PriceParse(null, cellIssue(excelRowNumber, columnIndex, rawPrice,
                    "El precio no pudo convertirse a un número."));
        }
    }

    private String normalizePriceNumber(String numeric) {
        int comma = numeric.lastIndexOf(',');
        int dot = numeric.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (comma > dot) {
                return numeric.replace(".", "").replace(',', '.');
            }
            return numeric.replace(",", "");
        }
        if (comma >= 0) {
            return separatorValue(numeric, ',');
        }
        if (dot >= 0) {
            return separatorValue(numeric, '.');
        }
        return numeric;
    }

    private String separatorValue(String numeric, char separator) {
        int separatorIndex = numeric.lastIndexOf(separator);
        int decimals = numeric.length() - separatorIndex - 1;
        if (decimals == 3 && separatorIndex > 0) {
            return numeric.replace(String.valueOf(separator), "");
        }
        return separator == ',' ? numeric.replace(',', '.') : numeric;
    }

    private String cellIssue(int row, Integer columnIndex, String value, String explanation) {
        String column = columnIndex == null ? "?" : CellReference.convertNumToColString(columnIndex);
        return "Fila Excel " + row + ", columna " + column + ", valor \"" + value
                + "\": " + explanation;
    }

    private String normalizeStatus(String value) {
        if (blank(value)) return "DISPONIBLE";
        String normalized = normalize(value);
        if (normalized.contains("vendido")) return "VENDIDO";
        if (normalized.contains("reservado")) return "RESERVADO";
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String detectStatus(Row row, Map<String, Integer> columns,
                                FormulaEvaluator evaluator, List<String> warnings) {
        String explicit = normalizeStatus(value(row, columns.get("status"), evaluator, warnings));
        if (!"DISPONIBLE".equals(explicit)) return explicit;
        for (Cell cell : row) {
            String candidate = cellValue(cell, evaluator, warnings).value();
            String normalized = normalize(candidate);
            if ("vendido".equals(normalized)) return "VENDIDO";
            if ("reservado".equals(normalized)) return "RESERVADO";
        }
        return explicit;
    }

    private DiscogsImportRowStatus rowStatusForSourceStatus(String sourceStatus) {
        if ("VENDIDO".equals(sourceStatus)) return DiscogsImportRowStatus.SOLD;
        if ("RESERVADO".equals(sourceStatus)) return DiscogsImportRowStatus.RESERVED;
        return DiscogsImportRowStatus.PARSED;
    }

    private String appendWarning(String message, String warning) {
        return blank(warning) ? message : message + ". " + warning;
    }

    private String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record HeaderInfo(int rowIndex, Map<String, Integer> columns, List<String> extraColumns) {}

    private record ArtistTitle(String artist, String title) {}

    private record PriceParse(BigDecimal value, String warning) {}

    private record CellRead(String value, String warning) {}

    public record ParsedSheet(String sheetName, int physicalExcelLastRow, int ignoredBlankRows,
                              List<ParsedRow> rows, List<String> extraColumns) {}

    public record ParsedRow(
            int sourceExcelRowNumber,
            String visibleCellValue,
            String hyperlinkUrl,
            String normalizedDiscogsUrl,
            String urlSource,
            String discogsType,
            Long discogsId,
            String artist,
            String title,
            String rawCondition,
            String manualCondition,
            String rawPrice,
            BigDecimal manualPriceUyu,
            String manualGenre,
            String observation,
            String sourceStatus,
            String internalCode,
            DiscogsImportRowStatus status,
            String errorMessage
    ) {}
}
