package com.sonograma.dto;

public record PricingApplyResponseDTO(
    PricingSettingsDTO settings,
    int updatedCount
) {}
