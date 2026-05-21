package com.sonograma.controller;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.dto.ClienteDetalleDTO;
import com.sonograma.dto.ClienteRequest;
import com.sonograma.dto.DireccionClienteDTO;
import com.sonograma.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

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
}
