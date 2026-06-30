package com.sonograma.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PricingApplyRequestDTO(
    @NotNull @Valid
    PricingSettingsUpdateDTO settings,
    @NotBlank
    String scope
) {}
