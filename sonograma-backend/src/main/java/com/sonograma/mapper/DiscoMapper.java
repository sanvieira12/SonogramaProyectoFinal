package com.sonograma.mapper;

import com.sonograma.dto.DiscoDTO;
import com.sonograma.dto.DiscoRequest;
import com.sonograma.entity.Disco;

public class DiscoMapper {

    private DiscoMapper() {}

    public static DiscoDTO toDTO(Disco disco) {
        return DiscoDTO.builder()
                .idDisco(disco.getIdDisco())
                .codigoInterno(disco.getCodigoInterno())
                .codigoQr(disco.getCodigoQr())
                .artista(disco.getArtista())
                .album(disco.getAlbum())
                .genero(disco.getGenero())
                .anio(disco.getAnio())
                .condicion(disco.getCondicion() != null ? disco.getCondicion().name() : null)
                .tipoDisco(disco.getTipoDisco() != null ? disco.getTipoDisco().name() : null)
                .costo(disco.getCosto())
                .precioVenta(disco.getPrecioVenta())
                .estado(disco.getEstado() != null ? disco.getEstado().name() : null)
                .fechaIngreso(disco.getFechaIngreso())
                .fechaActualizacion(disco.getFechaActualizacion())
                .build();
    }

    public static Disco toEntity(DiscoRequest request) {
        return Disco.builder()
                .codigoInterno(request.getCodigoInterno())
                .artista(request.getArtista())
                .album(request.getAlbum())
                .genero(request.getGenero())
                .anio(request.getAnio())
                .condicion(request.getCondicion())
                .tipoDisco(request.getTipoDisco())
                .costo(request.getCosto())
                .precioVenta(request.getPrecioVenta())
                .build();
    }

    public static void updateFromRequest(Disco disco, DiscoRequest request) {
        if (request.getCodigoInterno() != null) disco.setCodigoInterno(request.getCodigoInterno());
        if (request.getArtista() != null) disco.setArtista(request.getArtista());
        if (request.getAlbum() != null) disco.setAlbum(request.getAlbum());
        if (request.getGenero() != null) disco.setGenero(request.getGenero());
        if (request.getAnio() != null) disco.setAnio(request.getAnio());
        if (request.getCondicion() != null) disco.setCondicion(request.getCondicion());
        if (request.getTipoDisco() != null) disco.setTipoDisco(request.getTipoDisco());
        if (request.getCosto() != null) disco.setCosto(request.getCosto());
        if (request.getPrecioVenta() != null) disco.setPrecioVenta(request.getPrecioVenta());
    }
}
