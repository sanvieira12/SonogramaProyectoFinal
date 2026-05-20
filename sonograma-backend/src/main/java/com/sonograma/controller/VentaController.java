package com.sonograma.controller;

import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.dto.VentasPorMesDTO;
import com.sonograma.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    @GetMapping
    public ResponseEntity<List<VentaResponseDTO>> obtenerTodas() {
        return ResponseEntity.ok(ventaService.obtenerTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(ventaService.obtenerPorId(id));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<VentaResponseDTO>> obtenerPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(ventaService.obtenerPorCliente(idCliente));
    }

    @GetMapping("/estadisticas/por-mes")
    public ResponseEntity<List<VentasPorMesDTO>> estadisticasPorMes() {
        return ResponseEntity.ok(ventaService.obtenerEstadisticasPorMes());
    }

    @PostMapping
    public ResponseEntity<VentaResponseDTO> registrarVenta(@Valid @RequestBody VentaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.registrarVenta(dto));
    }
}
