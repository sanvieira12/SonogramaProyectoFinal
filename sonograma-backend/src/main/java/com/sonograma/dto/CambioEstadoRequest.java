package com.sonograma.dto;

import com.sonograma.enums.EstadoDisco;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambioEstadoRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoDisco estado;
}
