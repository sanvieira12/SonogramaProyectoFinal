package com.sonograma.service;

import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscoQrCopy;
import com.sonograma.entity.DiscogsManualBatch;
import com.sonograma.dto.ManualDiscogsExcelRowDTO;
import com.sonograma.enums.EstadoCopiaDisco;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.DiscoQrCopyRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsManualBatchRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generates the manual Discogs customer workbook from exact physical copies. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscogsManualBatchExcelService {

    public static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final String SHEET_NAME = "Hoja 1";
    private static final String[] HEADERS = {"LINK", "PRECIO", "CONDICION", "ESTADO", "GENERO", "CODIGO"};
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final float DEFAULT_ROW_HEIGHT = 15.75f;

    private final DiscogsManualBatchRepository batchRepository;
    private final DiscoQrCopyRepository copyRepository;
    private final DiscoRepository discoRepository;

    public GeneratedWorkbook generate(Long batchId) {
        if (batchId == null || batchId <= 0) {
            throw new NegocioException("El batch Discogs no es válido.");
        }
        DiscogsManualBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Batch Discogs", batchId));
        List<DiscoQrCopy> copies = copyRepository.findByManualDiscogsBatchIdOrderByCopyNumber(batchId);
        if (copies.isEmpty()) {
            throw new NegocioException("El batch Discogs no tiene copias físicas para exportar.");
        }

        List<Long> productIds = copies.stream().map(DiscoQrCopy::getIdDisco).distinct().toList();
        Map<Long, Disco> products = new HashMap<>();
        discoRepository.findAllById(productIds).forEach(product -> products.put(product.getIdDisco(), product));
        for (DiscoQrCopy copy : copies) {
            if (!products.containsKey(copy.getIdDisco())) {
                throw new NegocioException("No se encontró el producto de la copia " + copy.getId() + ".");
            }
        }

        List<WorkbookRow> rows = copies.stream().map(copy -> {
            Disco product = products.get(copy.getIdDisco());
            return new WorkbookRow(
                    copy.getPrecioVenta(), copy.getCondicionFisica(), copy.getEstado(),
                    batch.getNormalizedCustomerCode(), product.getDiscogsReleaseId(),
                    product.getDiscogsUrl(), product.getArtista(), product.getAlbum(), product.getGenero());
        }).toList();
        return writeWorkbook(rows, filename(batch), false);
    }

    public GeneratedWorkbook generateForSource(String sourceCustomerCode) {
        final String normalized;
        try {
            normalized = DiscogsManualBatchService.normalizeCustomerCode(sourceCustomerCode);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException(ex.getMessage());
        }
        List<ManualDiscogsExcelRowDTO> sourceRows = copyRepository.findExcelRowsByManualSource(normalized);
        if (sourceRows.isEmpty()) {
            throw new RecursoNoEncontradoException(
                    "No hay copias físicas retenidas para la fuente Discogs " + normalized + ".");
        }
        List<WorkbookRow> rows = sourceRows.stream().map(row -> new WorkbookRow(
                row.copyPrice(), row.copyCondition(), row.copyState(),
                row.normalizedSourceCustomerCode(), row.discogsReleaseId(),
                row.storedDiscogsUrl(), row.artist(), row.title(), row.genre())).toList();
        String source = sanitize(normalized, "SOURCE");
        return writeWorkbook(rows, source + "_" + LocalDate.now().format(FILE_DATE) + ".xlsx", true);
    }

    private GeneratedWorkbook writeWorkbook(List<WorkbookRow> rows, String filename, boolean exposeAllStates) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            sheet.setDefaultColumnWidth(13);
            sheet.setDefaultRowHeightInPoints(DEFAULT_ROW_HEIGHT);
            sheet.setColumnWidth(0, width(88.13));
            sheet.setColumnWidth(4, width(20.13));

            Styles styles = new Styles(workbook);
            Row header = sheet.createRow(0);
            header.setHeightInPoints(DEFAULT_ROW_HEIGHT);
            for (int column = 0; column < HEADERS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(styles.header());
            }

            int rowNumber = 1;
            for (WorkbookRow exportRow : rows) {
                Row row = sheet.createRow(rowNumber++);
                row.setHeightInPoints(DEFAULT_ROW_HEIGHT);
                writeLink(row, exportRow, styles, workbook);
                writeText(row, 1, formatPrice(exportRow.price()), styles.body());
                writeText(row, 2, exportRow.condition(), styles.condition(exportRow.condition()));
                writeText(row, 3, exportStatus(exportRow.state(), exposeAllStates), styles.status(exportRow.state()));
                writeText(row, 4, exportRow.genre(), styles.body());
                writeText(row, 5, exportRow.normalizedSource(), styles.body());
            }

            workbook.write(output);
            return new GeneratedWorkbook(output.toByteArray(), filename);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el Excel manual Discogs.", ex);
        }
    }

    private void writeLink(Row row, WorkbookRow exportRow, Styles styles, Workbook workbook) {
        String url = DiscogsReleaseUrlBuilder.build(
                exportRow.releaseId(), exportRow.storedUrl(), exportRow.artist(), exportRow.title());
        Cell cell = row.createCell(0);
        cell.setCellValue(url == null ? "" : url);
        cell.setCellStyle(url != null && isHttpUrl(url) ? styles.hyperlink() : styles.body());
        if (url != null && isHttpUrl(url)) {
            var hyperlink = workbook.getCreationHelper().createHyperlink(
                    org.apache.poi.common.usermodel.HyperlinkType.URL);
            hyperlink.setAddress(url);
            cell.setHyperlink(hyperlink);
        }
    }

    private void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "https://", 0, 8)
                || value.regionMatches(true, 0, "http://", 0, 7);
    }

    private String formatPrice(BigDecimal value) {
        if (value == null) return "SIN PRECIO";
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-UY"));
        boolean hasDecimals = value.stripTrailingZeros().scale() > 0;
        DecimalFormat formatter = new DecimalFormat(hasDecimals ? "$#,##0.00" : "$#,##0", symbols);
        formatter.setGroupingUsed(true);
        return formatter.format(value);
    }

    private String exportStatus(EstadoCopiaDisco status, boolean exposeAllStates) {
        if (status == null) return "";
        if (exposeAllStates) return status.name();
        return status == EstadoCopiaDisco.VENDIDO ? EstadoCopiaDisco.VENDIDO.name() : "";
    }

    private String filename(DiscogsManualBatch batch) {
        String customer = sanitize(batch.getNormalizedCustomerCode(), "BATCH");
        LocalDate date = dateOf(batch.getStartedAt(), batch.getCreatedAt());
        return customer + "_" + date.format(FILE_DATE) + "_batch-" + batch.getId() + ".xlsx";
    }

    private LocalDate dateOf(LocalDateTime startedAt, LocalDateTime createdAt) {
        LocalDateTime value = startedAt != null ? startedAt : createdAt;
        return value != null ? value.toLocalDate() : LocalDate.now();
    }

    private String sanitize(String value, String fallback) {
        String sanitized = value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }

    private int width(double characters) {
        return (int) Math.round(characters * 256);
    }

    public record GeneratedWorkbook(byte[] content, String filename) {}

    private record WorkbookRow(
            BigDecimal price,
            String condition,
            EstadoCopiaDisco state,
            String normalizedSource,
            Long releaseId,
            String storedUrl,
            String artist,
            String title,
            String genre
    ) {}

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle body;
        private final CellStyle hyperlink;
        private final Map<String, CellStyle> conditionStyles = new HashMap<>();
        private final CellStyle sold;

        private Styles(XSSFWorkbook workbook) {
            this.body = textStyle(workbook, null, false, false);
            this.header = textStyle(workbook, "D9D9D9", true, false);
            this.hyperlink = textStyle(workbook, null, false, true);
            this.sold = textStyle(workbook, "FF0000", false, false);
            conditionStyles.put("VG+", textStyle(workbook, "34A853", false, false));
            conditionStyles.put("M", textStyle(workbook, "34A853", false, false));
            conditionStyles.put("NM", textStyle(workbook, "34A853", false, false));
            conditionStyles.put("INCOMPLETO", textStyle(workbook, "CCCCCC", false, false));
            conditionStyles.put("VG", textStyle(workbook, "FFF2CC", false, false));
            conditionStyles.put("G", textStyle(workbook, "F6B26B", false, false));
        }

        private CellStyle header() { return header; }
        private CellStyle body() { return body; }
        private CellStyle hyperlink() { return hyperlink; }
        private CellStyle condition(String value) {
            return value == null ? body : conditionStyles.getOrDefault(value.trim().toUpperCase(Locale.ROOT), body);
        }
        private CellStyle status(EstadoCopiaDisco value) { return value == EstadoCopiaDisco.VENDIDO ? sold : body; }

        private static CellStyle textStyle(Workbook workbook, String fill, boolean bold, boolean link) {
            XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
            XSSFFont font = (XSSFFont) workbook.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints((short) 10);
            font.setBold(bold);
            if (link) {
                font.setColor(new XSSFColor(new byte[]{0x11, 0x55, (byte) 0xCC}, null));
                font.setUnderline(Font.U_SINGLE);
            }
            style.setFont(font);
            style.setDataFormat(workbook.createDataFormat().getFormat("@"));
            if (fill != null) {
                style.setFillForegroundColor(new XSSFColor(hex(fill), null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }

        private static byte[] hex(String color) {
            return new byte[]{
                    (byte) Integer.parseInt(color.substring(0, 2), 16),
                    (byte) Integer.parseInt(color.substring(2, 4), 16),
                    (byte) Integer.parseInt(color.substring(4, 6), 16)
            };
        }
    }
}
