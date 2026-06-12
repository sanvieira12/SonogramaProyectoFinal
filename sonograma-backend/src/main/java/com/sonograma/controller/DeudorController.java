package com.sonograma.controller;

import com.sonograma.dto.DeudorDTO;
import com.sonograma.dto.DeudorImportResultDTO;
import com.sonograma.entity.Deudor;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudorRepository;
import com.sonograma.service.DeudorExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/deudores")
@RequiredArgsConstructor
public class DeudorController {

    private final DeudorRepository deudorRepository;
    private final ClienteRepository clienteRepository;
    private final DeudorExcelImportService deudorExcelImportService;

    @GetMapping
    public ResponseEntity<List<DeudorDTO>> obtenerTodos() {
        List<Deudor> deudores = deudorRepository.findAll();
        return ResponseEntity.ok(deudores.stream().map(this::toDTO).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeudorDTO> obtenerPorId(@PathVariable Long id) {
        Deudor d = deudorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deudor no encontrado: " + id));
        return ResponseEntity.ok(toDTO(d));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeudorDTO> actualizar(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> body) {
        Deudor d = deudorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deudor no encontrado: " + id));
        if (body.containsKey("notas")) d.setNotas((String) body.get("notas"));
        if (body.containsKey("descripcionDiscos")) d.setDescripcionDiscos((String) body.get("descripcionDiscos"));
        if (body.containsKey("estado")) d.setEstado((String) body.get("estado"));
        d.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(toDTO(deudorRepository.save(d)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        Deudor d = deudorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Deudor no encontrado: " + id));
        d.setEstado("CANCELADO");
        d.setUpdatedAt(LocalDateTime.now());
        deudorRepository.save(d);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/importar-excel")
    public ResponseEntity<DeudorImportResultDTO> importarExcel(@RequestParam MultipartFile file) {
        return ResponseEntity.ok(deudorExcelImportService.importarExcel(file));
    }

    private DeudorDTO toDTO(Deudor d) {
        String clienteNombre = null;
        if (d.getIdCliente() != null) {
            clienteNombre = clienteRepository.findById(d.getIdCliente())
                    .map(c -> (c.getNombre() != null ? c.getNombre() : "")
                            + (c.getApellido() != null ? " " + c.getApellido() : "").trim())
                    .orElse(null);
        }
        return DeudorDTO.builder()
                .id(d.getId())
                .nombreDeudor(d.getNombreDeudor())
                .idCliente(d.getIdCliente())
                .clienteNombre(clienteNombre)
                .montoOriginal(d.getMontoOriginal())
                .montoUyu(d.getMontoUyu())
                .fechaEstimada(d.getFechaEstimada())
                .notas(d.getNotas())
                .descripcionDiscos(d.getDescripcionDiscos())
                .estado(d.getEstado())
                .fuente(d.getFuente())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
