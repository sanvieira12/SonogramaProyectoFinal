package com.sonograma.dto;

import java.math.BigDecimal;

public record ManualDiscogsDuplicateCopyDTO(
        Long copyId,
        Integer copyNumber,
        String estado,
        String condicionFisica,
        BigDecimal precioVenta
) {}
