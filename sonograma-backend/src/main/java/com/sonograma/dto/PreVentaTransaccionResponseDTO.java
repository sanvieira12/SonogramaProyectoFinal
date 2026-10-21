package com.sonograma.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreVentaTransaccionResponseDTO {
    private Long idPreVentaTransaccion;
    private Long idCliente;
    private String clienteNombre;
    private LocalDate fecha;
    private String notas;
    private List<PreVentaResponseDTO> items;
    private Integer cantidadItems;
    private Integer cantidadUnidades;
    private BigDecimal total;
    private BigDecimal pagado;
    private BigDecimal pendiente;
    private String estadoPago;
}
