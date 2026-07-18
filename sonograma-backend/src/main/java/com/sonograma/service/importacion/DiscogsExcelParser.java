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
                    markDuplicates(rows)
            );
        }
    }

    private List<ParsedRow> markDuplicates(List<ParsedRow> rows) {
        Set<String> seen = new HashSet<>();
        List<ParsedRow> deduplicated = new ArrayList<>(rows.size());
        for (ParsedRow row : rows) {
            if (row.discogsType() == null || row.discogsId() == null) {
                deduplicated.add(row);
                continue;
            }
            String key = row.discogsType() + ":" + row.discogsId();
            if (seen.add(key)) {
                deduplicated.add(row);
                continue;
            }
            deduplicated.add(new ParsedRow(
                    row.sourceExcelRowNumber(),
                    row.visibleCellValue(),
                    row.hyperlinkUrl(),
                    row.normalizedDiscogsUrl(),
                    row.urlSource(),
                    row.discogsType(),
                    row.discogsId(),
                    row.artist(),
                    row.title(),
                    row.rawCondition(),
                    row.manualCondition(),
                    row.rawPrice(),
                    row.manualPriceUyu(),
                    row.manualGenre(),
                    row.observation(),
                    row.sourceStatus(),
                    row.internalCode(),
                    DiscogsImportRowStatus.IGNORED,
                    "Link Discogs duplicado dentro de la importación"
            ));
        }
        return deduplicated;
    }

    private ParsedRow parseRow(
            Row row,
            int excelRowNumber,
            Map<String, Integer> columns,
            FormulaEvaluator evaluator
    ) throws IOException {
        String artist = value(row, columns.get("artist"), evaluator);
        String title = value(row, columns.get("title"), evaluator);
        String rawCondition = value(row, columns.get("condition"), evaluator);
        String manualCondition = blank(rawCondition) ? null : rawCondition.trim();
        String rawPrice = value(row, columns.get("price"), evaluator);
        PriceParse price = parsePrice(rawPrice, excelRowNumber, columns.get("price"));
        String manualGenre = value(row, columns.get("genre"), evaluator);
        String sourceStatus = normalizeStatus(value(row, columns.get("status"), evaluator));
        String internalCode = value(row, columns.get("code"), evaluator);
        String observation = observation(row, columns, evaluator);
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
                    visibleCellValue = cellValue(cell, evaluator);
                }
                Optional<DiscogsLinkParser.DiscogsLink> parsed = linkParser.parse(hyperlink.getAddress());
                if (parsed.isPresent()) {
                    hyperlinkUrl = hyperlink.getAddress();
                    visibleCellValue = cellValue(cell, evaluator);
                    link = parsed.get();
                    urlSource = "hyperlink";
                    break;
                }
            }
        }

        if (link == null && row != null) {
            for (Cell cell : row) {
                String visible = cellValue(cell, evaluator);
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
                    price.warning()
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
                    appendWarning("Fila con artista o álbum, pero sin URL de Discogs", price.warning())
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
                appendWarning("Fila ignorada: no contiene un link Discogs válido", price.warning())
        );
    }

    private HeaderInfo detectHeader(Sheet sheet, FormulaEvaluator evaluator) throws IOException {
        int limit = Math.min(sheet.getLastRowNum(), 30);
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= limit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) {
                String header = normalize(cellValue(cell, evaluator));
                if (containsAny(header, "artista", "artist", "autor")) {
                    columns.putIfAbsent("artist", cell.getColumnIndex());
                } else if (containsAny(header, "album", "titulo", "title")) {
                    columns.putIfAbsent("title", cell.getColumnIndex());
                } else if (containsAny(header, "discogs", "link", "url")) {
                    columns.putIfAbsent("url", cell.getColumnIndex());
                } else if (containsAny(header, "condicion", "condition")) {
                    columns.putIfAbsent("condition", cell.getColumnIndex());
                } else if (containsAny(header, "precio", "price")) {
                    columns.putIfAbsent("price", cell.getColumnIndex());
                } else if (containsAny(header, "genero", "genre")) {
                    columns.putIfAbsent("genre", cell.getColumnIndex());
                } else if (containsAny(header, "observacion", "nota", "notes", "comment", "comentario")) {
                    columns.putIfAbsent("observation", cell.getColumnIndex());
                } else if (containsAny(header, "estado", "status")) {
                    columns.putIfAbsent("status", cell.getColumnIndex());
                } else if (containsAny(header, "codigo", "code")) {
                    columns.putIfAbsent("code", cell.getColumnIndex());
                }
            }
            if (!columns.isEmpty()) {
                return new HeaderInfo(rowIndex, columns);
            }
        }
        throw new IOException("No se pudo detectar la fila de encabezados del Excel");
    }

    private boolean rowHasMeaningfulData(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator)
            throws IOException {
        if (row == null) return false;
        for (Cell cell : row) {
            if (cell.getHyperlink() != null) return true;
        }
        if (!blank(value(row, columns.get("url"), evaluator))) return true;
        return !blank(value(row, columns.get("artist"), evaluator))
                || !blank(value(row, columns.get("title"), evaluator));
    }

    private String value(Row row, Integer column, FormulaEvaluator evaluator) throws IOException {
        if (row == null || column == null) return null;
        String value = cellValue(row.getCell(column), evaluator).trim();
        return value.isBlank() ? null : value;
    }

    private String cellValue(Cell cell, FormulaEvaluator evaluator) throws IOException {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) {
            CellType cachedType = cell.getCachedFormulaResultType();
            if (cachedType != CellType.BLANK && cachedType != CellType.ERROR) {
                return typedCellValue(cell, cachedType);
            }
            try {
                CellValue evaluated = evaluator.evaluate(cell);
                if (evaluated == null || evaluated.getCellType() == CellType.ERROR) {
                    throw new ExcelImportException(
                            cell.getRowIndex() + 1,
                            columnName(cell),
                            "[fórmula sin valor en caché]",
                            "La fórmula no pudo evaluarse y no tiene un resultado utilizable.");
                }
                return typedFormulaValue(evaluated);
            } catch (ExcelImportException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new ExcelImportException(
                        cell.getRowIndex() + 1,
                        columnName(cell),
                        "[fórmula sin valor en caché]",
                        "La fórmula de Excel no es compatible con el importador y no tiene un valor calculado disponible.");
            }
        }
        return typedCellValue(cell, cell.getCellType());
    }

    private String typedCellValue(Cell cell, CellType type) {
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue())
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

    private String observation(Row row, Map<String, Integer> columns, FormulaEvaluator evaluator)
            throws IOException {
        List<String> values = new ArrayList<>();
        String explicit = value(row, columns.get("observation"), evaluator);
        if (!blank(explicit)) values.add(explicit);

        Set<Integer> mappedColumns = new HashSet<>(columns.values());
        for (Cell cell : row) {
            if (mappedColumns.contains(cell.getColumnIndex())) continue;
            String extra = cellValue(cell, evaluator).trim();
            if (!extra.isBlank()) {
                values.add(columnName(cell) + (cell.getRowIndex() + 1) + ": " + extra);
            }
        }
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private String columnName(Cell cell) {
        return CellReference.convertNumToColString(cell.getColumnIndex());
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]", "");
    }

    private boolean containsAny(String value, String... candidates) {
        return Arrays.stream(candidates).anyMatch(value::contains);
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

    private record HeaderInfo(int rowIndex, Map<String, Integer> columns) {}

    private record ArtistTitle(String artist, String title) {}

    private record PriceParse(BigDecimal value, String warning) {}

    public record ParsedSheet(String sheetName, int physicalExcelLastRow, int ignoredBlankRows, List<ParsedRow> rows) {}

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
