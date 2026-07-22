package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumenFinancieroMensualDTO {
    private String periodo;
    private LocalDate desde;
    private LocalDate hasta;
    private Long cantidadVentas;
    private Long cantidadItems;
    private BigDecimal totalVentas;
    private BigDecimal ingresosRegistrados;
    private BigDecimal gananciaItems;
    private BigDecimal gastos;
    private BigDecimal balanceFinal;
    private Integer itemsGananciaNoDisponible;
    private String advertenciaGanancia;
    private List<VentaResumenMensualDTO> ventas;
    private List<ItemResumenMensualDTO> items;
    private List<GastoTiendaDTO> gastosDetalle;
}
