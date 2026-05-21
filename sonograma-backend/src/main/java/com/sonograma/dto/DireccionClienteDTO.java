package com.sonograma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionClienteDTO {
    private Long idDireccion;

    @NotBlank(message = "La dirección es obligatoria")
    @Size(max = 255)
    private String direccion;

    @Size(max = 80)
    private String departamento;

    @Size(max = 255)
    private String referencia;

    private LocalDateTime fechaAlta;
    private LocalDateTime ultimaUsada;
}
