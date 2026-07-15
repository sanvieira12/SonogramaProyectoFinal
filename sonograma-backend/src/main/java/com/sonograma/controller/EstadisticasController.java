package com.sonograma.controller;

import com.sonograma.dto.EstadisticasResponseDTO;
import com.sonograma.dto.IngresoSerieResponseDTO;
import com.sonograma.service.EstadisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/estadisticas")
@RequiredArgsConstructor
public class EstadisticasController {

    private final EstadisticasService estadisticasService;

    @GetMapping("/catalogo")
    public ResponseEntity<EstadisticasResponseDTO> catalogoInventarioVentas() {
        return ResponseEntity.ok(estadisticasService.obtenerCatalogoInventarioVentas());
    }

    @GetMapping("/ingresos")
    public ResponseEntity<IngresoSerieResponseDTO> ingresos(@RequestParam("periodo") String periodo) {
        return ResponseEntity.ok(estadisticasService.obtenerSerieIngresos(periodo));
    }
}
