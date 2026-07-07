package com.sonograma.controller;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaResponseDTO;
import com.sonograma.service.PreVentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pre-ventas")
@RequiredArgsConstructor
public class PreVentaController {

    private final PreVentaService service;

    @GetMapping
    public ResponseEntity<List<PreVentaResponseDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping
    public ResponseEntity<PreVentaResponseDTO> crear(@Valid @RequestBody PreVentaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }
}
