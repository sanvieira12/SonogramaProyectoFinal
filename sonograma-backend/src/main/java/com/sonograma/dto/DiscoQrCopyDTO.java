package com.sonograma.dto;

public record DiscoQrCopyDTO(
    Long id,
    Integer copyNumber,
    String codigoQr,
    String estado,
    String content,
    String imageUrl
) {}
