package com.sonograma.service;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;

    public ClienteDTO crearCliente(Cliente cliente) {
        return mapearADTO(clienteRepository.save(cliente));
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .map(this::mapearADTO)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> buscarPorNombre(String nombre) {
        return clienteRepository.findByNombreContainingIgnoreCase(nombre).stream()
                .map(this::mapearADTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorCedula(String cedula) {
        Cliente cliente = clienteRepository.findByCedula(cedula);
        if (cliente == null) {
            throw new IllegalArgumentException("Cliente no encontrado con cédula: " + cedula);
        }
        return mapearADTO(cliente);
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> obtenerTodos() {
        return clienteRepository.findAll().stream()
                .map(this::mapearADTO)
                .collect(Collectors.toList());
    }

    public ClienteDTO actualizarCliente(Long id, Cliente datosActualizados) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado: " + id));

        if (datosActualizados.getNombre() != null) cliente.setNombre(datosActualizados.getNombre());
        if (datosActualizados.getApellido() != null) cliente.setApellido(datosActualizados.getApellido());
        if (datosActualizados.getTelefono() != null) cliente.setTelefono(datosActualizados.getTelefono());
        if (datosActualizados.getEmail() != null) cliente.setEmail(datosActualizados.getEmail());
        if (datosActualizados.getDireccion() != null) cliente.setDireccion(datosActualizados.getDireccion());
        if (datosActualizados.getInstagramUsuario() != null) cliente.setInstagramUsuario(datosActualizados.getInstagramUsuario());
        if (datosActualizados.getObservaciones() != null) cliente.setObservaciones(datosActualizados.getObservaciones());

        return mapearADTO(clienteRepository.save(cliente));
    }

    public void eliminarCliente(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new IllegalArgumentException("Cliente no encontrado: " + id);
        }
        clienteRepository.deleteById(id);
    }

    private ClienteDTO mapearADTO(Cliente cliente) {
        return ClienteDTO.builder()
                .idCliente(cliente.getIdCliente())
                .nombre(cliente.getNombre())
                .apellido(cliente.getApellido())
                .telefono(cliente.getTelefono())
                .email(cliente.getEmail())
                .cedula(cliente.getCedula())
                .instagramUsuario(cliente.getInstagramUsuario())
                .direccion(cliente.getDireccion())
                .observaciones(cliente.getObservaciones())
                .fechaAlta(cliente.getFechaAlta())
                .build();
    }
}
