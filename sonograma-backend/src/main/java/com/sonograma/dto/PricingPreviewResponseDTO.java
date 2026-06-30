package com.sonograma.dto;

import java.util.List;

public record PricingPreviewResponseDTO(
    PricingSettingsDTO settings,
    List<PricingPreviewRowDTO> rows
) {}
