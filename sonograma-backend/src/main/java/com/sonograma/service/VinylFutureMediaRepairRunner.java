package com.sonograma.service;

import com.sonograma.dto.InvoiceItem;
import com.sonograma.dto.TrackInfo;
import com.sonograma.dto.VinylPageData;
import com.sonograma.entity.Disco;
import com.sonograma.repository.DiscoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sonograma.vinylfuture.repair", name = "enabled", havingValue = "true")
public class VinylFutureMediaRepairRunner implements ApplicationRunner {

    private final DiscoRepository discoRepository;
    private final VinylFutureSearchService searchService;
    private final VinylFutureScraperService scraperService;
    private final VinylFutureAssetService assetService;
    private final AudioPreviewService audioPreviewService;
    private final ApplicationContext applicationContext;

    @Value("${sonograma.vinylfuture.repair.date}")
    private LocalDate repairDate;

    @Override
    public void run(ApplicationArguments args) {
        List<Disco> discos = discoRepository.findAll().stream()
            .filter(disco -> "VINYL_FUTURE".equalsIgnoreCase(disco.getProcedencia()))
            .filter(disco -> disco.getFechaIngreso() != null)
            .filter(disco -> repairDate.equals(disco.getFechaIngreso().toLocalDate()))
            .sorted(Comparator.comparing(Disco::getIdDisco))
            .toList();

        log.info("VinylFuture media repair started for date={} discos={}", repairDate, discos.size());

        int repaired = 0;
        int failed = 0;
        for (Disco disco : discos) {
            try {
                repairDisco(disco);
                repaired++;
            } catch (Exception ex) {
                failed++;
                log.error("VinylFuture media repair failed for discoId={} code='{}': {}",
                    disco.getIdDisco(), disco.getCodigoInterno(), ex.getMessage(), ex);
            }
        }

        log.info("VinylFuture media repair finished for date={} repaired={} failed={}",
            repairDate, repaired, failed);
        final int exitCode = failed == 0 ? 0 : 1;
        System.exit(org.springframework.boot.SpringApplication.exit(applicationContext, () -> exitCode));
    }

    @Transactional
    protected void repairDisco(Disco disco) {
        InvoiceItem item = new InvoiceItem(
            disco.getCodigoInterno(),
            disco.getArtista(),
            disco.getAlbum(),
            disco.getFormato(),
            disco.getCosto(),
            disco.getCantidadCopias(),
            null
        );

        String productUrl = searchService.buscar(item)
            .orElseThrow(() -> new IllegalStateException("No se encontro pagina VinylFuture"));

        VinylPageData page = scraperService.scrape(productUrl)
            .orElseThrow(() -> new IllegalStateException("No se pudo scrapear pagina VinylFuture"));

        VinylPageData storedPage = assetService.storeAssets(item, page);
        if (storedPage == null) {
            throw new IllegalStateException("No se pudieron almacenar assets");
        }

        disco.setImagenUrl(storedPage.frontImageUrl());
        disco.setPreviewUrl(firstPlayableUrl(storedPage.tracks()).orElse(null));
        discoRepository.save(disco);
        audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), storedPage.tracks());

        log.info("VinylFuture media repaired discoId={} code='{}' tracks={}",
            disco.getIdDisco(), disco.getCodigoInterno(), storedPage.tracks() == null ? 0 : storedPage.tracks().size());
    }

    private Optional<String> firstPlayableUrl(List<TrackInfo> tracks) {
        if (tracks == null) return Optional.empty();
        return tracks.stream()
            .map(track -> firstNonBlank(track.mp3Url(), track.youtubeUrl()))
            .filter(url -> url != null && !url.isBlank())
            .findFirst();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
