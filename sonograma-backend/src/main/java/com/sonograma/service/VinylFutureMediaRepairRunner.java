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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final CsvExportService csvExportService;
    private final ZipBundleService zipBundleService;
    private final ApplicationContext applicationContext;

    @Value("${sonograma.vinylfuture.repair.date:}")
    private String repairDateValue;

    @Value("${sonograma.vinylfuture.repair.invoice-number:}")
    private String invoiceNumber;

    @Value("${sonograma.vinylfuture.repair.export-path:}")
    private String exportPath;

    @Override
    public void run(ApplicationArguments args) {
        LocalDate repairDate = repairDateValue == null || repairDateValue.isBlank()
            ? null
            : LocalDate.parse(repairDateValue.strip());
        String targetInvoice = invoiceNumber == null ? "" : invoiceNumber.strip();
        if (targetInvoice.isBlank() && repairDate == null) {
            throw new IllegalArgumentException(
                "VinylFuture repair requires invoice-number or date to avoid a broad catalog mutation"
            );
        }
        List<Disco> discos = discoRepository.findAll().stream()
            .filter(disco -> targetInvoice.isBlank()
                ? ImportMetadataNormalizer.isFutureSource(disco.getProcedencia())
                    && disco.getFechaIngreso() != null
                    && repairDate.equals(disco.getFechaIngreso().toLocalDate())
                : targetInvoice.equalsIgnoreCase(firstNonBlank(disco.getNumeroFacturaCompra(), "")))
            .sorted(Comparator.comparing(Disco::getIdDisco))
            .toList();

        log.info("VinylFuture media repair started invoice={} date={} discos={}",
            targetInvoice, repairDate, discos.size());

        int repaired = 0;
        int failed = 0;
        Map<InvoiceItem, Optional<String>> searchResults = new LinkedHashMap<>();
        Map<InvoiceItem, Optional<VinylPageData>> pageData = new LinkedHashMap<>();
        for (Disco disco : discos) {
            InvoiceItem item = invoiceItem(disco);
            try {
                RepairResult result = repairDisco(disco, item);
                searchResults.put(item, Optional.of(result.productUrl()));
                pageData.put(item, Optional.of(result.page()));
                repaired++;
            } catch (Exception ex) {
                searchResults.put(item, Optional.empty());
                pageData.put(item, Optional.empty());
                failed++;
                log.error("VinylFuture media repair failed for discoId={} code='{}': {}",
                    disco.getIdDisco(), disco.getCodigoInterno(), ex.getMessage(), ex);
            }
        }

        if (exportPath != null && !exportPath.isBlank()) {
            exportZip(searchResults, pageData, targetInvoice, repairDate);
        }

        log.info("VinylFuture media repair finished invoice={} date={} repaired={} failed={}",
            targetInvoice, repairDate, repaired, failed);
        final int exitCode = failed == 0 ? 0 : 1;
        System.exit(org.springframework.boot.SpringApplication.exit(applicationContext, () -> exitCode));
    }

    @Transactional
    protected RepairResult repairDisco(Disco disco, InvoiceItem item) {
        String productUrl = searchService.buscar(item)
            .orElseThrow(() -> new IllegalStateException("No se encontro pagina VinylFuture"));

        VinylPageData page = scraperService.scrape(productUrl)
            .orElseThrow(() -> new IllegalStateException("No se pudo scrapear pagina VinylFuture"));

        VinylPageData storedPage = assetService.storeAssets(item, page);
        if (storedPage == null) {
            throw new IllegalStateException("No se pudieron almacenar assets");
        }
        if (storedPage.frontImageUrl() == null || storedPage.frontImageUrl().isBlank()) {
            throw new IllegalStateException("No se pudo descargar la portada");
        }
        if (firstPlayableUrl(storedPage.tracks()).isEmpty()) {
            throw new IllegalStateException("No se pudo descargar ningun preview de audio");
        }

        disco.setImagenUrl(storedPage.frontImageUrl());
        disco.setPreviewUrl(firstPlayableUrl(storedPage.tracks()).orElse(null));
        discoRepository.save(disco);
        audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), storedPage.tracks());

        log.info("VinylFuture media repaired discoId={} code='{}' tracks={}",
            disco.getIdDisco(), disco.getCodigoInterno(), storedPage.tracks() == null ? 0 : storedPage.tracks().size());
        return new RepairResult(productUrl, storedPage);
    }

    private InvoiceItem invoiceItem(Disco disco) {
        return new InvoiceItem(
            disco.getCodigoInterno(),
            disco.getArtista(),
            disco.getAlbum(),
            disco.getFormato(),
            disco.getCosto(),
            disco.getCantidadCopias(),
            null
        );
    }

    private void exportZip(
            Map<InvoiceItem, Optional<String>> searchResults,
            Map<InvoiceItem, Optional<VinylPageData>> pageData,
            String targetInvoice,
            LocalDate repairDate) {
        try {
            String suffix = targetInvoice.isBlank() ? repairDate.toString() : targetInvoice;
            Path generated = zipBundleService.buildZip(
                csvExportService.buildCsv(searchResults),
                pageData,
                "VinylFuture_Invoice_" + suffix
            );
            Path target = Path.of(exportPath).toAbsolutePath().normalize();
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.move(generated, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("VinylFuture repaired ZIP exported to '{}'", target);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo exportar el ZIP reparado", ex);
        }
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

    protected record RepairResult(String productUrl, VinylPageData page) {}
}
