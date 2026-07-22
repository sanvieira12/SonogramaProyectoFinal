package com.sonograma.service;

import com.sonograma.entity.GastoTienda;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResumenFinancieroMensualPdfServiceTest {

    @Test
    void generaReporteConResumenYAdvertenciaDeCostoFaltante() throws Exception {
        ResumenFinancieroMensualService summary = mock(ResumenFinancieroMensualService.class);
        when(summary.obtener(anyString())).thenReturn(com.sonograma.dto.ResumenFinancieroMensualDTO.builder()
                .periodo("2026-06").cantidadVentas(1L).cantidadItems(2L)
                .totalVentas(new BigDecimal("1000")).ingresosRegistrados(new BigDecimal("500"))
                .gananciaItems(new BigDecimal("100")).gastos(new BigDecimal("50"))
                .balanceFinal(new BigDecimal("450")).itemsGananciaNoDisponible(1)
                .advertenciaGanancia("1 ítem sin costo histórico")
                .ventas(List.of()).items(List.of()).gastosDetalle(List.of(
                        com.sonograma.dto.GastoTiendaDTO.builder().fecha(LocalDate.of(2026, 6, 5)).descripcion("Luz").monto(new BigDecimal("50")).build()))
                .build());

        byte[] pdf = new ResumenFinancieroMensualPdfService(summary).generar("2026-06");
        String text = new PDFTextStripper().getText(Loader.loadPDF(pdf));

        assertThat(pdf).isNotEmpty();
        assertThat(text).contains("SONOGRAMA", "2026-06", "Ingresos registrados", "Balance final", "1 ítem sin costo histórico", "Luz");
    }
}
