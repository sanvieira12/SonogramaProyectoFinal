package com.sonograma.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngresoSerieResponseDTO {
    private String periodo;
    private String etiquetaPeriodo;
    private BigDecimal totalMonto;
    private BigDecimal totalMontoPeriodoAnterior;
    private BigDecimal diferenciaMonto;
    private BigDecimal diferenciaPorcentual;
    private Long cantidadVentas;
    private Long cantidadPagosDeuda;
    private List<IngresoSerieBucketDTO> buckets;
}
