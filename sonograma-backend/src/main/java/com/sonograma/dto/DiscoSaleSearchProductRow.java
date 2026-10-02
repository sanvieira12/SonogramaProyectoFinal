package com.sonograma.dto;

import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;

import java.math.BigDecimal;

/** Database projection used only to assemble the bounded sale-search response. */
public record DiscoSaleSearchProductRow(
        Long idDisco,
        String artista,
        String album,
        Integer anio,
        String codigoInterno,
        String imagenUrl,
        CondicionDisco condicion,
        String condicionFisica,
        TipoDisco tipoDisco,
        String formato,
        String selloDiscografico,
        BigDecimal precioVenta,
        EstadoDisco estado
) {
}
