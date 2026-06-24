package com.sonograma.controller;

import com.sonograma.dto.NotaDTO;
import com.sonograma.dto.NotaRequestDTO;
import com.sonograma.service.NotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notas")
@RequiredArgsConstructor
public class NotaController {

    private final NotaService notaService;

    @GetMapping
    public ResponseEntity<List<NotaDTO>> listar(@RequestParam(required = false) String search) {
        return ResponseEntity.ok(notaService.listar(search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotaDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(notaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<NotaDTO> crear(@RequestBody NotaRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notaService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotaDTO> actualizar(@PathVariable Long id, @RequestBody NotaRequestDTO request) {
        return ResponseEntity.ok(notaService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archivar(@PathVariable Long id) {
        notaService.archivar(id);
        return ResponseEntity.noContent().build();
    }
}
