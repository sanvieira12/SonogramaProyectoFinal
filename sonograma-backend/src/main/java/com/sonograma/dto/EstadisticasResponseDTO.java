package com.sonograma.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadisticasResponseDTO {
    private List<EstadisticaItemDTO> aniosMusicaMasVendidos;
    private List<EstadisticaItemDTO> generosMasVendidos;
    private List<EstadisticaItemDTO> artistasMasVendidos;
    private List<EstadisticaItemDTO> sellosMasVendidos;
    private List<EstadisticaItemDTO> ventasPorMes;
    private List<EstadisticaItemDTO> ventasPorSemana;
    private List<EstadisticaItemDTO> ventasPorAnio;
    private List<EstadisticaItemDTO> gananciaPorSemana;
    private List<EstadisticaItemDTO> gananciaPorMes;
    private List<EstadisticaItemDTO> gananciaPorAnio;
    private List<EstadisticaItemDTO> inventarioPorEstado;
    private List<EstadisticaItemDTO> inventarioPorGenero;
    private List<EstadisticaItemDTO> inventarioPorAnio;
    private List<EstadisticaItemDTO> inventarioPorSello;
}
