package com.sonograma.mapper;

import com.sonograma.dto.ClienteDTO;
import com.sonograma.dto.ClienteRequest;
import com.sonograma.entity.Cliente;

public class ClienteMapper {

    private ClienteMapper() {}

    public static ClienteDTO toDTO(Cliente cliente) {
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

    public static Cliente toEntity(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNombre(request.getNombre());
        cliente.setApellido(request.getApellido());
        cliente.setTelefono(request.getTelefono());
        cliente.setEmail(request.getEmail());
        cliente.setCedula(request.getCedula());
        cliente.setInstagramUsuario(request.getInstagramUsuario());
        cliente.setDireccion(request.getDireccion());
        cliente.setObservaciones(request.getObservaciones());
        return cliente;
    }

    public static void updateFromRequest(Cliente cliente, ClienteRequest request) {
        if (request.getNombre() != null) cliente.setNombre(request.getNombre());
        if (request.getApellido() != null) cliente.setApellido(request.getApellido());
        if (request.getTelefono() != null) cliente.setTelefono(request.getTelefono());
        if (request.getEmail() != null) cliente.setEmail(request.getEmail());
        if (request.getCedula() != null) cliente.setCedula(request.getCedula());
        if (request.getInstagramUsuario() != null) cliente.setInstagramUsuario(request.getInstagramUsuario());
        if (request.getDireccion() != null) cliente.setDireccion(request.getDireccion());
        if (request.getObservaciones() != null) cliente.setObservaciones(request.getObservaciones());
    }
}
