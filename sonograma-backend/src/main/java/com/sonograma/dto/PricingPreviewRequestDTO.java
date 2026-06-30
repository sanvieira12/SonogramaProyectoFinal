package com.sonograma.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PricingPreviewRequestDTO(
    @NotNull @Valid
    PricingSettingsUpdateDTO settings
) {}
