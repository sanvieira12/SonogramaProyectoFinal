package com.sonograma.controller;

import com.sonograma.dto.DeudaRequestDTO;
import com.sonograma.dto.DeudaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Deuda;
import com.sonograma.enums.EstadoPago;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DeudaRepository;
import com.sonograma.service.DeudaService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/deudas")
@RequiredArgsConstructor
public class DeudaController {

    private final DeudaService deudaService;
    private final ClienteRepository clienteRepository;
    private final DeudaRepository deudaRepository;

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

    @GetMapping("/{idDeuda}")
    public ResponseEntity<DeudaResponseDTO> obtener(@PathVariable Long idDeuda) {
        return ResponseEntity.ok(deudaService.obtenerPorId(idDeuda));
    }

    @PostMapping
    public ResponseEntity<DeudaResponseDTO> crear(@RequestBody DeudaRequestDTO request) {
        return ResponseEntity.ok(deudaService.crear(request));
    }

    @PutMapping("/{idDeuda}")
    public ResponseEntity<DeudaResponseDTO> actualizar(
            @PathVariable Long idDeuda,
            @RequestBody DeudaRequestDTO request) {
        return ResponseEntity.ok(deudaService.actualizar(idDeuda, request));
    }

    @PostMapping("/{idDeuda}/registrar-pago")
    public ResponseEntity<DeudaResponseDTO> registrarPago(
            @PathVariable Long idDeuda,
            @RequestBody Map<String, Object> body) {
        BigDecimal monto = requiredBigDecimal(body, "monto", "El monto del pago es obligatorio");
        String notas = optionalString(body, "notas");
        return ResponseEntity.ok(deudaService.registrarPago(idDeuda, monto, notas));
    }

    @PostMapping("/{idDeuda}/pagos")
    public ResponseEntity<DeudaResponseDTO> registrarPagoAlias(
            @PathVariable Long idDeuda,
            @RequestBody Map<String, Object> body) {
        return registrarPago(idDeuda, body);
    }

    @PostMapping("/importar-excel")
    @Transactional
    public ResponseEntity<Map<String, Object>> importarExcel(@RequestParam MultipartFile file) {
        int creados = 0;
        int omitidos = 0;
        List<String> errores = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return ResponseEntity.badRequest().body(Map.of("error", "Archivo vacío"));

            Map<String, Integer> cols = new HashMap<>();
            for (Cell c : header) {
                String h = normalize(c.getStringCellValue());
                cols.put(h, c.getColumnIndex());
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String nombreCliente = str(row, cols.getOrDefault("nombre_cliente", cols.get("nombre")));
                    String cedula = str(row, cols.get("cedula"));
                    String montoStr = str(row, cols.getOrDefault("monto_total", cols.get("monto")));
                    if (montoStr == null || montoStr.isBlank()) { omitidos++; continue; }

                    Optional<Cliente> clienteOpt = Optional.empty();
                    if (cedula != null && !cedula.isBlank())
                        clienteOpt = clienteRepository.findByCedulaAndActivoTrue(cedula);
                    if (clienteOpt.isEmpty() && nombreCliente != null && !nombreCliente.isBlank()) {
                        String[] parts = nombreCliente.trim().split("\\s+", 2);
                        clienteOpt = clienteRepository
                            .findAll().stream()
                            .filter(c -> c.getActivo() && c.getNombre().equalsIgnoreCase(parts[0])
                                && (parts.length < 2 || (c.getApellido() != null && c.getApellido().equalsIgnoreCase(parts[1]))))
                            .findFirst();
                    }
                    if (clienteOpt.isEmpty()) { errores.add("Fila " + (i+1) + ": cliente no encontrado"); omitidos++; continue; }

                    BigDecimal monto = new BigDecimal(montoStr.replace(",", "."));
                    String notasFila = str(row, cols.get("notas"));
                    LocalDate fecha = LocalDate.now();
                    String fechaStr = str(row, cols.get("fecha"));
                    if (fechaStr != null && !fechaStr.isBlank()) {
                        try { fecha = LocalDate.parse(fechaStr); } catch (DateTimeParseException ignored) {}
                    }

                    Deuda deuda = Deuda.builder()
                        .cliente(clienteOpt.get())
                        .montoTotal(monto)
                        .montoPagado(BigDecimal.ZERO)
                        .montoPendiente(monto)
                        .fechaVenta(fecha)
                        .estadoPago(EstadoPago.PENDIENTE)
                        .notas(notasFila)
                        .build();
                    deudaRepository.save(deuda);
                    creados++;
                } catch (Exception e) {
                    errores.add("Fila " + (i+1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("creados", creados);
        result.put("omitidos", omitidos);
        result.put("errores", errores);
        return ResponseEntity.ok(result);
    }

    private String str(Row row, Integer col) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase()
            .replace("ó","o").replace("é","e").replace("á","a").replace("í","i").replace("ú","u");
    }

    private BigDecimal requiredBigDecimal(Map<String, Object> body, String field, String message) {
        Object raw = body != null ? body.get(field) : null;
        if (raw == null || raw.toString().isBlank()) {
            throw new NegocioException(message);
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new NegocioException("El monto del pago no es válido");
        }
    }

    private String optionalString(Map<String, Object> body, String field) {
        if (body == null) {
            return null;
        }
        Object raw = body.get(field);
        return raw == null ? null : raw.toString();
    }
}
