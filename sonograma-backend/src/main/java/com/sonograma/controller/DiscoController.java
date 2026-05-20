package com.sonograma.controller;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.service.DiscoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/discos")
@RequiredArgsConstructor
public class DiscoController {

    private final DiscoService discoService;

    @GetMapping
    public ResponseEntity<List<DiscoResponseDTO>> obtenerTodos() {
        return ResponseEntity.ok(discoService.obtenerTodos());
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<DiscoResponseDTO>> obtenerDisponibles() {
        return ResponseEntity.ok(discoService.obtenerDisponibles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscoResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(discoService.obtenerPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<DiscoResponseDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(discoService.buscar(q));
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<DiscoResponseDTO>> obtenerPorEstado(@PathVariable EstadoDisco estado) {
        return ResponseEntity.ok(discoService.obtenerPorEstado(estado));
    }

    @PostMapping
    public ResponseEntity<DiscoResponseDTO> crearDisco(@Valid @RequestBody DiscoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discoService.crearDisco(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscoResponseDTO> actualizarDisco(@PathVariable Long id,
                                                             @Valid @RequestBody DiscoRequestDTO request) {
        return ResponseEntity.ok(discoService.actualizarDisco(id, request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<DiscoResponseDTO> cambiarEstado(@PathVariable Long id,
                                                          @RequestParam("nuevoEstado") EstadoDisco nuevoEstado) {
        return ResponseEntity.ok(discoService.cambiarEstado(id, nuevoEstado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarDisco(@PathVariable Long id) {
        discoService.eliminarDisco(id);
        return ResponseEntity.noContent().build();
    }
}
