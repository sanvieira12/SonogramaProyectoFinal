package com.sonograma.controller;

import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.EscaneoQRRequest;
import com.sonograma.service.QRService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/qr")
@RequiredArgsConstructor
public class QRController {

    private final QRService qrService;

    @GetMapping("/descargar/{idDisco}")
    public ResponseEntity<byte[]> descargarQR(@PathVariable Long idDisco) {
        byte[] qrBytes = qrService.descargarQR(idDisco);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition", "attachment; filename=\"qr-" + idDisco + ".png\"")
                .body(qrBytes);
    }

    @GetMapping("/descargar/{idDisco}/{copyNumber}")
    public ResponseEntity<byte[]> descargarQR(
            @PathVariable Long idDisco,
            @PathVariable Integer copyNumber) {
        byte[] qrBytes = qrService.descargarQR(idDisco, copyNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition",
                        "inline; filename=\"qr-" + idDisco + "-" + copyNumber + ".png\"")
                .body(qrBytes);
    }

    @PostMapping("/escanear")
    public ResponseEntity<DiscoResponseDTO> escanearQR(@RequestBody EscaneoQRRequest request) {
        return ResponseEntity.ok(qrService.obtenerPorQRScaneado(request.getCodigoQr()));
    }
}
