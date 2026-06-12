package com.sonograma.dto;

import java.math.BigDecimal;

public record InvoiceItem(
    String codigoCatalogo,
    String artista,
    String album,
    String formato,
    BigDecimal precioUnitario,
    Integer cantidad,
    BigDecimal subtotal
) {}
