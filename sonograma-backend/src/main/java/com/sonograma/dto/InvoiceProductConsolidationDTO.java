package com.sonograma.dto;

import java.util.List;

public record InvoiceProductConsolidationDTO(
    String codigoCatalogo,
    String artista,
    String album,
    List<Integer> sourceRows,
    List<Integer> quantities,
    int totalQuantity
) {}
