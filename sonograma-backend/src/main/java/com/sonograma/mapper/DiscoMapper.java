package com.sonograma.mapper;

import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.entity.Disco;

public class DiscoMapper {

    private DiscoMapper() {}

    public static DiscoResponseDTO toDTO(Disco disco) {
        return DiscoResponseDTO.builder()
                .idDisco(disco.getIdDisco())
                .codigoInterno(disco.getCodigoInterno())
                .codigoQr(disco.getCodigoQr())
                .artista(disco.getArtista())
                .album(disco.getAlbum())
                .genero(disco.getGenero())
                .selloDiscografico(disco.getSelloDiscografico())
                .descripcion(disco.getDescripcion())
                .anio(disco.getAnio())
                .condicion(disco.getCondicion() != null ? disco.getCondicion().name() : null)
                .tipoDisco(disco.getTipoDisco() != null ? disco.getTipoDisco().name() : null)
                .costo(disco.getCosto())
                .precioVenta(disco.getPrecioVenta())
                .estado(disco.getEstado() != null ? disco.getEstado().name() : null)
                .pais(disco.getPais())
                .estilo(disco.getEstilo())
                .tracklist(disco.getTracklist())
                .notas(disco.getNotas())
                .procedencia(disco.getProcedencia())
                .imagenUrl(disco.getImagenUrl())
                .previewUrl(disco.getPreviewUrl())
                .discogsUrl(disco.getDiscogsUrl())
                .cantidadCopias(disco.getCantidadCopias())
                .fechaIngreso(disco.getFechaIngreso())
                .fechaActualizacion(disco.getFechaActualizacion())
                .build();
    }

    public static Disco toEntity(DiscoRequestDTO request) {
        return Disco.builder()
                .codigoInterno(request.getCodigoInterno())
                .artista(request.getArtista())
                .album(request.getAlbum())
                .genero(request.getGenero())
                .selloDiscografico(request.getSelloDiscografico())
                .descripcion(request.getDescripcion())
                .anio(request.getAnio())
                .condicion(request.getCondicion())
                .tipoDisco(request.getTipoDisco())
                .costo(request.getCosto())
                .precioVenta(request.getPrecioVenta())
                .pais(request.getPais())
                .estilo(request.getEstilo())
                .tracklist(request.getTracklist())
                .notas(request.getNotas())
                .procedencia(request.getProcedencia())
                .imagenUrl(request.getImagenUrl())
                .previewUrl(request.getPreviewUrl())
                .discogsUrl(request.getDiscogsUrl())
                .cantidadCopias(request.getCantidadCopias() != null ? request.getCantidadCopias() : 1)
                .build();
    }

    public static void updateFromRequest(Disco disco, DiscoRequestDTO request) {
        if (request.getCodigoInterno() != null) disco.setCodigoInterno(request.getCodigoInterno());
        if (request.getArtista() != null) disco.setArtista(request.getArtista());
        if (request.getAlbum() != null) disco.setAlbum(request.getAlbum());
        if (request.getGenero() != null) disco.setGenero(request.getGenero());
        if (request.getSelloDiscografico() != null) disco.setSelloDiscografico(request.getSelloDiscografico());
        if (request.getDescripcion() != null) disco.setDescripcion(request.getDescripcion());
        if (request.getAnio() != null) disco.setAnio(request.getAnio());
        if (request.getCondicion() != null) disco.setCondicion(request.getCondicion());
        if (request.getTipoDisco() != null) disco.setTipoDisco(request.getTipoDisco());
        if (request.getCosto() != null) disco.setCosto(request.getCosto());
        if (request.getPrecioVenta() != null) disco.setPrecioVenta(request.getPrecioVenta());
        if (request.getPais() != null) disco.setPais(request.getPais());
        if (request.getEstilo() != null) disco.setEstilo(request.getEstilo());
        if (request.getTracklist() != null) disco.setTracklist(request.getTracklist());
        if (request.getNotas() != null) disco.setNotas(request.getNotas());
        if (request.getProcedencia() != null) disco.setProcedencia(request.getProcedencia());
        if (request.getImagenUrl() != null) disco.setImagenUrl(request.getImagenUrl());
        if (request.getPreviewUrl() != null) disco.setPreviewUrl(request.getPreviewUrl());
        if (request.getDiscogsUrl() != null) disco.setDiscogsUrl(request.getDiscogsUrl());
        if (request.getCantidadCopias() != null) disco.setCantidadCopias(request.getCantidadCopias());
    }
}
