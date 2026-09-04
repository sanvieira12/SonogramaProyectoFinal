package com.sonograma.controller;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaResponseDTO;
import com.sonograma.dto.PreVentaPagoUpdateRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
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

    @PostMapping("/{id}/marcar-pagada")
    public ResponseEntity<PreVentaResponseDTO> marcarPagada(@PathVariable Long id) {
        return ResponseEntity.ok(service.marcarPagada(id));
    }

    @PutMapping("/{id}/pago")
    public ResponseEntity<VentaResponseDTO> actualizarPago(
            @PathVariable Long id,
            @Valid @RequestBody PreVentaPagoUpdateRequestDTO request) {
        return ResponseEntity.ok(service.actualizarPago(id, request));
    }

    @DeleteMapping("/{id}/pago")
    public ResponseEntity<Void> eliminarPago(@PathVariable Long id) {
        service.eliminarPago(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
