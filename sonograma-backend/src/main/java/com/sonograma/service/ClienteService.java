package com.sonograma.service;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.dto.ClienteRequest;
import com.sonograma.entity.Cliente;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.mapper.ClienteMapper;
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

    public ClienteDTO crearCliente(ClienteRequest request) {
        if (request.getCedula() != null && clienteRepository.existsByCedula(request.getCedula())) {
            throw new NegocioException("Ya existe un cliente con la cédula: " + request.getCedula());
        }
        Cliente cliente = ClienteMapper.toEntity(request);
        cliente.setActivo(true);
        return ClienteMapper.toDTO(clienteRepository.save(cliente));
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorId(Long id) {
        return clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .map(ClienteMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorCedula(String cedula) {
        return clienteRepository.findByCedulaAndActivoTrue(cedula)
                .map(ClienteMapper::toDTO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado con cédula: " + cedula));
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> buscar(String q) {
        return clienteRepository.buscarActivosPorNombreOApellido(q).stream()
                .map(ClienteMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> obtenerTodos() {
        return clienteRepository.findByActivoTrue().stream()
                .map(ClienteMapper::toDTO)
                .collect(Collectors.toList());
    }

    public ClienteDTO actualizarCliente(Long id, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));

        if (request.getCedula() != null
                && !request.getCedula().equals(cliente.getCedula())
                && clienteRepository.existsByCedulaAndIdClienteNot(request.getCedula(), id)) {
            throw new NegocioException("Ya existe un cliente con la cédula: " + request.getCedula());
        }

        ClienteMapper.updateFromRequest(cliente, request);
        return ClienteMapper.toDTO(clienteRepository.save(cliente));
    }

    public void eliminarCliente(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .filter(Cliente::getActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        cliente.setActivo(false);
        clienteRepository.save(cliente);
    }
}
