package com.sonograma.controller;

import com.sonograma.dto.GastoTiendaDTO;
import com.sonograma.dto.GastoTiendaResumenDTO;
import com.sonograma.service.GastoTiendaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/gastos-tienda")
@RequiredArgsConstructor
public class GastoTiendaController {

    private final GastoTiendaService service;

    @GetMapping
    public ResponseEntity<List<GastoTiendaDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/resumen")
    public ResponseEntity<GastoTiendaResumenDTO> resumen() {
        return ResponseEntity.ok(service.resumenMesActual());
    }

    @PostMapping
    public ResponseEntity<GastoTiendaDTO> crear(@RequestBody GastoTiendaDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GastoTiendaDTO> actualizar(@PathVariable Long id, @RequestBody GastoTiendaDTO request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
