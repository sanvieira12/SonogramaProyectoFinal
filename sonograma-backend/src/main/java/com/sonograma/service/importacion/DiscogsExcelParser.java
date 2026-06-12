package com.sonograma.service.importacion;

import com.sonograma.enums.DiscogsImportRowStatus;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DiscogsExcelParser {

    private final DiscogsLinkParser linkParser;

    public ParsedSheet parse(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IOException("El Excel no contiene hojas");
            }
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            HeaderInfo header = detectHeader(sheet, evaluator);
            List<ParsedRow> rows = new ArrayList<>();

            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                rows.add(parseRow(sheet.getRow(rowIndex), rowIndex + 1, header.columns(), evaluator));
            }
            return new ParsedSheet(sheet.getSheetName(), markDuplicates(rows));
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
    ) {
        String artist = value(row, columns.get("artist"), evaluator);
        String title = value(row, columns.get("title"), evaluator);
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
                    urlSource = "visible";
                    break;
                }
            }
        }

        if (link != null) {
            return new ParsedRow(
                    excelRowNumber,
                    visibleCellValue,
                    hyperlinkUrl,
                    link.normalizedUrl(),
                    urlSource,
                    link.type(),
                    link.id(),
                    artist,
                    title,
                    DiscogsImportRowStatus.PARSED,
                    null
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
                    DiscogsImportRowStatus.NEEDS_MANUAL_MATCH,
                    "Fila con artista o álbum, pero sin URL de Discogs"
            );
        }

        boolean trulyEmpty = row == null || rowIsEmpty(row, evaluator);
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
                DiscogsImportRowStatus.IGNORED,
                trulyEmpty
                        ? "Fila sin artista ni álbum y sin link Discogs"
                        : "Fila ignorada: no contiene un link Discogs válido"
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
                }
            }
            if (!columns.isEmpty()) {
                return new HeaderInfo(rowIndex, columns);
            }
        }
        throw new IOException("No se pudo detectar la fila de encabezados del Excel");
    }

    private boolean rowIsEmpty(Row row, FormulaEvaluator evaluator) {
        for (Cell cell : row) {
            if (cell.getHyperlink() != null || !cellValue(cell, evaluator).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String value(Row row, Integer column, FormulaEvaluator evaluator) {
        if (row == null || column == null) return null;
        String value = cellValue(row.getCell(column), evaluator).trim();
        return value.isBlank() ? null : value;
    }

    private String cellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? evaluator.evaluateFormulaCell(cell)
                : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue())
                    .stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record HeaderInfo(int rowIndex, Map<String, Integer> columns) {}

    public record ParsedSheet(String sheetName, List<ParsedRow> rows) {}

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
            DiscogsImportRowStatus status,
            String errorMessage
    ) {}
}
