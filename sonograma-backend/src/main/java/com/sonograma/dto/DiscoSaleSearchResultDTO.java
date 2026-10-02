package com.sonograma.dto;

import java.math.BigDecimal;
import java.util.List;

/** Lightweight product result used only by Nueva Venta. */
public record DiscoSaleSearchResultDTO(
        Long idDisco,
        String artista,
        String album,
        Integer anio,
        String codigoInterno,
        String imagenUrl,
        String condicion,
        String condicionFisica,
        String tipoDisco,
        String formato,
        String selloDiscografico,
        BigDecimal precioVenta,
        String estado,
        Boolean requiresExactCopySelection,
        Integer availableCopyCount,
        List<DiscoSaleSearchCopyDTO> availableCopies
) {}
