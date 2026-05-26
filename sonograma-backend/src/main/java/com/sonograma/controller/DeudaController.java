package com.sonograma.controller;

import com.sonograma.dto.DeudaResponseDTO;
import com.sonograma.service.DeudaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/deudas")
@RequiredArgsConstructor
public class DeudaController {

    private final DeudaService deudaService;

    @GetMapping
    public List<DeudaResponseDTO> listar(@RequestParam(required = false) String q) {
        return deudaService.obtenerPendientes(q);
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        return deudaService.obtenerResumen();
    }

    @GetMapping("/cliente/{idCliente}")
    public List<DeudaResponseDTO> porCliente(@PathVariable Long idCliente) {
        return deudaService.obtenerPorCliente(idCliente);
    }

    @PostMapping("/{idDeuda}/registrar-pago")
    public ResponseEntity<DeudaResponseDTO> registrarPago(
            @PathVariable Long idDeuda,
            @RequestBody Map<String, Object> body) {
        BigDecimal monto = new BigDecimal(body.get("monto").toString());
        String notas = body.containsKey("notas") ? body.get("notas").toString() : null;
        return ResponseEntity.ok(deudaService.registrarPago(idDeuda, monto, notas));
    }
}
