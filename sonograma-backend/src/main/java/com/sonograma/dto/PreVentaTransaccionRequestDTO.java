package com.sonograma.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
public class PreVentaTransaccionRequestDTO {
    @NotNull
    private Long idCliente;
    private LocalDate fecha;
    private String notas;

    @NotBlank
    @Size(max = 120)
    private String idempotencyKey;

    @Valid
    @NotEmpty
    private List<PreVentaTransaccionItemRequestDTO> items;
}
