package com.sonograma.service;

import com.sonograma.dto.PreVentaRequestDTO;
import com.sonograma.dto.PreVentaResponseDTO;
import com.sonograma.entity.Cliente;
import com.sonograma.entity.Disco;
import com.sonograma.entity.PreVenta;
import com.sonograma.exception.NegocioException;
import com.sonograma.exception.RecursoNoEncontradoException;
import com.sonograma.repository.ClienteRepository;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.PreVentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PreVentaService {

    private final PreVentaRepository repository;
    private final ClienteRepository clienteRepository;
    private final DiscoRepository discoRepository;

    @Transactional(readOnly = true)
    public List<PreVentaResponseDTO> listar() {
        return repository.findAllByOrderByFechaDescIdPreVentaDesc().stream().map(this::toDto).toList();
    }

    public PreVentaResponseDTO crear(PreVentaRequestDTO request) {
        Cliente cliente = clienteRepository.findById(request.getIdCliente())
            .filter(Cliente::getActivo)
            .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", request.getIdCliente()));
        Disco disco = request.getIdDisco() != null
            ? discoRepository.findById(request.getIdDisco()).orElseThrow(() -> new RecursoNoEncontradoException("Disco", request.getIdDisco()))
            : null;

        String descripcion = disco != null
            ? null
            : (request.getDescripcion() != null ? request.getDescripcion().trim() : null);
        if (disco == null && (descripcion == null || descripcion.isBlank())) {
            throw new NegocioException("Seleccioná un disco o ingresá una descripción para la pre-venta");
        }

        PreVenta preVenta = PreVenta.builder()
            .cliente(cliente)
            .disco(disco)
            .fecha(request.getFecha() != null ? request.getFecha() : LocalDate.now())
            .cantidad(request.getCantidad())
            .precio(request.getPrecio())
            .estado(request.getEstado() != null && !request.getEstado().isBlank() ? request.getEstado().trim() : "PENDIENTE")
            .notas(request.getNotas())
            .artistaSnap(disco != null ? disco.getArtista() : null)
            .albumSnap(disco != null ? disco.getAlbum() : null)
            .descripcionSnap(descripcion)
            .build();
        return toDto(repository.save(preVenta));
    }

    private PreVentaResponseDTO toDto(PreVenta preVenta) {
        Cliente cliente = preVenta.getCliente();
        Disco disco = preVenta.getDisco();
        return PreVentaResponseDTO.builder()
            .idPreVenta(preVenta.getIdPreVenta())
            .idCliente(cliente.getIdCliente())
            .clienteNombre((cliente.getNombre() + " " + (cliente.getApellido() != null ? cliente.getApellido() : "")).trim())
            .idDisco(disco != null ? disco.getIdDisco() : null)
            .artista(disco != null ? disco.getArtista() : preVenta.getArtistaSnap())
            .album(disco != null ? disco.getAlbum() : preVenta.getAlbumSnap())
            .descripcion(preVenta.getDescripcionSnap())
            .cantidad(preVenta.getCantidad())
            .precio(preVenta.getPrecio())
            .fecha(preVenta.getFecha())
            .estado(preVenta.getEstado())
            .notas(preVenta.getNotas())
            .build();
    }
}
