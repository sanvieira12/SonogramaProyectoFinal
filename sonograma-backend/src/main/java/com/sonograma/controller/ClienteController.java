package com.sonograma.controller;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.dto.ClienteDetalleDTO;
import com.sonograma.dto.ClienteRequest;
import com.sonograma.dto.DireccionClienteDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteRepository clienteRepository;

    @GetMapping
    public ResponseEntity<List<ClienteDTO>> obtenerTodos() {
        return ResponseEntity.ok(clienteService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @GetMapping("/{id}/detalle")
    public ResponseEntity<ClienteDetalleDTO> obtenerDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerDetalle(id));
    }

    @GetMapping("/{id}/direcciones")
    public ResponseEntity<List<DireccionClienteDTO>> obtenerDirecciones(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerDirecciones(id));
    }

    @GetMapping("/cedula/{cedula}")
    public ResponseEntity<ClienteDTO> obtenerPorCedula(@PathVariable String cedula) {
        return ResponseEntity.ok(clienteService.obtenerPorCedula(cedula));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteDTO>> buscar(@RequestParam String q) {
        return ResponseEntity.ok(clienteService.buscar(q));
    }

    @PostMapping
    public ResponseEntity<ClienteDTO> crearCliente(@Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crearCliente(request));
    }

    @PostMapping("/{id}/direcciones")
    public ResponseEntity<DireccionClienteDTO> crearDireccion(@PathVariable Long id,
                                                              @Valid @RequestBody DireccionClienteDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crearDireccion(id, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteDTO> actualizarCliente(@PathVariable Long id,
                                                         @Valid @RequestBody ClienteRequest request) {
        return ResponseEntity.ok(clienteService.actualizarCliente(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
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
                String h = c.getStringCellValue().trim().toLowerCase()
                    .replace("ó","o").replace("é","e").replace("á","a").replace("í","i").replace("ú","u");
                cols.put(h, c.getColumnIndex());
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String nombre = str(row, cols.get("nombre"));
                    if (nombre == null || nombre.isBlank()) { omitidos++; continue; }
                    String cedula = str(row, cols.get("cedula"));
                    if (cedula != null && !cedula.isBlank()
                            && clienteRepository.findByCedulaAndActivoTrue(cedula).isPresent()) {
                        omitidos++; continue;
                    }
                    Cliente c = new Cliente();
                    c.setNombre(nombre);
                    c.setApellido(str(row, cols.get("apellido")));
                    c.setCedula(cedula);
                    c.setEmail(str(row, cols.get("email")));
                    c.setTelefono(str(row, cols.get("telefono")));
                    c.setInstagramUsuario(str(row, cols.getOrDefault("instagram", cols.get("instagram_usuario"))));
                    c.setDireccion(str(row, cols.get("direccion")));
                    c.setActivo(true);
                    clienteRepository.save(c);
                    creados++;
                } catch (Exception e) {
                    errores.add("Fila " + (i + 1) + ": " + e.getMessage());
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
}
