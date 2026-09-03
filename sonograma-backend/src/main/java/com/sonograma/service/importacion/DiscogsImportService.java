package com.sonograma.service.importacion;

import com.sonograma.dto.DiscoImportPreviewDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.DiscogsCoverDownloadDTO;
import com.sonograma.dto.ManualDiscogsImportResultDTO;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.EstadoDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscogsCatalogStockService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.ImportMetadataNormalizer;
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
import java.util.LinkedHashMap;
import java.util.UUID;
import java.io.OutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscogsImportService {

    private final DiscogsApiClient discogsApiClient;
    private final DiscogsEnrichmentService enrichmentService;
    private final AudioPreviewService audioPreviewService;
    private final DiscogsCatalogStockService catalogStockService;
    private final DiscoQrCopyService qrCopyService;
    private final DiscoRepository discoRepository;
    private final DiscogsCoverService coverService;
    private final ManualDiscogsReceiptOperationService receiptOperationService;

    public DiscoImportPreviewDTO fetchDesdeLink(String url) {
        DiscogsEnrichmentService.EnrichmentResult enriched = enrichmentService.enrich(
                url, discogsApiClient.newSession());
        if (!enriched.success()) {
            return errorPreview(enriched);
        }
        DiscoImportPreviewDTO preview = toPreview(enriched.metadata(), enriched.cover());
        applyExistingProductState(preview);
        UUID operationId = receiptOperationService.createPending(preview.getDiscogsReleaseId(), preview.getCantidadCopias());
        preview.setOperationId(operationId.toString());
        return preview;
    }

    @Transactional
    public ManualDiscogsImportResultDTO guardar(DiscoImportPreviewDTO preview) {
        if (preview == null || preview.getDiscogsReleaseId() == null) {
            throw new com.sonograma.exception.NegocioException("No se pudo identificar el release concreto de Discogs. Volvé a consultarlo.");
        }
        return receiptOperationService.confirm(preview, () -> {
            DiscogsCatalogStockService.ReceiptResult receipt = catalogStockService.receive(
                    new DiscogsCatalogStockService.ReceiptCommand(
                            preview.getDiscogsReleaseId(), preview.getCantidadCopias(), toMetadata(preview)
                    ));
            audioPreviewService.guardarDesdeTracks(receipt.disco().getIdDisco(), preview.getTracks());
            return receipt;
        });
    }

    @Transactional
    public List<DiscoResponseDTO> guardarLote(List<DiscoImportPreviewDTO> previews) {
        List<DiscoResponseDTO> guardados = new ArrayList<>();
        for (DiscoImportPreviewDTO preview : previews) {
            if (preview.getErrores() != null && !preview.getErrores().isEmpty()) continue;
            try {
                ManualDiscogsImportResultDTO result = guardar(preview);
                if (result.getProductId() != null) {
                    discoRepository.findById(result.getProductId()).ifPresent(disco -> {
                        DiscoResponseDTO dto = com.sonograma.mapper.DiscoMapper.toDTO(disco);
                        dto.setAudioPreviews(audioPreviewService.listarPorDisco(disco.getIdDisco()));
                        dto.setQrCopies(qrCopyService.listDtos(disco));
                        guardados.add(dto);
                    });
                }
            } catch (Exception ex) {
                log.warn("Error guardando disco '{}': {}", preview.getAlbum(), ex.getMessage());
            }
        }
        return guardados;
    }

    /** Media action only: it validates the preview operation and never receives stock. */
    public DiscogsCoverDownloadDTO descargarPortada(DiscoImportPreviewDTO preview) {
        receiptOperationService.validateOperation(preview);
        DiscogsCoverService.CoverResult existing = coverService.existing(preview.getImagenUrl());
        DiscogsCoverService.CoverResult result = existing.available()
                ? existing
                : coverService.download(preview.getImagenUrl(), preview.getDiscogsReleaseId());
        return DiscogsCoverDownloadDTO.builder()
                .imagenUrl(result.publicUrl())
                .warning(result.warning())
                .build();
    }

    /** Writes an individual media ZIP; it has no catalogue/stock dependency. */
    public void escribirZip(DiscoImportPreviewDTO preview, OutputStream output) throws java.io.IOException {
        receiptOperationService.validateOperation(preview);
        coverService.writeManualReleaseZip(output, preview);
    }

    private DiscoImportPreviewDTO toPreview(DiscogsApiClient.FetchResult result,
                                            DiscogsCoverService.CoverResult cover) {
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
                .tracks(result.tracks())
                .codigoInterno(generateCode(
                        result.artist(),
                        result.year(),
                        String.valueOf(result.resolvedReleaseId())
                ))
                .discogsUrl(canonicalReleaseUrl(result.resolvedReleaseId()))
                .discogsReleaseId(result.resolvedReleaseId())
                .estado(EstadoDisco.DISPONIBLE.name())
                .condicion(CondicionDisco.USADO.name())
                .cantidadCopias(1)
                .procedencia(ImportMetadataNormalizer.SOURCE_DISCOGS)
                .notas(cover.warning() == null ? null : "Portada: " + cover.warning())
                .errores(new ArrayList<>())
                .build();
    }

    private DiscogsCatalogStockService.DiscogsMetadata toMetadata(DiscoImportPreviewDTO preview) {
        return new DiscogsCatalogStockService.DiscogsMetadata(
                preview.getArtista(), preview.getAlbum(), preview.getGenero(), preview.getSello(), preview.getAnio(),
                parseCondition(preview.getCondicion()), null, parseFormat(preview.getFormato()), preview.getFormato(),
                preview.getCosto(), preview.getPrecioVenta(),
                preview.getPrecioVenta() != null ? PricingMode.MANUAL : PricingMode.AUTO,
                preview.getPais(), preview.getEstilo(), preview.getTracklist(), preview.getImagenUrl(), preview.getPreviewUrl(),
                preview.getCodigoInterno(), preview.getProcedencia(), preview.getNotas());
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

    private String canonicalReleaseUrl(Long releaseId) {
        return "https://www.discogs.com/release/" + releaseId;
    }

    private void applyExistingProductState(DiscoImportPreviewDTO preview) {
        Long releaseId = preview.getDiscogsReleaseId();
        LinkedHashMap<Long, Disco> matches = new LinkedHashMap<>();
        discoRepository.findAllByDiscogsReleaseId(releaseId).forEach(disco -> matches.put(disco.getIdDisco(), disco));
        discoRepository.findByDiscogsUrl(canonicalReleaseUrl(releaseId))
                .ifPresent(disco -> matches.put(disco.getIdDisco(), disco));
        preview.setProductoExistente(!matches.isEmpty());
        if (matches.size() == 1) {
            Disco disco = matches.values().iterator().next();
            int available = disco.getIdDisco() != null && qrCopyService.hasCopyInventory(disco.getIdDisco())
                    ? Math.toIntExact(qrCopyService.countAvailableCopies(disco.getIdDisco()))
                    : Math.max(0, Optional.ofNullable(disco.getCantidadCopias()).orElse(0));
            preview.setCopiasDisponibles(available);
        } else if (matches.size() > 1) {
            preview.setErrores(List.of("Se encontró un conflicto con un producto existente. La importación necesita revisión."));
        }
    }

    private DiscoImportPreviewDTO errorPreview(DiscogsEnrichmentService.EnrichmentResult enriched) {
        DiscogsApiClient.FetchResult metadata = enriched.metadata();
        String message;
        if (metadata != null && metadata.rateLimited()) {
            message = "Discogs está limitando temporalmente las consultas. Intentá nuevamente en unos minutos.";
        } else if (enriched.errorMessage() != null && enriched.errorMessage().toLowerCase(Locale.ROOT).contains("master")) {
            message = "No se pudo determinar una edición concreta para este master de Discogs.";
        } else if (enriched.errorMessage() != null && enriched.errorMessage().toLowerCase(Locale.ROOT).contains("extraer un id")) {
            message = "El enlace de Discogs no es válido.";
        } else {
            message = "No se pudo obtener la información de Discogs. Podés volver a intentarlo.";
        }
        return DiscoImportPreviewDTO.builder().errores(List.of(message)).build();
    }
}
