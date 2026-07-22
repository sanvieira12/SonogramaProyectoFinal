package com.sonograma.service;

import com.sonograma.dto.GastoTiendaDTO;
import com.sonograma.dto.ItemResumenMensualDTO;
import com.sonograma.dto.ResumenFinancieroMensualDTO;
import com.sonograma.dto.VentaResumenMensualDTO;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResumenFinancieroMensualPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ResumenFinancieroMensualService resumenService;

    public byte[] generar(String periodo) {
        ResumenFinancieroMensualDTO resumen = resumenService.obtener(periodo);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document);
            writer.title("SONOGRAMA");
            writer.line("Resumen financiero mensual - " + resumen.getPeriodo());
            writer.line("Generado: " + DATE_TIME.format(LocalDateTime.now(ZoneId.of("America/Montevideo"))));
            writer.line("");
            writer.heading("RESUMEN MENSUAL");
            writer.line("Ventas: " + resumen.getCantidadVentas());
            writer.line("Ítems vendidos: " + resumen.getCantidadItems());
            writer.line("Total ventas: " + money(resumen.getTotalVentas()));
            writer.line("Ingresos registrados: " + money(resumen.getIngresosRegistrados()));
            writer.line("Ganancia de ítems: " + signedMoney(resumen.getGananciaItems(), null));
            writer.line("Gastos: " + money(resumen.getGastos()));
            writer.line("Balance final: " + money(resumen.getBalanceFinal()));
            if (resumen.getAdvertenciaGanancia() != null) {
                writer.line("");
                writer.line("ADVERTENCIA: " + resumen.getAdvertenciaGanancia());
            }

            writer.heading("DETALLE DE VENTAS");
            if (resumen.getVentas().isEmpty()) writer.line("Sin ventas válidas en el período.");
            for (VentaResumenMensualDTO venta : resumen.getVentas()) {
                writer.line(String.format("%s | %s | Recibo: %s | Pago: %s", date(venta.getFecha()), text(venta.getCliente()), text(venta.getNumeroRecibo()), text(venta.getEstadoPago())));
                writer.line("  Total: " + money(venta.getTotalVenta()) + " | Recibido: " + money(venta.getMontoRecibido()) + " | Deuda: " + money(venta.getDeudaPendiente()) + " | Ganancia: " + signedMoney(venta.getGananciaNeta(), venta.getEstadoGanancia()));
            }

            writer.heading("DETALLE DE ÍTEMS VENDIDOS");
            if (resumen.getItems().isEmpty()) writer.line("Sin ítems válidos en el período.");
            for (ItemResumenMensualDTO item : resumen.getItems()) {
                writer.line(String.format("%s - %s | Código: %s | Cant.: %s", text(item.getArtista()), text(item.getAlbum()), text(item.getCodigoInterno()), item.getCantidad()));
                writer.line("  Venta: " + moneyOrUnavailable(item.getImporteVentaReal(), null) + " | Costo original: " + moneyOrUnavailable(item.getCostoAdquisicionOriginal(), null) + " | Ganancia: " + signedMoney(item.getGananciaNeta(), item.getEstadoGanancia()));
            }

            writer.heading("GASTOS TIENDA");
            if (resumen.getGastosDetalle().isEmpty()) writer.line("Sin gastos en el período.");
            for (GastoTiendaDTO gasto : resumen.getGastosDetalle()) {
                writer.line(date(gasto.getFecha()) + " | " + text(gasto.getDescripcion()) + " | " + money(gasto.getMonto()));
            }
            writer.closePage();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el reporte mensual", exception);
        }
    }

    private String money(BigDecimal value) {
        if (value == null) return "—";
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("es", "UY"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "UYU $" + format.format(value.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private String moneyOrUnavailable(BigDecimal value, String status) {
        return value == null || "UNAVAILABLE".equals(status) ? "No disponible" : money(value);
    }

    private String signedMoney(BigDecimal value, String status) {
        if (value == null || "UNAVAILABLE".equals(status)) return "No disponible";
        int comparison = value.compareTo(BigDecimal.ZERO);
        if (comparison > 0) return "+ " + money(value);
        if (comparison < 0) return "- " + money(value.abs());
        return money(BigDecimal.ZERO);
    }

    private String date(java.time.LocalDate value) { return value == null ? "—" : DATE.format(value.atStartOfDay()); }
    private String date(LocalDateTime value) { return value == null ? "—" : DATE_TIME.format(value); }
    private String text(String value) { return value == null || value.isBlank() ? "—" : value; }

    private static final class Writer {
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private Writer(PDDocument document) throws IOException { this.document = document; newPage(); }
        private void newPage() throws IOException {
            page = new PDPage(); document.addPage(page);
            stream = new PDPageContentStream(document, page); y = 770;
        }
        private void title(String text) throws IOException { write(text, bold, 18); y -= 8; }
        private void heading(String text) throws IOException { y -= 8; write(text, bold, 12); }
        private void line(String text) throws IOException {
            if (y < 45) { stream.close(); newPage(); }
            String value = text == null ? "" : text;
            for (String chunk : wrap(value, 112)) { write(chunk, regular, 9); }
        }
        private void write(String text, PDType1Font font, float size) throws IOException {
            stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(40, y); stream.showText(text); stream.endText(); y -= size + 4;
        }
        private void closePage() throws IOException { stream.close(); }
        private List<String> wrap(String value, int max) {
            if (value.length() <= max) return List.of(value);
            List<String> lines = new ArrayList<>();
            String remaining = value;
            while (remaining.length() > max) { int cut = remaining.lastIndexOf(' ', max); if (cut <= 0) cut = max; lines.add(remaining.substring(0, cut)); remaining = remaining.substring(cut).trim(); }
            lines.add(remaining); return lines;
        }
    }
}
