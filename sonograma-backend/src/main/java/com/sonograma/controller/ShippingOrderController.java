package com.sonograma.controller;

import com.sonograma.dto.ShippingOrderRequestDTO;
import com.sonograma.dto.ShippingOrderResponseDTO;
import com.sonograma.service.ShippingOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shipping-orders")
@RequiredArgsConstructor
public class ShippingOrderController {

    private final ShippingOrderService shippingOrderService;

    @GetMapping
    public List<ShippingOrderResponseDTO> listar() {
        return shippingOrderService.obtenerTodas();
    }

    @GetMapping("/{id}")
    public ShippingOrderResponseDTO obtener(@PathVariable Long id) {
        return shippingOrderService.obtenerPorId(id);
    }

    @PostMapping
    public ResponseEntity<ShippingOrderResponseDTO> crear(@RequestBody ShippingOrderRequestDTO dto) {
        return ResponseEntity.ok(shippingOrderService.crear(dto));
    }

    @GetMapping("/{id}/exportar")
    public ResponseEntity<byte[]> exportar(@PathVariable Long id) {
        ShippingOrderResponseDTO order = shippingOrderService.obtenerPorId(id);
        byte[] bytes = shippingOrderService.exportarExcel(id);
        String filename = "orden-" + order.getNumero().replace("/", "-") + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
