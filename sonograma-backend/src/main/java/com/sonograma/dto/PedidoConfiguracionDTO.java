package com.sonograma.dto;

import java.math.BigDecimal;

public record PedidoConfiguracionDTO(
    BigDecimal tipoCambio,
    BigDecimal extraCostoSimple,
    BigDecimal extraCostoDoble,
    BigDecimal markupSimple,
    BigDecimal markupDoble
) {}
