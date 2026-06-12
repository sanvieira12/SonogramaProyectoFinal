package com.sonograma.dto;

import java.math.BigDecimal;

public record PedidoItemResponseDTO(
    Long idPedidoItem,
    String codigo,
    String artista,
    String titulo,
    String formato,
    BigDecimal precioUnitarioEur,
    Integer cantidad,
    BigDecimal totalLineaEur,
    String tipo,
    BigDecimal extraCostoEur,
    BigDecimal costoRealEur,
    BigDecimal costoRealUyu,
    BigDecimal markup,
    BigDecimal precioFinalUyu,
    String portadaUrl,
    Long idDisco,
    String enrichStatus
) {}
