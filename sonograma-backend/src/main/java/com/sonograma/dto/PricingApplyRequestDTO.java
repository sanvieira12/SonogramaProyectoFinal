package com.sonograma.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PricingApplyRequestDTO(
    @NotNull @Valid
    PricingSettingsUpdateDTO settings,
    @NotBlank
    String scope,
    List<Long> selectedIds
) {}
