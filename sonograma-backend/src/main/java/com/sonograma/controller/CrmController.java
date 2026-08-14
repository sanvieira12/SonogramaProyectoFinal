package com.sonograma.controller;

import com.sonograma.dto.crm.CrmDtos;
import com.sonograma.service.crm.CrmInterestService;
import com.sonograma.service.crm.CrmProfileService;
import com.sonograma.service.crm.CrmRecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/crm")
@RequiredArgsConstructor
public class CrmController {

    private final CrmProfileService profileService;
    private final CrmInterestService interestService;
    private final CrmRecommendationService recommendationService;

    @GetMapping("/clientes/{clienteId}/perfil")
    public CrmDtos.PerfilCliente profile(@PathVariable Long clienteId) {
        return profileService.profileDto(clienteId);
    }

    @GetMapping("/clientes/{clienteId}/recomendaciones")
    public List<CrmDtos.Recomendacion> recommendations(@PathVariable Long clienteId,
                                                        @RequestParam(required = false) Integer limit) {
        return recommendationService.recommendations(clienteId, limit);
    }

    @GetMapping("/clientes/{clienteId}/intereses")
    public List<CrmDtos.Interes> interests(@PathVariable Long clienteId) {
        return interestService.list(clienteId);
    }

    @PostMapping("/clientes/{clienteId}/intereses")
    public ResponseEntity<CrmDtos.Interes> createInterest(@PathVariable Long clienteId,
                                                          @Valid @RequestBody CrmDtos.InteresRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(interestService.create(clienteId, request));
    }

    @PatchMapping("/clientes/{clienteId}/intereses/{interesId}")
    public CrmDtos.Interes updateInterest(@PathVariable Long clienteId,
                                          @PathVariable Long interesId,
                                          @Valid @RequestBody CrmDtos.InteresEstadoRequest request) {
        return interestService.setActive(clienteId, interesId, request.activo());
    }

    @GetMapping("/discos/{discoId}/clientes-recomendados")
    public List<CrmDtos.ClienteAfin> recommendedCustomers(@PathVariable Long discoId,
                                                          @RequestParam(required = false) Integer limit) {
        return recommendationService.recommendedCustomers(discoId, limit);
    }
}
