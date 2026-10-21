package com.sonograma.controller;

import com.sonograma.dto.*;
import com.sonograma.service.PreVentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pre-venta-transacciones")
@RequiredArgsConstructor
public class PreVentaTransaccionController {

    private final PreVentaService service;

    @GetMapping
    public ResponseEntity<List<PreVentaTransaccionResponseDTO>> listar() {
        return ResponseEntity.ok(service.listarTransacciones());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PreVentaTransaccionResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtenerTransaccion(id));
    }

    @PostMapping
    public ResponseEntity<PreVentaTransaccionResponseDTO> crear(
            @Valid @RequestBody PreVentaTransaccionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTransaccion(request));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<PreVentaTransaccionResponseDTO> agregarItem(
            @PathVariable Long id,
            @Valid @RequestBody PreVentaTransaccionItemRequestDTO request) {
        return ResponseEntity.ok(service.agregarItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<PreVentaTransaccionResponseDTO> actualizarItem(
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Valid @RequestBody PreVentaItemUpdateRequestDTO request) {
        return ResponseEntity.ok(service.actualizarItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<PreVentaTransaccionResponseDTO> eliminarItem(
            @PathVariable Long id,
            @PathVariable Long itemId) {
        return ResponseEntity.ok(service.eliminarItem(id, itemId));
    }

    @PostMapping("/{id}/pagar-pendientes")
    public ResponseEntity<PreVentaTransaccionResponseDTO> pagarPendientes(@PathVariable Long id) {
        return ResponseEntity.ok(service.pagarPendientes(id));
    }
}
