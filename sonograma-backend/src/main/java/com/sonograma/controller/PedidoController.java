package com.sonograma.controller;

import com.sonograma.dto.PedidoConfiguracionDTO;
import com.sonograma.dto.PedidoResponseDTO;
import com.sonograma.dto.PedidoUploadResponseDTO;
import com.sonograma.entity.Pedido;
import com.sonograma.service.PedidoService;
import com.sonograma.service.PedidoControlImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
@Slf4j
public class PedidoController {

    private final PedidoService pedidoService;
    private final PedidoControlImportService controlImportService;

    @PostMapping("/upload-pdf")
    public ResponseEntity<PedidoUploadResponseDTO> uploadPdf(@RequestParam MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Upload PDF pedido: '{}' ({} bytes)", file.getOriginalFilename(), file.getSize());
        PedidoUploadResponseDTO response = pedidoService.crearDesdePdf(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/upload-control")
    public ResponseEntity<byte[]> uploadControl(
            @RequestParam("pdf") MultipartFile pdf,
            @RequestParam(required = false) MultipartFile template) {
        if (pdf.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var result = (template != null && !template.isEmpty())
            ? controlImportService.importAndGenerate(pdf, template)
            : controlImportService.importAndGenerate(pdf);
        var generated = result.workbook();
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(generated.filename(), StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.status(HttpStatus.CREATED)
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header("X-Pedido-Id", result.pedidoId().toString())
            .body(generated.content());
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> listar(
            @RequestParam(required = false) String source) {
        if ("vinylfuture".equalsIgnoreCase(source)) {
            return ResponseEntity.ok(pedidoService.listarVinylFuture());
        }
        return ResponseEntity.ok(pedidoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerPorId(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> descargarPdf(@PathVariable Long id) {
        Pedido pedido = pedidoService.obtenerEntidad(id);
        Resource resource = pedidoService.obtenerPdfOriginal(id);
        String filename = pedido.getPdfOriginalFilename() != null ? pedido.getPdfOriginalFilename() : "pedido-" + id + ".pdf";
        ContentDisposition disposition = ContentDisposition.inline()
            .filename(filename, StandardCharsets.UTF_8)
            .build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                pedido.getPdfContentType() != null ? pedido.getPdfContentType() : "application/pdf"))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(resource);
    }

    @PatchMapping("/{id}/configuracion")
    public ResponseEntity<PedidoResponseDTO> configurar(
            @PathVariable Long id,
            @RequestBody PedidoConfiguracionDTO dto) {
        return ResponseEntity.ok(pedidoService.actualizarConfiguracion(id, dto));
    }

    @PostMapping("/{id}/enriquecer")
    public ResponseEntity<Void> enriquecer(@PathVariable Long id) {
        pedidoService.lanzarEnriquecimiento(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/importar-catalogo")
    public ResponseEntity<PedidoResponseDTO> importarCatalogo(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.importarAlCatalogo(id));
    }

    @PostMapping("/{pedidoId}/items/{itemId}/retry-enrich")
    public ResponseEntity<Void> retryItem(
            @PathVariable Long pedidoId,
            @PathVariable Long itemId) {
        pedidoService.reintentarItemEnriquecimiento(itemId);
        return ResponseEntity.accepted().build();
    }
}
