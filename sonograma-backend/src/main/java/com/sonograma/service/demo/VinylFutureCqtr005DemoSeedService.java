package com.sonograma.service.demo;

import com.sonograma.dto.DiscoQrCopyDTO;
import com.sonograma.dto.DiscoRequestDTO;
import com.sonograma.dto.DiscoResponseDTO;
import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.enums.CondicionDisco;
import com.sonograma.enums.PricingMode;
import com.sonograma.enums.TipoDisco;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscoQrCopyService;
import com.sonograma.service.DiscoService;
import com.sonograma.service.ImportMetadataNormalizer;
import com.sonograma.service.QRService;
import com.sonograma.service.VinylFutureAssetService;
import com.sonograma.service.VinylFutureScraperService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fixed, one-time demo seed for VinylFuture product 1133955.
 *
 * <p>This deliberately is not a general VinylFuture URL importer. It exists only so the demo
 * record can be created through the same scraper, media storage, catalog, preview, stock-copy,
 * and QR services used by the application, without manufacturing an invoice or purchase data.</p>
 */
@Service
@RequiredArgsConstructor
public class VinylFutureCqtr005DemoSeedService {

    static final String PRODUCT_URL =
        "https://www.vinylfuture.com/Various_CQTR005_CQTR005_Vinyl__1133955";
    static final String PRODUCT_ID = "1133955";
    static final String CATALOG_CODE = "CQTR005";

    private final DiscoRepository discoRepository;
    private final VinylFutureScraperService scraperService;
    private final VinylFutureAssetService assetService;
    private final DiscoService discoService;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final QRService qrService;

    @Transactional
    public SeedResult seed() {
        Disco existing = discoRepository.findByDiscogsUrl(PRODUCT_URL).orElse(null);
        if (existing != null) {
            return resultFor(existing, false, 0);
        }

        discoRepository.findByCodigoInterno(CATALOG_CODE).ifPresent(collision -> {
            throw new IllegalStateException(
                "CQTR005 ya existe con otra URL (disco " + collision.getIdDisco() + ")"
            );
        });

        VinylPageData scraped = scraperService.scrape(PRODUCT_URL)
            .orElseThrow(() -> new IllegalStateException("No se pudo leer el producto VinylFuture " + PRODUCT_ID));
        validateProduct(scraped);

        InvoiceItem namingContext = new InvoiceItem(
            scraped.code(), scraped.artist(), scraped.title(), scraped.format(), null, 1, null
        );
        VinylFutureAssetService.AssetStoreResult assets = assetService.storeAssetsWithResult(namingContext, scraped);
        if (assets == null || assets.page() == null || blank(assets.page().frontImageUrl())) {
            throw new IllegalStateException("No se pudo almacenar la portada VinylFuture");
        }
        VinylPageData stored = assets.page();
        if (playableTracks(stored).isEmpty()) {
            throw new IllegalStateException("VinylFuture no devolvio previews reproducibles para el demo");
        }

        DiscoResponseDTO created = discoService.crearDisco(toRequest(stored));
        audioPreviewService.guardarDesdeTracks(created.getIdDisco(), stored.tracks());

        Disco saved = discoRepository.findById(created.getIdDisco())
            .orElseThrow(() -> new IllegalStateException("El disco demo no quedo guardado"));
        assertStockAndQr(saved);
        byte[] qrPng = qrService.descargarQR(saved.getIdDisco(), 1);
        if (qrPng == null || qrPng.length == 0) {
            throw new IllegalStateException("No se pudo generar el PNG del QR del demo");
        }

        return resultFor(saved, true, qrPng.length);
    }

    private DiscoRequestDTO toRequest(VinylPageData page) {
        DiscoRequestDTO request = new DiscoRequestDTO();
        request.setCodigoInterno(page.code());
        request.setArtista(page.artist());
        request.setAlbum(page.title());
        request.setGenero(page.genre());
        request.setSelloDiscografico(page.label());
        request.setDescripcion(page.description());
        request.setAnio(page.year());
        request.setCondicion(parseCondition(page.condition()));
        request.setTipoDisco(TipoDisco.VINILO);
        request.setFormato(page.format());
        request.setCosto(null);
        request.setCostoMoneda(null);
        request.setNumeroFacturaCompra(null);
        request.setFechaFacturaCompra(null);
        request.setPrecioVenta(null);
        request.setPricingMode(PricingMode.AUTO);
        request.setPais(page.country());
        request.setEstilo(null);
        request.setTracklist(tracklist(page.tracks()));
        request.setNotas("Demo manual VinylFuture ID " + PRODUCT_ID
            + "; sin factura, costo ni precio de venta.");
        request.setProcedencia(ImportMetadataNormalizer.SOURCE_FUTURE);
        request.setImagenUrl(page.frontImageUrl());
        request.setPreviewUrl(firstPlayableUrl(page.tracks()));
        request.setDiscogsUrl(PRODUCT_URL);
        request.setCantidadCopias(1);
        return request;
    }

    private void validateProduct(VinylPageData page) {
        if (!PRODUCT_URL.equals(page.sourceUrl())) {
            throw new IllegalStateException("La URL obtenida no corresponde al producto demo");
        }
        if (!CATALOG_CODE.equalsIgnoreCase(value(page.code()))) {
            throw new IllegalStateException("El producto VinylFuture no devolvio el catalogo CQTR005");
        }
        if (blank(page.artist()) || blank(page.title()) || blank(page.label()) || blank(page.format())) {
            throw new IllegalStateException("Faltan metadatos esenciales en la pagina VinylFuture");
        }
    }

    private void assertStockAndQr(Disco disco) {
        if (qrCopyService.totalCopies(disco.getIdDisco()) != 1
                || qrCopyService.countAvailableCopies(disco.getIdDisco()) != 1) {
            throw new IllegalStateException("El demo no quedo con exactamente una copia disponible");
        }
        List<DiscoQrCopyDTO> copies = qrCopyService.listDtos(disco);
        if (copies.size() != 1
                || !"DISPONIBLE".equals(copies.getFirst().estado())
                || blank(copies.getFirst().codigoQr())
                || blank(copies.getFirst().content())
                || blank(copies.getFirst().imageUrl())) {
            throw new IllegalStateException("La copia demo no quedo asociada al QR normal de catalogo");
        }
    }

    private SeedResult resultFor(Disco disco, boolean created, int qrPngBytes) {
        List<DiscoQrCopyDTO> copies = qrCopyService.listDtos(disco);
        DiscoQrCopyDTO copy = copies.size() == 1 ? copies.getFirst() : null;
        return new SeedResult(
            created,
            disco.getIdDisco(),
            disco.getCodigoInterno(),
            copy != null ? copy.id() : null,
            copy != null ? copy.copyNumber() : null,
            copy != null ? copy.codigoQr() : null,
            copy != null ? copy.content() : null,
            copy != null ? copy.imageUrl() : null,
            qrPngBytes
        );
    }

    private List<TrackInfo> playableTracks(VinylPageData page) {
        if (page.tracks() == null) return List.of();
        return page.tracks().stream()
            .filter(track -> !blank(track.mp3Url()) || !blank(track.youtubeUrl()))
            .toList();
    }

    private String tracklist(List<TrackInfo> tracks) {
        if (tracks == null) return null;
        return tracks.stream()
            .map(track -> (value(track.label()) + " " + value(track.name())).strip())
            .filter(line -> !line.isBlank())
            .reduce((left, right) -> left + "\n" + right)
            .orElse(null);
    }

    private String firstPlayableUrl(List<TrackInfo> tracks) {
        if (tracks == null) return null;
        return tracks.stream()
            .map(track -> !blank(track.mp3Url()) ? track.mp3Url() : track.youtubeUrl())
            .filter(url -> !blank(url))
            .findFirst()
            .orElse(null);
    }

    private CondicionDisco parseCondition(String condition) {
        if (blank(condition)) return CondicionDisco.NUEVO;
        String normalized = condition.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("USED") || normalized.contains("USADO")
            ? CondicionDisco.USADO
            : CondicionDisco.NUEVO;
    }

    private String value(String value) {
        return value == null ? "" : value.strip();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record SeedResult(
        boolean created,
        Long discoId,
        String catalogCode,
        Long copyId,
        Integer copyNumber,
        String qrCode,
        String qrContent,
        String qrDownloadUrl,
        int qrPngBytes
    ) {}
}
