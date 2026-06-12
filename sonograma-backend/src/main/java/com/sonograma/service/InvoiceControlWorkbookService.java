package com.sonograma.service;

import com.sonograma.entity.Pedido;
import com.sonograma.entity.PedidoItem;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.PedidoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InvoiceControlWorkbookService {

    private static final int COL_ARTIST = 6;
    private static final int COL_TITLE = 7;
    private static final int COL_FORMAT = 8;
    private static final int COL_CODE = 9;
    private static final int COL_UNIT_PRICE = 10;
    private static final int COL_QUANTITY = 11;
    private static final int COL_TOTAL = 12;
    private static final int COL_TYPE = 13;
    private static final int COL_EXTRA = 14;
    private static final int COL_REAL_EUR = 15;
    private static final int COL_REAL_UYU = 16;
    private static final int COL_MARKUP = 17;
    private static final int COL_FINAL_UYU = 18;

    private final PedidoRepository pedidoRepository;

    public record GeneratedWorkbook(byte[] content, String filename) {}

    @Transactional(readOnly = true)
    public GeneratedWorkbook generate(Long pedidoId, MultipartFile template) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
            .orElseThrow(() -> new RecursoNoEncontradoException("Pedido", pedidoId));

        try (Workbook workbook = WorkbookFactory.create(template.getInputStream())) {
            if (!(workbook instanceof XSSFWorkbook)) {
                throw new IllegalArgumentException("La plantilla debe ser un archivo .xlsx");
            }

            Sheet items = requiredSheet(workbook, "Items");
            Sheet summary = requiredSheet(workbook, "Summary");
            Sheet rawExtract = requiredSheet(workbook, "Raw Extract");

            int firstItemRow = findItemsHeaderRow(items) + 1;
            int lastItemRow = firstItemRow + pedido.getItems().size() - 1;

            fillItems(items, pedido, firstItemRow);
            fillSummary(summary, pedido, firstItemRow, lastItemRow);
            fillRawExtract(rawExtract, pedido);

            validate(pedido);
            workbook.setForceFormulaRecalculation(true);

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                workbook.write(output);
                return new GeneratedWorkbook(output.toByteArray(), outputFilename(pedido));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el Excel de control: " + e.getMessage(), e);
        }
    }

    private void fillItems(Sheet sheet, Pedido pedido, int firstRow) {
        int existingLastRow = findLastItemRow(sheet, firstRow);
        int requiredLastRow = firstRow + pedido.getItems().size() - 1;
        int clearThrough = Math.max(existingLastRow, requiredLastRow);
        Row styleSource = sheet.getRow(firstRow);

        for (int rowIndex = firstRow; rowIndex <= clearThrough; rowIndex++) {
            Row row = getOrCreateRow(sheet, rowIndex, styleSource);
            for (int col = COL_ARTIST; col <= COL_QUANTITY; col++) {
                clearValue(getOrCreateCell(row, col, styleSource));
            }
        }

        for (int i = 0; i < pedido.getItems().size(); i++) {
            PedidoItem item = pedido.getItems().get(i);
            int rowIndex = firstRow + i;
            int excelRow = rowIndex + 1;
            Row row = getOrCreateRow(sheet, rowIndex, styleSource);

            setText(row, COL_ARTIST, item.getArtista(), styleSource);
            setText(row, COL_TITLE, item.getTitulo(), styleSource);
            setText(row, COL_FORMAT, item.getFormato(), styleSource);
            setText(row, COL_CODE, item.getCodigo(), styleSource);
            setNumber(row, COL_UNIT_PRICE, item.getPrecioUnitarioEur(), styleSource);
            setNumber(row, COL_QUANTITY, item.getCantidad(), styleSource);

            setFormula(row, COL_TOTAL, "K" + excelRow + "*L" + excelRow, styleSource);
            setFormula(row, COL_TYPE,
                "IF(LEFT(I" + excelRow + ",2)=\"2x\",\"Double\",\"Single\")", styleSource);
            setFormula(row, COL_EXTRA,
                "IF(N" + excelRow + "=\"Double\",Summary!$B$37,Summary!$B$36)", styleSource);
            setFormula(row, COL_REAL_EUR, "K" + excelRow + "+O" + excelRow, styleSource);
            setFormula(row, COL_REAL_UYU, "P" + excelRow + "*Summary!$B$35", styleSource);
            setFormula(row, COL_MARKUP,
                "IF(N" + excelRow + "=\"Double\",Summary!$B$39,Summary!$B$38)", styleSource);
            setFormula(row, COL_FINAL_UYU, "Q" + excelRow + "*R" + excelRow, styleSource);
        }
    }

    private void fillSummary(Sheet sheet, Pedido pedido, int firstItemRow, int lastItemRow) {
        setSummaryValue(sheet, 4, pedido.getNumeroFactura());
        setSummaryDate(sheet, 5, pedido);
        setSummaryValue(sheet, 6, pedido.getProveedor());
        setSummaryValue(sheet, 7, pedido.getEnvio());
        setSummaryValue(sheet, 8, pedido.getPago());
        setSummaryValue(sheet, 9, pedido.getUnidadPeso());
        setSummaryValue(sheet, 10, pedido.getMoneda());
        setSummaryValue(sheet, 11, pedido.getTerminosVenta());
        setSummaryNumber(sheet, 12, pedido.getPesoTotalKg());
        setSummaryValue(sheet, 13, pedido.getCodigoArancel());
        setSummaryValue(sheet, 14, pedido.getEoriNo());
        setSummaryValue(sheet, 15, pedido.getNombreArchivo());
        setSummaryNumber(sheet, 23, pedido.getFranqueo());
        setSummaryNumber(sheet, 24, pedido.getTarifas());
        setSummaryNumber(sheet, 25, pedido.getNeto());

        setSummaryNumber(sheet, 35, new BigDecimal("49"));
        setSummaryNumber(sheet, 36, new BigDecimal("5"));
        setSummaryNumber(sheet, 37, new BigDecimal("8"));
        setSummaryNumber(sheet, 38, new BigDecimal("1.6"));
        setSummaryNumber(sheet, 39, new BigDecimal("1.4"));

        if (lastItemRow >= firstItemRow) {
            String merchandiseFormula = "SUM(Items!M" + (firstItemRow + 1) + ":M" + (lastItemRow + 1) + ")";
            String quantityFormula = "SUM(Items!L" + (firstItemRow + 1) + ":L" + (lastItemRow + 1) + ")";
            boolean merchandiseUpdated = updateItemSumFormulas(sheet, "M", merchandiseFormula);
            boolean quantityUpdated = updateItemSumFormulas(sheet, "L", quantityFormula);
            if (!merchandiseUpdated) setFormulaNextToLabel(sheet, "merchandise", "mercader", merchandiseFormula);
            if (!quantityUpdated) setFormulaNextToLabel(sheet, "quantity", "cantidad", quantityFormula);
        }
    }

    private void fillRawExtract(Sheet sheet, Pedido pedido) {
        Cell sourceLabel = findLabel(sheet, "source file");
        Cell extractLabel = findLabel(sheet, "extracted text");
        if (sourceLabel == null || extractLabel == null) {
            throw new IllegalArgumentException("La hoja Raw Extract no contiene los encabezados Source File y Extracted Text");
        }

        boolean sameHeaderRow = sourceLabel.getRowIndex() == extractLabel.getRowIndex();
        Cell sourceValue = sameHeaderRow
            ? getOrCreateCell(sheet, sourceLabel.getRowIndex() + 1, sourceLabel.getColumnIndex())
            : getOrCreateCell(sheet, sourceLabel.getRowIndex(), sourceLabel.getColumnIndex() + 1);
        Cell extractValue = sameHeaderRow
            ? getOrCreateCell(sheet, extractLabel.getRowIndex() + 1, extractLabel.getColumnIndex())
            : getOrCreateCell(sheet, extractLabel.getRowIndex(), extractLabel.getColumnIndex() + 1);

        sourceValue.setCellValue(orEmpty(pedido.getNombreArchivo()));
        extractValue.setCellValue(orEmpty(pedido.getTextoExtraido()));
    }

    private void validate(Pedido pedido) {
        int itemQuantity = pedido.getItems().stream()
            .map(PedidoItem::getCantidad)
            .filter(value -> value != null)
            .mapToInt(Integer::intValue)
            .sum();
        if (pedido.getCantidadTotalPdf() != null && pedido.getCantidadTotalPdf() != itemQuantity) {
            throw new IllegalArgumentException(
                "La cantidad del PDF (" + pedido.getCantidadTotalPdf()
                    + ") no coincide con los items (" + itemQuantity + ")");
        }

        for (PedidoItem item : pedido.getItems()) {
            if (item.getPrecioUnitarioEur() == null || item.getCantidad() == null) {
                throw new IllegalArgumentException("Hay items sin precio unitario o cantidad");
            }
            BigDecimal expected = item.getPrecioUnitarioEur().multiply(BigDecimal.valueOf(item.getCantidad()));
            if (item.getTotalLineaEur() == null || expected.compareTo(item.getTotalLineaEur()) != 0) {
                throw new IllegalArgumentException("El total del item " + orEmpty(item.getCodigo()) + " no coincide");
            }
        }
    }

    private Sheet requiredSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) throw new IllegalArgumentException("La plantilla no contiene la hoja " + name);
        return sheet;
    }

    private int findItemsHeaderRow(Sheet sheet) {
        for (Row row : sheet) {
            if (contains(row.getCell(COL_ARTIST), "artist", "artista")
                    && contains(row.getCell(COL_TITLE), "title", "titulo")) {
                return row.getRowNum();
            }
        }
        throw new IllegalArgumentException("No se encontró el encabezado de items en columnas G:H");
    }

    private int findLastItemRow(Sheet sheet, int firstRow) {
        int last = firstRow - 1;
        boolean foundItemRow = false;
        for (int rowIndex = firstRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            boolean itemRow = isItemRow(row);
            if (itemRow) {
                foundItemRow = true;
                last = rowIndex;
            } else if (foundItemRow) {
                break;
            }
        }
        return last;
    }

    private boolean isItemRow(Row row) {
        if (row == null) return false;
        for (int col = COL_ARTIST; col <= COL_QUANTITY; col++) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK) return true;
        }
        for (int col = COL_TOTAL; col <= COL_FINAL_UYU; col++) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() == CellType.FORMULA) return true;
        }
        return false;
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex, Row styleSource) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
            if (styleSource != null) row.setHeight(styleSource.getHeight());
        }
        return row;
    }

    private Cell getOrCreateCell(Row row, int col, Row styleSource) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            cell = row.createCell(col);
            if (styleSource != null && styleSource.getCell(col) != null) {
                cell.setCellStyle(styleSource.getCell(col).getCellStyle());
            }
        }
        return cell;
    }

    private Cell getOrCreateCell(Sheet sheet, int row, int col) {
        return getOrCreateRow(sheet, row, null).getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
    }

    private void clearValue(Cell cell) {
        cell.setBlank();
    }

    private void setText(Row row, int col, String value, Row styleSource) {
        getOrCreateCell(row, col, styleSource).setCellValue(orEmpty(value));
    }

    private void setNumber(Row row, int col, Number value, Row styleSource) {
        Cell cell = getOrCreateCell(row, col, styleSource);
        if (value == null) cell.setBlank();
        else cell.setCellValue(value.doubleValue());
    }

    private void setFormula(Row row, int col, String formula, Row styleSource) {
        getOrCreateCell(row, col, styleSource).setCellFormula(formula);
    }

    private void setSummaryValue(Sheet sheet, int excelRow, String value) {
        getOrCreateCell(sheet, excelRow - 1, 1).setCellValue(orEmpty(value));
    }

    private void setSummaryDate(Sheet sheet, int excelRow, Pedido pedido) {
        Cell cell = getOrCreateCell(sheet, excelRow - 1, 1);
        if (pedido.getFechaFactura() == null) cell.setBlank();
        else cell.setCellValue(pedido.getFechaFactura());
    }

    private void setSummaryNumber(Sheet sheet, int excelRow, Number value) {
        Cell cell = getOrCreateCell(sheet, excelRow - 1, 1);
        if (value == null) cell.setBlank();
        else cell.setCellValue(value.doubleValue());
    }

    private boolean updateItemSumFormulas(Sheet sheet, String column, String replacement) {
        boolean updated = false;
        String marker = "ITEMS!" + column.toUpperCase(Locale.ROOT);
        for (Row row : sheet) {
            for (Cell cell : row) {
                String formula = cell.getCellType() == CellType.FORMULA
                    ? cell.getCellFormula().toUpperCase(Locale.ROOT).replace("'", "")
                    : "";
                if (formula.contains("SUM(") && formula.contains(marker)) {
                    cell.setCellFormula(replacement);
                    updated = true;
                }
            }
        }
        return updated;
    }

    private void setFormulaNextToLabel(Sheet sheet, String english, String spanish, String formula) {
        Cell label = findLabel(sheet, english, spanish);
        if (label != null) {
            getOrCreateCell(sheet, label.getRowIndex(), label.getColumnIndex() + 1).setCellFormula(formula);
        }
    }

    private Cell findLabel(Sheet sheet, String... fragments) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (contains(cell, fragments)) return cell;
            }
        }
        return null;
    }

    private boolean contains(Cell cell, String... fragments) {
        if (cell == null || cell.getCellType() != CellType.STRING) return false;
        String value = normalize(cell.getStringCellValue());
        for (String fragment : fragments) {
            if (value.contains(normalize(fragment))) return true;
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u");
    }

    private String outputFilename(Pedido pedido) {
        String invoice = pedido.getNumeroFactura();
        if (invoice == null || invoice.isBlank()) invoice = "invoice";
        invoice = invoice.replaceAll("[^A-Za-z0-9._-]", "_");
        return invoice + "_control.xlsx";
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
