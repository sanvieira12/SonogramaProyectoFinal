package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.TipoDisco;
import com.sonograma.mapper.DiscoMapper;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscogsImportService {

    private final DiscoRepository discoRepository;
    private final DiscogsLinkParser discogsLinkParser;
    private final DiscogsApiClient discogsApiClient;
    private final DiscogsCoverService coverService;

    public DiscoImportPreviewDTO fetchDesdeLink(String url) {
        Optional<DiscogsLinkParser.DiscogsLink> link = discogsLinkParser.parse(url);
        if (link.isEmpty()) {
            return errorPreview("No se pudo extraer un ID release/master de la URL: " + url);
        }
        log.info("URL Discogs detectada: {} -> {} id={}",
                url, link.get().type(), link.get().id());
        DiscogsApiClient.FetchResult result = discogsApiClient.fetch(
                discogsApiClient.newSession(),
                link.get().type(),
                link.get().id()
        );
        if (!result.success()) {
            return errorPreview(result.errorMessage());
        }
        return toPreview(result, link.get().normalizedUrl(), downloadCover(result));
    }

    @Transactional
    public DiscoResponseDTO guardar(DiscoImportPreviewDTO preview) {
        Disco disco = DiscoMapper.toEntity(toRequest(preview));
        disco.setEstado(EstadoDisco.DISPONIBLE);
        disco.setCodigoQr(UUID.randomUUID().toString());
        disco.setCantidadCopias(1);
        return DiscoMapper.toDTO(discoRepository.save(disco));
    }

    @Transactional
    public List<DiscoResponseDTO> guardarLote(List<DiscoImportPreviewDTO> previews) {
        List<DiscoResponseDTO> guardados = new ArrayList<>();
        for (DiscoImportPreviewDTO preview : previews) {
            if (preview.getErrores() != null && !preview.getErrores().isEmpty()) continue;
            try {
                guardados.add(guardar(preview));
            } catch (Exception ex) {
                log.warn("Error guardando disco '{}': {}", preview.getAlbum(), ex.getMessage());
            }
        }
        return guardados;
    }

    private DiscoImportPreviewDTO toPreview(
            DiscogsApiClient.FetchResult result,
            String normalizedUrl,
            DiscogsCoverService.CoverResult cover
    ) {
        return DiscoImportPreviewDTO.builder()
                .artista(result.artist())
                .album(result.title())
                .sello(result.label())
                .anio(result.year())
                .pais(result.country())
                .genero(result.genre())
                .estilo(result.style())
                .formato(result.format())
                .imagenUrl(cover.publicUrl())
                .previewUrl(null)
                .tracklist(result.tracklist())
                .codigoInterno(generateCode(
                        result.artist(),
                        result.year(),
                        String.valueOf(result.resolvedReleaseId())
                ))
                .discogsUrl(normalizedUrl)
                .estado(EstadoDisco.DISPONIBLE.name())
                .condicion(CondicionDisco.USADO.name())
                .notas(cover.warning() == null ? null : "Portada: " + cover.warning())
                .errores(new ArrayList<>())
                .build();
    }

    private DiscogsCoverService.CoverResult downloadCover(DiscogsApiClient.FetchResult result) {
        if (result.resolvedReleaseId() == null) {
            return DiscogsCoverService.CoverResult.missing("No se pudo resolver el release");
        }
        return coverService.download(result.imageUrl(), result.resolvedReleaseId());
    }

    private DiscoRequestDTO toRequest(DiscoImportPreviewDTO preview) {
        DiscoRequestDTO request = new DiscoRequestDTO();
        request.setArtista(preview.getArtista());
        request.setAlbum(preview.getAlbum());
        request.setSelloDiscografico(preview.getSello());
        request.setGenero(preview.getGenero());
        request.setPais(preview.getPais());
        request.setEstilo(preview.getEstilo());
        request.setAnio(preview.getAnio());
        request.setPrecioVenta(preview.getPrecioVenta());
        request.setCosto(preview.getCosto());
        request.setTracklist(preview.getTracklist());
        request.setImagenUrl(preview.getImagenUrl());
        request.setPreviewUrl(preview.getPreviewUrl());
        request.setDiscogsUrl(preview.getDiscogsUrl());
        request.setCodigoInterno(preview.getCodigoInterno());
        request.setNotas(preview.getNotas());
        request.setProcedencia(preview.getProcedencia());
        request.setCondicion(parseCondition(preview.getCondicion()));
        request.setTipoDisco(parseFormat(preview.getFormato()));
        return request;
    }

    private CondicionDisco parseCondition(String condition) {
        try {
            return CondicionDisco.valueOf(Optional.ofNullable(condition).orElse("USADO"));
        } catch (IllegalArgumentException ex) {
            return CondicionDisco.USADO;
        }
    }

    private TipoDisco parseFormat(String format) {
        try {
            return TipoDisco.valueOf(Optional.ofNullable(format).orElse("VINILO").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TipoDisco.VINILO;
        }
    }

    private String generateCode(String artist, Integer year, String releaseId) {
        String initials = artist == null ? "XX" : Arrays.stream(artist.split("\\s+"))
                .filter(value -> !value.isBlank())
                .map(value -> value.substring(0, 1).toUpperCase(Locale.ROOT))
                .reduce("", String::concat);
        return (initials.isBlank() ? "XX" : initials)
                + "-" + Optional.ofNullable(year).orElse(0)
                + "-" + releaseId;
    }

    private DiscoImportPreviewDTO errorPreview(String message) {
        return DiscoImportPreviewDTO.builder().errores(List.of(message)).build();
    }
}
