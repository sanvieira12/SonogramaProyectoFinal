package com.sonograma.controller;

import com.sonograma.dto.AudioPreviewDTO;
import com.sonograma.dto.AudioPreviewRequestDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.service.AudioPreviewService;
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
    private final AudioPreviewService audioPreviewService;

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

    @PatchMapping("/{id}/copias")
    public ResponseEntity<DiscoResponseDTO> actualizarCopias(
            @PathVariable Long id,
            @RequestParam("cantidad") Integer cantidad) {
        return ResponseEntity.ok(discoService.actualizarCopias(id, cantidad));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarDisco(@PathVariable Long id) {
        discoService.eliminarDisco(id);
        return ResponseEntity.noContent().build();
    }

    // ── Audio previews ────────────────────────────────────────────────────────

    @GetMapping("/{id}/previews")
    public ResponseEntity<List<AudioPreviewDTO>> listarPreviews(@PathVariable Long id) {
        return ResponseEntity.ok(audioPreviewService.listarPorDisco(id));
    }

    @PostMapping("/{id}/previews")
    public ResponseEntity<AudioPreviewDTO> agregarPreview(
            @PathVariable Long id,
            @RequestBody AudioPreviewRequestDTO req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(audioPreviewService.agregar(id, req));
    }

    @PatchMapping("/{id}/previews/{previewId}/url")
    public ResponseEntity<AudioPreviewDTO> actualizarUrlPreview(
            @PathVariable Long id,
            @PathVariable Long previewId,
            @RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(audioPreviewService.actualizarUrl(previewId, body.get("audioUrl")));
    }

    @DeleteMapping("/{id}/previews/{previewId}")
    public ResponseEntity<Void> eliminarPreview(
            @PathVariable Long id,
            @PathVariable Long previewId) {
        audioPreviewService.eliminar(previewId);
        return ResponseEntity.noContent().build();
    }
}
