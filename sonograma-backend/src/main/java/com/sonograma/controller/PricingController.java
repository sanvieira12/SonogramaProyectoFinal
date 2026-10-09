package com.sonograma.controller;

import com.sonograma.dto.*;
import com.sonograma.service.CatalogPricingService;
import com.sonograma.service.StockValuationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final CatalogPricingService catalogPricingService;
    private final StockValuationService stockValuationService;

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

    @GetMapping("/stock-valuation")
    public ResponseEntity<StockValuationDTO> stockValuation() {
        return ResponseEntity.ok(stockValuationService.current());
    }

    @PostMapping("/apply")
    public ResponseEntity<PricingApplyResponseDTO> apply(@Valid @RequestBody PricingApplyRequestDTO request) {
        return ResponseEntity.ok(catalogPricingService.apply(request));
    }

    @PatchMapping("/discs/{id}/markup")
    public ResponseEntity<PricingMarkupUpdateResponseDTO> updateMarkup(
        @PathVariable Long id,
        @Valid @RequestBody PricingMarkupUpdateRequestDTO request
    ) {
        return ResponseEntity.ok(catalogPricingService.updateDiscMarkup(id, request));
    }

    @PostMapping("/reset")
    public ResponseEntity<PricingSettingsDTO> reset() {
        return ResponseEntity.ok(catalogPricingService.resetToDefaults());
    }
}
