package com.sonograma.controller;

import com.sonograma.dto.CambioEstadoRequest;
import com.sonograma.dto.DiscoDTO;
import com.sonograma.dto.DiscoRequest;
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
    public ResponseEntity<List<DiscoDTO>> obtenerTodos() {
        return ResponseEntity.ok(discoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(discoService.obtenerPorId(id));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<DiscoDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(discoService.buscar(q));
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<DiscoDTO>> obtenerPorEstado(@PathVariable EstadoDisco estado) {
        return ResponseEntity.ok(discoService.obtenerPorEstado(estado));
    }

    @PostMapping
    public ResponseEntity<DiscoDTO> crearDisco(@Valid @RequestBody DiscoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discoService.crearDisco(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscoDTO> actualizarDisco(@PathVariable Long id,
                                                      @Valid @RequestBody DiscoRequest request) {
        return ResponseEntity.ok(discoService.actualizarDisco(id, request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<DiscoDTO> cambiarEstado(@PathVariable Long id,
                                                   @Valid @RequestBody CambioEstadoRequest request) {
        return ResponseEntity.ok(discoService.cambiarEstado(id, request.getEstado()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarDisco(@PathVariable Long id) {
        discoService.eliminarDisco(id);
        return ResponseEntity.noContent().build();
    }
}
