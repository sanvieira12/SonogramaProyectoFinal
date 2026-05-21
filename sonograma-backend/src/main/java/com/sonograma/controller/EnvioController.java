package com.sonograma.controller;

import com.sonograma.dto.CotizacionEnvioDTO;
import com.sonograma.dto.SucursalDacDTO;
import com.sonograma.service.DacService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/envios")
@RequiredArgsConstructor
public class EnvioController {

    private final DacService dacService;

    @GetMapping("/dac/departamentos")
    public ResponseEntity<List<String>> departamentosDac() {
        return ResponseEntity.ok(dacService.obtenerDepartamentos());
    }

    @GetMapping("/dac/sucursales")
    public ResponseEntity<List<SucursalDacDTO>> sucursalesDac(@RequestParam String departamento) {
        return ResponseEntity.ok(dacService.obtenerSucursales(departamento));
    }

    @GetMapping("/dac/cotizar")
    public ResponseEntity<CotizacionEnvioDTO> cotizarDac(@RequestParam String departamento,
                                                         @RequestParam String sucursalCodigo) {
        return ResponseEntity.ok(dacService.cotizar(departamento, sucursalCodigo));
    }
}
