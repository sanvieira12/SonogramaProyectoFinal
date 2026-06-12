package com.sonograma.dto;

public record PedidoUploadResponseDTO(
    Long pedidoId,
    String numeroFactura,
    int itemCount,
    Integer cantidadTotalPdf,
    boolean hayAdvertenciaCantidad
) {}
