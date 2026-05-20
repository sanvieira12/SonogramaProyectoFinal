package com.sonograma.service;

import com.sonograma.dto.DiscoDTO;
import com.sonograma.entity.Disco;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscoService {

    private final DiscoRepository discoRepository;

    public DiscoDTO crearDisco(Disco disco) {
        String codigoQr = "QR-" + System.currentTimeMillis();
        disco.setCodigoQr(codigoQr);
        disco.setEstado("DISPONIBLE");
        disco.setFechaIngreso(LocalDateTime.now());
        return mapearADTO(discoRepository.save(disco));
    }

    @Transactional(readOnly = true)
    public DiscoDTO obtenerPorId(Long id) {
        return discoRepository.findById(id)
                .map(this::mapearADTO)
                .orElseThrow(() -> new IllegalArgumentException("Disco no encontrado: " + id));
    }

    @Transactional(readOnly = true)
    public DiscoDTO obtenerPorQR(String codigoQr) {
        return discoRepository.findByCodigoQr(codigoQr)
                .map(this::mapearADTO)
                .orElseThrow(() -> new IllegalArgumentException("QR no encontrado: " + codigoQr));
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> obtenerTodos() {
        return discoRepository.findAll().stream()
                .map(this::mapearADTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> obtenerDisponibles() {
        return discoRepository.findDisponibles().stream()
                .map(this::mapearADTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> buscarPorArtista(String artista) {
        return discoRepository.findByArtistaContainingIgnoreCase(artista).stream()
                .map(this::mapearADTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DiscoDTO> buscarPorAlbum(String album) {
        return discoRepository.findByAlbumContainingIgnoreCase(album).stream()
                .map(this::mapearADTO)
                .collect(Collectors.toList());
    }

    public DiscoDTO actualizarDisco(Long id, Disco datosActualizados) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Disco no encontrado: " + id));

        if (datosActualizados.getArtista() != null) disco.setArtista(datosActualizados.getArtista());
        if (datosActualizados.getAlbum() != null) disco.setAlbum(datosActualizados.getAlbum());
        if (datosActualizados.getGenero() != null) disco.setGenero(datosActualizados.getGenero());
        if (datosActualizados.getAnio() != null) disco.setAnio(datosActualizados.getAnio());
        if (datosActualizados.getCondicion() != null) disco.setCondicion(datosActualizados.getCondicion());
        if (datosActualizados.getTipoDisco() != null) disco.setTipoDisco(datosActualizados.getTipoDisco());
        if (datosActualizados.getCosto() != null) disco.setCosto(datosActualizados.getCosto());
        if (datosActualizados.getPrecioVenta() != null) disco.setPrecioVenta(datosActualizados.getPrecioVenta());

        disco.setFechaActualizacion(LocalDateTime.now());
        return mapearADTO(discoRepository.save(disco));
    }

    public DiscoDTO cambiarEstado(Long id, String nuevoEstado) {
        Disco disco = discoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Disco no encontrado: " + id));
        disco.setEstado(nuevoEstado);
        disco.setFechaActualizacion(LocalDateTime.now());
        return mapearADTO(discoRepository.save(disco));
    }

    public void eliminarDisco(Long id) {
        if (!discoRepository.existsById(id)) {
            throw new IllegalArgumentException("Disco no encontrado: " + id);
        }
        discoRepository.deleteById(id);
    }

    public DiscoDTO mapearADTO(Disco disco) {
        return DiscoDTO.builder()
                .idDisco(disco.getIdDisco())
                .codigoInterno(disco.getCodigoInterno())
                .codigoQr(disco.getCodigoQr())
                .artista(disco.getArtista())
                .album(disco.getAlbum())
                .genero(disco.getGenero())
                .anio(disco.getAnio())
                .condicion(disco.getCondicion())
                .tipoDisco(disco.getTipoDisco())
                .costo(disco.getCosto())
                .precioVenta(disco.getPrecioVenta())
                .estado(disco.getEstado())
                .fechaIngreso(disco.getFechaIngreso())
                .fechaActualizacion(disco.getFechaActualizacion())
                .build();
    }
}
