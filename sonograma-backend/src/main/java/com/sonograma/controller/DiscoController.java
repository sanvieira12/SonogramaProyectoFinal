package com.sonograma.controller;

import com.sonograma.dto.DiscoDTO;
import com.sonograma.entity.Disco;
import com.sonograma.service.DiscoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/discos")
@RequiredArgsConstructor
public class DiscoController {

    private final DiscoService discoService;

    @GetMapping
    public ResponseEntity<List<DiscoDTO>> obtenerTodos() {
        return ResponseEntity.ok(discoService.obtenerTodos());
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<DiscoDTO>> obtenerDisponibles() {
        return ResponseEntity.ok(discoService.obtenerDisponibles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DiscoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(discoService.obtenerPorId(id));
    }

    @GetMapping("/qr/{codigoQr}")
    public ResponseEntity<DiscoDTO> obtenerPorQR(@PathVariable String codigoQr) {
        return ResponseEntity.ok(discoService.obtenerPorQR(codigoQr));
    }

    @GetMapping("/buscar/artista")
    public ResponseEntity<List<DiscoDTO>> buscarPorArtista(@RequestParam String q) {
        return ResponseEntity.ok(discoService.buscarPorArtista(q));
    }

    @GetMapping("/buscar/album")
    public ResponseEntity<List<DiscoDTO>> buscarPorAlbum(@RequestParam String q) {
        return ResponseEntity.ok(discoService.buscarPorAlbum(q));
    }

    @PostMapping
    public ResponseEntity<DiscoDTO> crearDisco(@RequestBody Disco disco) {
        return ResponseEntity.status(HttpStatus.CREATED).body(discoService.crearDisco(disco));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DiscoDTO> actualizarDisco(@PathVariable Long id, @RequestBody Disco disco) {
        return ResponseEntity.ok(discoService.actualizarDisco(id, disco));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<DiscoDTO> cambiarEstado(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(discoService.cambiarEstado(id, body.get("estado")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarDisco(@PathVariable Long id) {
        discoService.eliminarDisco(id);
        return ResponseEntity.noContent().build();
    }
}
