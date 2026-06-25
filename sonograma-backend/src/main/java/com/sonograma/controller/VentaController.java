package com.sonograma.controller;

import com.sonograma.dto.ConfiguracionCostosDTO;
import com.sonograma.dto.VentaRequestDTO;
import com.sonograma.dto.VentaResponseDTO;
import com.sonograma.dto.VentasPorMesDTO;
import com.sonograma.service.ExcelExportService;
import com.sonograma.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final ExcelExportService excelExportService;

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

    @GetMapping("/configuracion-costos")
    public ResponseEntity<ConfiguracionCostosDTO> configuracionCostos() {
        return ResponseEntity.ok(ventaService.obtenerConfiguracionCostos());
    }

    @PostMapping
    public ResponseEntity<VentaResponseDTO> registrarVenta(@Valid @RequestBody VentaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.registrarVenta(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VentaResponseDTO> actualizarVenta(
            @PathVariable Long id,
            @Valid @RequestBody VentaRequestDTO dto) {
        return ResponseEntity.ok(ventaService.actualizarVenta(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelarVenta(@PathVariable Long id) {
        ventaService.cancelarVenta(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/libro")
    public ResponseEntity<List<VentaResponseDTO>> libro(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String canal,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ventaService.obtenerLibro(desde, hasta, canal, q));
    }

    @GetMapping("/libro/exportar")
    public ResponseEntity<byte[]> exportarLibro(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String canal,
            @RequestParam(required = false) String q) {
        byte[] bytes = excelExportService.exportarLibroVentas(
                ventaService.obtenerVentasParaExportar(desde, hasta, canal, q));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"libro-ventas.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
