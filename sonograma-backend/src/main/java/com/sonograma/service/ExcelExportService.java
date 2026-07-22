package com.sonograma.service;

import com.sonograma.entity.ShippingOrder;
import com.sonograma.entity.ShippingOrderItem;
import com.sonograma.entity.Venta;
import com.sonograma.dto.VentaResponseDTO;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private final ProfitCalculationService profitCalculationService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] exportarLibroVentas(List<Venta> ventas) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Libro de Ventas");
            sheet.setDefaultColumnWidth(18);

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle = buildMoneyStyle(wb);

            String[] headers = {
                "N° Factura", "Fecha", "Cliente", "Artista", "Álbum",
                "Canal", "Medio Pago", "Precio Venta", "Costo Envío",
                "Impuesto", "Total Final", "Monto Pagado", "Monto Deuda",
                "Estado Pago", "Ganancia", "Observaciones"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            BigDecimal totalGanancia = BigDecimal.ZERO;
            BigDecimal totalVentas = BigDecimal.ZERO;

            for (Venta v : ventas) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(orEmpty(v.getNumeroFactura()));

                Cell fechaCell = row.createCell(1);
                fechaCell.setCellValue(v.getFechaVenta() != null ? v.getFechaVenta().format(FMT) : "");

                String cliente = v.getClienteNombreSnapshot() != null ? v.getClienteNombreSnapshot()
                        : v.getCliente().getNombre() + " " + orEmpty(v.getCliente().getApellido());
                row.createCell(2).setCellValue(cliente);
                String artistaXls = v.getDisco() != null ? v.getDisco().getArtista()
                        : (v.getDetalles() != null && !v.getDetalles().isEmpty() ? v.getDetalles().get(0).getArtistaSnap() : null);
                String albumXls = v.getDisco() != null ? v.getDisco().getAlbum()
                        : (v.getDetalles() != null && !v.getDetalles().isEmpty() ? v.getDetalles().get(0).getAlbumSnap() : null);
                row.createCell(3).setCellValue(orEmpty(artistaXls));
                row.createCell(4).setCellValue(orEmpty(albumXls));
                row.createCell(5).setCellValue(v.getCanalVenta() != null ? v.getCanalVenta().name() : "");
                row.createCell(6).setCellValue(v.getMedioPago() != null ? v.getMedioPago().name() : "");

                setMoney(row, 7, v.getPrecioVenta(), moneyStyle);
                setMoney(row, 8, v.getCostoEnvio(), moneyStyle);
                setMoney(row, 9, v.getMontoImpuesto(), moneyStyle);
                setMoney(row, 10, VentaTotals.totalProductos(v), moneyStyle);
                setMoney(row, 11, v.getMontoPagado(), moneyStyle);
                setMoney(row, 12, v.getMontoDeuda(), moneyStyle);

                row.createCell(13).setCellValue(v.getEstadoPago() != null ? v.getEstadoPago().name() : "");
                ProfitResult profit = profitCalculationService.netProfitForSale(v);
                setMoney(row, 14, profit.netProfit(), moneyStyle);
                row.createCell(15).setCellValue(orEmpty(v.getObservaciones()));

                totalVentas = totalVentas.add(VentaTotals.totalProductos(v));
                totalGanancia = totalGanancia.add(profit.netProfit());
            }

            Row totalesRow = sheet.createRow(rowNum + 1);
            CellStyle boldStyle = buildBoldStyle(wb);
            Cell totalLabel = totalesRow.createCell(9);
            totalLabel.setCellValue("TOTALES");
            totalLabel.setCellStyle(boldStyle);
            setMoney(totalesRow, 10, totalVentas, moneyStyle);
            setMoney(totalesRow, 14, totalGanancia, moneyStyle);

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de ventas", e);
        }
    }

    public byte[] exportarLibroMovimientos(List<VentaResponseDTO> movimientos) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Libro de Ventas");
            sheet.setDefaultColumnWidth(18);

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle = buildMoneyStyle(wb);

            String[] headers = {
                "Movimiento", "N° Factura", "Fecha", "Cliente", "Artista / Descripción", "Álbum",
                "Canal", "Ganancia neta", "Ingreso", "Total Venta", "Monto Deuda",
                "Estado Pago", "Observaciones"
            };

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            BigDecimal totalIngresos = BigDecimal.ZERO;
            BigDecimal totalGanancia = BigDecimal.ZERO;

            for (VentaResponseDTO v : movimientos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(orEmpty(v.getDescripcionMovimiento()));
                row.createCell(1).setCellValue(orEmpty(v.getNumeroFactura()));
                row.createCell(2).setCellValue(v.getFechaVenta() != null ? v.getFechaVenta().format(FMT) : "");
                row.createCell(3).setCellValue(orEmpty(v.getClienteNombreSnapshot() != null
                    ? v.getClienteNombreSnapshot()
                    : (orEmpty(v.getNombreCliente()) + " " + orEmpty(v.getApellidoCliente())).trim()));
                row.createCell(4).setCellValue(orEmpty(descripcionMovimiento(v)));
                row.createCell(5).setCellValue(orEmpty(v.getAlbum()));
                row.createCell(6).setCellValue(orEmpty(v.getCanalVenta()));
                setMoney(row, 7, gananciaMovimiento(v), moneyStyle);
                setMoney(row, 8, ingresoMovimiento(v), moneyStyle);
                setMoney(row, 9, v.getTotalFinal(), moneyStyle);
                setMoney(row, 10, v.getMontoDeuda(), moneyStyle);
                row.createCell(11).setCellValue(orEmpty(v.getEstadoPago()));
                row.createCell(12).setCellValue(orEmpty(v.getObservaciones()));

                totalIngresos = totalIngresos.add(ingresoMovimiento(v));
                if (gananciaMovimiento(v) != null) totalGanancia = totalGanancia.add(gananciaMovimiento(v));
            }

            Row totalesRow = sheet.createRow(rowNum + 1);
            CellStyle boldStyle = buildBoldStyle(wb);
            Cell totalLabel = totalesRow.createCell(6);
            totalLabel.setCellValue("TOTALES");
            totalLabel.setCellStyle(boldStyle);
            setMoney(totalesRow, 7, totalGanancia, moneyStyle);
            setMoney(totalesRow, 8, totalIngresos, moneyStyle);

            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de ventas", e);
        }
    }

    public byte[] exportarShippingOrder(ShippingOrder order) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Orden " + order.getNumero());
            sheet.setDefaultColumnWidth(20);

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle moneyStyle = buildMoneyStyle(wb);

            // Header info
            sheet.createRow(0).createCell(0).setCellValue("Orden: " + order.getNumero());
            sheet.createRow(1).createCell(0).setCellValue("Proveedor: " + order.getProveedor());
            sheet.createRow(2).createCell(0).setCellValue("Fecha: " +
                (order.getFechaOrden() != null ? order.getFechaOrden().format(FMT_DATE) : ""));
            sheet.createRow(3).createCell(0).setCellValue("Estado: " + order.getEstado().name());

            String[] cols = { "Artista", "Álbum", "Descripción", "Cant.", "Precio Unit.", "Subtotal" };
            Row headerRow = sheet.createRow(5);
            for (int i = 0; i < cols.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            int rowNum = 6;
            BigDecimal costoTotal = BigDecimal.ZERO;
            for (ShippingOrderItem item : order.getItems()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(orEmpty(item.getArtista()));
                row.createCell(1).setCellValue(orEmpty(item.getAlbum()));
                row.createCell(2).setCellValue(orEmpty(item.getDescripcion()));
                row.createCell(3).setCellValue(item.getCantidad() != null ? item.getCantidad() : 1);
                setMoney(row, 4, item.getPrecioUnitario(), moneyStyle);
                setMoney(row, 5, item.getSubtotal(), moneyStyle);
                if (item.getSubtotal() != null) costoTotal = costoTotal.add(item.getSubtotal());
            }

            Row totalRow = sheet.createRow(rowNum + 1);
            CellStyle bold = buildBoldStyle(wb);
            Cell lbl = totalRow.createCell(4);
            lbl.setCellValue("TOTAL");
            lbl.setCellStyle(bold);
            setMoney(totalRow, 5, costoTotal, moneyStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de shipping order", e);
        }
    }

    private void setMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private BigDecimal ingresoMovimiento(VentaResponseDTO movimiento) {
        if (movimiento.getMontoMovimiento() != null) return movimiento.getMontoMovimiento();
        if (movimiento.getMontoPagado() != null) return movimiento.getMontoPagado();
        return movimiento.getTotalFinal() != null ? movimiento.getTotalFinal() : BigDecimal.ZERO;
    }

    private BigDecimal gananciaMovimiento(VentaResponseDTO movimiento) {
        if ("PAGO_DEUDA".equals(movimiento.getTipoMovimiento())) return null;
        return movimiento.getGananciaNeta();
    }

    private String descripcionMovimiento(VentaResponseDTO movimiento) {
        if ("PAGO_DEUDA".equals(movimiento.getTipoMovimiento())) {
            return movimiento.getDescripcionMovimiento();
        }
        if (movimiento.getDetalles() != null && !movimiento.getDetalles().isEmpty()) {
            if (movimiento.getDetalles().size() > 1) {
                return movimiento.getDetalles().stream()
                        .map(d -> d.getArtista() != null ? d.getArtista() : d.getDescripcion())
                        .filter(s -> s != null && !s.isBlank())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("Varios ítems");
            }
            var detalle = movimiento.getDetalles().get(0);
            return detalle.getManualItem() != null && detalle.getManualItem()
                    ? detalle.getDescripcion()
                    : detalle.getArtista();
        }
        return movimiento.getArtista();
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle buildMoneyStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0.00"));
        return s;
    }

    private CellStyle buildBoldStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        return s;
    }

    private String orEmpty(String s) {
        return s != null ? s : "";
    }
}
