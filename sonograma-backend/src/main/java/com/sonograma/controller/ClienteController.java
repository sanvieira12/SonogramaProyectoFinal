package com.sonograma.controller;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.service.ClienteService;
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

    @GetMapping("/buscar/nombre")
    public ResponseEntity<List<ClienteDTO>> buscarPorNombre(@RequestParam String q) {
        return ResponseEntity.ok(clienteService.buscarPorNombre(q));
    }

    @GetMapping("/cedula/{cedula}")
    public ResponseEntity<ClienteDTO> obtenerPorCedula(@PathVariable String cedula) {
        return ResponseEntity.ok(clienteService.obtenerPorCedula(cedula));
    }

    @PostMapping
    public ResponseEntity<ClienteDTO> crearCliente(@RequestBody Cliente cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.crearCliente(cliente));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteDTO> actualizarCliente(@PathVariable Long id, @RequestBody Cliente cliente) {
        return ResponseEntity.ok(clienteService.actualizarCliente(id, cliente));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(@PathVariable Long id) {
        clienteService.eliminarCliente(id);
        return ResponseEntity.noContent().build();
    }
}
