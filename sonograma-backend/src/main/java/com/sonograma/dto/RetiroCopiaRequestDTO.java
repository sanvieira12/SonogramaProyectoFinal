package com.sonograma.dto;

import com.sonograma.enums.DisposicionCopiaReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RetiroCopiaRequestDTO(
        @NotNull(message = "El motivo de retiro es obligatorio") DisposicionCopiaReason reason,
        @Size(max = 2000, message = "La nota de retiro no puede superar 2000 caracteres") String note
) {
}
