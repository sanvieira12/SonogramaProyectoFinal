package com.sonograma.controller;

import com.sonograma.dto.*;
import com.sonograma.service.CatalogPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final CatalogPricingService catalogPricingService;

    @GetMapping("/settings")
    public ResponseEntity<PricingSettingsDTO> settings() {
        return ResponseEntity.ok(catalogPricingService.getSettingsDto());
    }

    @PutMapping("/settings")
    public ResponseEntity<PricingSettingsDTO> updateSettings(@Valid @RequestBody PricingSettingsUpdateDTO request) {
        return ResponseEntity.ok(catalogPricingService.updateSettings(request));
    }

    @PostMapping("/preview")
    public ResponseEntity<PricingPreviewResponseDTO> preview(@Valid @RequestBody PricingPreviewRequestDTO request) {
        return ResponseEntity.ok(catalogPricingService.preview(request.settings()));
    }

    @PostMapping("/apply")
    public ResponseEntity<PricingApplyResponseDTO> apply(@Valid @RequestBody PricingApplyRequestDTO request) {
        return ResponseEntity.ok(catalogPricingService.apply(request));
    }

    @PostMapping("/reset")
    public ResponseEntity<PricingSettingsDTO> reset() {
        return ResponseEntity.ok(catalogPricingService.resetToDefaults());
    }
}
