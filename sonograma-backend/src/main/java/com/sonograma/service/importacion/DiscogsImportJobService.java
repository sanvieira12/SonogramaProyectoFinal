package com.sonograma.service.importacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.dto.DiscogsImportRowDTO;
import com.sonograma.dto.TrackInfo;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.enums.*;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsImportJobRepository;
import com.sonograma.repository.DiscogsImportRowRepository;
import com.sonograma.service.AudioPreviewService;
import com.sonograma.service.DiscoQrCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscogsImportJobService {

    private static final String RATE_LIMIT_WARNING =
            "Discogs limitó temporalmente esta solicitud. Ítem importado parcialmente; metadata pendiente.";

    private final DiscogsExcelParser excelParser;
    private final DiscogsApiClient apiClient;
    private final DiscogsImportJobRepository jobRepository;
    private final DiscogsImportRowRepository rowRepository;
    private final DiscoRepository discoRepository;
    private final DiscogsCoverService coverService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final AudioPreviewService audioPreviewService;
    private final DiscoQrCopyService qrCopyService;
    private final ExecutorService jobExecutor = Executors.newFixedThreadPool(2);

    public DiscogsImportJobDTO createJob(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new NegocioException("El archivo Excel está vacío");
        }

        DiscogsExcelParser.ParsedSheet parsed;
        try {
            parsed = excelParser.parse(file);
        } catch (IOException ex) {
            throw new NegocioException("No se pudo leer el Excel: " + ex.getMessage());
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long jobId = tx.execute(status -> {
            DiscogsImportJob job = DiscogsImportJob.builder()
                    .nombreArchivo(Optional.ofNullable(file.getOriginalFilename()).orElse("discogs.xlsx"))
                    .nombreHoja(parsed.sheetName())
                    .status(DiscogsImportJobStatus.PENDING)
                    .build();
            for (DiscogsExcelParser.ParsedRow source : parsed.rows()) {
                DiscogsImportRow row = DiscogsImportRow.builder()
                        .job(job)
                        .sourceExcelRowNumber(source.sourceExcelRowNumber())
                        .visibleCellValue(source.visibleCellValue())
                        .hyperlinkUrl(source.hyperlinkUrl())
                        .normalizedDiscogsUrl(source.normalizedDiscogsUrl())
                        .urlSource(source.urlSource())
                        .discogsType(source.discogsType())
                        .discogsId(source.discogsId())
                        .artist(source.artist())
                        .title(source.title())
                        .status(source.status())
                        .errorMessage(source.errorMessage())
                        .build();
                job.getRows().add(row);
            }
            return jobRepository.save(job).getIdDiscogsImportJob();
        });

        if (jobId == null) {
            throw new NegocioException("No se pudo crear el trabajo de importación");
        }
        jobExecutor.submit(() -> processJob(jobId));
        return getJob(jobId);
    }

    public DiscogsImportJobDTO getJob(Long jobId) {
        DiscogsImportJob job = jobRepository.findDetailedByIdDiscogsImportJob(jobId)
                .orElseThrow(() -> new NegocioException("Importación Discogs no encontrada: " + jobId));
        return toDto(job);
    }

    public DiscogsImportJobDTO retryRow(Long jobId, Long rowId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId)
                    .orElseThrow(() -> new NegocioException("Fila Discogs no encontrada: " + rowId));
            if (!row.getJob().getIdDiscogsImportJob().equals(jobId)) {
                throw new NegocioException("La fila no pertenece a la importación indicada");
            }
            if (row.getDiscogsId() == null || row.getDiscogsType() == null) {
                throw new NegocioException("La fila no contiene un link Discogs válido");
            }
            row.setStatus(DiscogsImportRowStatus.PARSED);
            row.setErrorMessage(null);
            rowRepository.save(row);
            DiscogsImportJob job = row.getJob();
            job.setStatus(DiscogsImportJobStatus.PROCESSING);
            job.setErrorMessage(null);
            jobRepository.save(job);
        });
        jobExecutor.submit(() -> processRowAndFinalize(jobId, rowId));
        return getJob(jobId);
    }

    public DiscogsImportJobDTO importParsedRows(Long jobId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            List<DiscogsImportRow> rows = rowRepository
                    .findByJobIdDiscogsImportJobAndStatusInOrderBySourceExcelRowNumber(
                            jobId,
                            List.of(DiscogsImportRowStatus.PARSED, DiscogsImportRowStatus.RATE_LIMITED)
                    );
            for (DiscogsImportRow row : rows) {
                if (row.getImportedCatalogProduct() != null) {
                    updateDisco(row.getImportedCatalogProduct(), row);
                    Disco disco = discoRepository.save(row.getImportedCatalogProduct());
                    qrCopyService.synchronize(disco);
                    audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), parseTracks(row.getTracksJson()));
                    row.setStatus(DiscogsImportRowStatus.IMPORTED);
                    rowRepository.save(row);
                    continue;
                }
                boolean partial = row.getStatus() == DiscogsImportRowStatus.RATE_LIMITED;
                Disco disco = discoRepository.findByDiscogsUrl(row.getNormalizedDiscogsUrl())
                        .orElseGet(() -> discoRepository.save(toDisco(row)));
                updateDisco(disco, row);
                discoRepository.save(disco);
                qrCopyService.synchronize(disco);
                audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), parseTracks(row.getTracksJson()));
                row.setImportedCatalogProduct(disco);
                row.setStatus(DiscogsImportRowStatus.IMPORTED);
                if (!partial) row.setErrorMessage(null);
                rowRepository.save(row);
            }
        });
        return getJob(jobId);
    }

    public Path buildCoversZip(Long jobId) throws IOException {
        if (!jobRepository.existsById(jobId)) {
            throw new NegocioException("Importación Discogs no encontrada: " + jobId);
        }
        List<DiscogsImportRow> rows = rowRepository
                .findByJobIdDiscogsImportJobOrderBySourceExcelRowNumber(jobId);
        return coverService.buildZip(rows);
    }

    private void processJob(Long jobId) {
        try {
            updateJobStatus(jobId, DiscogsImportJobStatus.PROCESSING, null);
            List<Long> rowIds = new TransactionTemplate(transactionManager).execute(status ->
                    rowRepository.findByJobIdDiscogsImportJobAndStatusInOrderBySourceExcelRowNumber(
                                    jobId,
                                    List.of(DiscogsImportRowStatus.PARSED)
                            ).stream()
                            .map(DiscogsImportRow::getIdDiscogsImportRow)
                            .toList()
            );
            if (rowIds != null) {
                DiscogsApiClient.ImportSession session = apiClient.newSession();
                for (Long rowId : rowIds) {
                    processRow(rowId, session);
                }
            }
            finalizeJob(jobId);
        } catch (Exception ex) {
            log.error("Falló importación Discogs {}: {}", jobId, ex.getMessage(), ex);
            updateJobStatus(jobId, DiscogsImportJobStatus.FAILED, ex.getMessage());
        }
    }

    private void processRowAndFinalize(Long jobId, Long rowId) {
        processRow(rowId, apiClient.newSession());
        finalizeJob(jobId);
    }

    private void processRow(Long rowId, DiscogsApiClient.ImportSession session) {
        DiscogsImportRow source = updateRowStatus(rowId, DiscogsImportRowStatus.FETCHING_DISCOGS, null);
        log.info("Consultando metadata Discogs {} id={} fila={}",
                source.getDiscogsType(), source.getDiscogsId(), source.getSourceExcelRowNumber());
        DiscogsApiClient.FetchResult result =
                apiClient.fetch(session, source.getDiscogsType(), source.getDiscogsId());
        if (result.success()) {
            saveFetchResult(rowId, result);
            return;
        }
        if (result.rateLimited()) {
            incrementRetry(rowId, result.errorMessage());
            updateRowStatus(rowId, DiscogsImportRowStatus.RATE_LIMITED, RATE_LIMIT_WARNING);
            log.warn("Importación Discogs parcial fila={} id={}: {}",
                    source.getSourceExcelRowNumber(), source.getDiscogsId(), RATE_LIMIT_WARNING);
            return;
        }
        updateRowStatus(rowId, DiscogsImportRowStatus.FAILED, result.errorMessage());
        log.warn("Falló metadata Discogs fila={} id={}: {}",
                source.getSourceExcelRowNumber(), source.getDiscogsId(), result.errorMessage());
    }

    private DiscogsImportRow updateRowStatus(Long rowId, DiscogsImportRowStatus status, String message) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(transactionStatus -> {
            DiscogsImportRow row = rowRepository.findById(rowId)
                    .orElseThrow(() -> new NegocioException("Fila Discogs no encontrada: " + rowId));
            row.setStatus(status);
            row.setErrorMessage(message);
            return rowRepository.save(row);
        });
    }

    private int incrementRetry(Long rowId, String message) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Integer count = tx.execute(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setRetryCount(row.getRetryCount() + 1);
            row.setErrorMessage(message);
            return rowRepository.save(row).getRetryCount();
        });
        return count == null ? 1 : count;
    }

    private void saveFetchResult(Long rowId, DiscogsApiClient.FetchResult result) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            DiscogsImportRow row = rowRepository.findById(rowId).orElseThrow();
            row.setMasterId(result.masterId());
            row.setResolvedReleaseId(result.resolvedReleaseId());
            row.setArtist(firstNonBlank(result.artist(), row.getArtist()));
            row.setTitle(firstNonBlank(result.title(), row.getTitle()));
            row.setYear(result.year());
            row.setGenre(result.genre());
            row.setLabel(result.label());
            row.setCatalogNumber(result.catalogNumber());
            row.setCountry(result.country());
            row.setStyle(result.style());
            row.setFormat(result.format());
            if (result.imageUrl() != null && result.resolvedReleaseId() != null) {
                DiscogsCoverService.CoverResult cover =
                        coverService.download(result.imageUrl(), result.resolvedReleaseId());
                row.setImageUrl(cover.publicUrl());
                if (cover.available()) {
                    log.info("Portada Discogs disponible fila={} release={}",
                            row.getSourceExcelRowNumber(), result.resolvedReleaseId());
                } else {
                    log.warn("Portada Discogs no descargada fila={} release={}: {}",
                            row.getSourceExcelRowNumber(), result.resolvedReleaseId(), cover.warning());
                }
            }
            row.setPreviewUrl(null);
            row.setTracklist(result.tracklist());
            try {
                row.setTracksJson(objectMapper.writeValueAsString(result.tracks()));
            } catch (Exception ex) {
                log.warn("No se pudieron serializar links de audio Discogs para fila {}",
                        row.getSourceExcelRowNumber());
            }
            if (row.getImportedCatalogProduct() != null) {
                updateDisco(row.getImportedCatalogProduct(), row);
                Disco disco = discoRepository.save(row.getImportedCatalogProduct());
                qrCopyService.synchronize(disco);
                discoRepository.save(disco);
                audioPreviewService.guardarDesdeTracks(disco.getIdDisco(), result.tracks());
                row.setStatus(DiscogsImportRowStatus.IMPORTED);
            } else {
                row.setStatus(DiscogsImportRowStatus.PARSED);
            }
            row.setErrorMessage(row.getImageUrl() == null ? "Discogs no informó una portada" : null);
            rowRepository.save(row);
            log.info("Metadata Discogs obtenida fila={} release={} cache={}",
                    row.getSourceExcelRowNumber(), result.resolvedReleaseId(), result.cacheHit());
        });
    }

    private void finalizeJob(Long jobId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            DiscogsImportJob job = jobRepository.findDetailedByIdDiscogsImportJob(jobId).orElseThrow();
            boolean errors = job.getRows().stream().anyMatch(row ->
                    row.getStatus() == DiscogsImportRowStatus.FAILED
                            || row.getStatus() == DiscogsImportRowStatus.RATE_LIMITED
            );
            job.setStatus(errors
                    ? DiscogsImportJobStatus.COMPLETED_WITH_ERRORS
                    : DiscogsImportJobStatus.COMPLETED);
            jobRepository.save(job);
        });
    }

    private void updateJobStatus(Long jobId, DiscogsImportJobStatus status, String message) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(transactionStatus -> {
            DiscogsImportJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(status);
            job.setErrorMessage(message);
            jobRepository.save(job);
        });
    }

    private Disco toDisco(DiscogsImportRow row) {
        Disco disco = Disco.builder()
                .codigoInterno(generateCode(row))
                .codigoQr(UUID.randomUUID().toString())
                .artista(partialArtist(row))
                .album(partialTitle(row))
                .genero(row.getGenre())
                .selloDiscografico(row.getLabel())
                .anio(row.getYear())
                .condicion(CondicionDisco.USADO)
                .tipoDisco(parseFormat(row.getFormat()))
                .estado(EstadoDisco.DISPONIBLE)
                .cantidadCopias(1)
                .pais(row.getCountry())
                .estilo(row.getStyle())
                .tracklist(row.getTracklist())
                .imagenUrl(row.getImageUrl())
                .previewUrl(null)
                .discogsUrl(row.getNormalizedDiscogsUrl())
                .procedencia("DISCOGS")
                .notas(row.getStatus() == DiscogsImportRowStatus.RATE_LIMITED
                        ? RATE_LIMIT_WARNING
                        : catalogNotes(row))
                .build();
        return disco;
    }

    private List<TrackInfo> parseTracks(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, TrackInfo.class)
            );
        } catch (Exception ex) {
            log.warn("No se pudieron leer links de audio Discogs: {}", ex.getMessage());
            return List.of();
        }
    }

    private void updateDisco(Disco disco, DiscogsImportRow row) {
        if (!blank(row.getArtist())) disco.setArtista(row.getArtist());
        if (!blank(row.getTitle())) disco.setAlbum(row.getTitle());
        if (!blank(row.getGenre())) disco.setGenero(row.getGenre());
        if (!blank(row.getLabel())) disco.setSelloDiscografico(row.getLabel());
        if (row.getYear() != null) disco.setAnio(row.getYear());
        if (!blank(row.getCountry())) disco.setPais(row.getCountry());
        if (!blank(row.getStyle())) disco.setEstilo(row.getStyle());
        if (!blank(row.getTracklist())) disco.setTracklist(row.getTracklist());
        if (!blank(row.getImageUrl())) disco.setImagenUrl(row.getImageUrl());
        disco.setPreviewUrl(null);
        if (row.getResolvedReleaseId() != null) {
            disco.setTipoDisco(parseFormat(row.getFormat()));
            disco.setNotas(catalogNotes(row));
        }
    }

    private String catalogNotes(DiscogsImportRow row) {
        return blank(row.getCatalogNumber())
                ? null
                : "Número de catálogo Discogs: " + row.getCatalogNumber();
    }

    private String partialTitle(DiscogsImportRow row) {
        return blank(row.getTitle())
                ? "Metadata pendiente (Discogs " + row.getDiscogsId() + ")"
                : row.getTitle();
    }

    private String partialArtist(DiscogsImportRow row) {
        return blank(row.getArtist()) ? "Discogs pendiente" : row.getArtist();
    }

    private String generateCode(DiscogsImportRow row) {
        String initials = Arrays.stream(partialArtist(row).split("\\s+"))
                .filter(value -> !value.isBlank())
                .map(value -> value.substring(0, 1).toUpperCase(Locale.ROOT))
                .reduce("", String::concat);
        return (initials.isBlank() ? "XX" : initials)
                + "-" + Optional.ofNullable(row.getYear()).orElse(0)
                + "-" + row.getResolvedReleaseId();
    }

    private TipoDisco parseFormat(String format) {
        try {
            return TipoDisco.valueOf(Optional.ofNullable(format).orElse("VINILO"));
        } catch (IllegalArgumentException ex) {
            return TipoDisco.VINILO;
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return blank(first) ? fallback : first;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private DiscogsImportJobDTO toDto(DiscogsImportJob job) {
        List<DiscogsImportRow> entityRows = job.getRows();
        List<DiscogsImportRowDTO> rows = job.getRows().stream().map(this::toRowDto).toList();
        return DiscogsImportJobDTO.builder()
                .id(job.getIdDiscogsImportJob())
                .nombreArchivo(job.getNombreArchivo())
                .nombreHoja(job.getNombreHoja())
                .status(job.getStatus().name().toLowerCase(Locale.ROOT))
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .totalRowsRead(rows.size())
                .validReleaseUrls(count(rows, row -> "release".equals(row.getDiscogsType())))
                .validMasterUrls(count(rows, row -> "master".equals(row.getDiscogsType())))
                .embeddedHyperlinkRows(count(rows, row -> row.getHyperlinkUrl() != null))
                .needsManualMatch(countStatus(rows, DiscogsImportRowStatus.NEEDS_MANUAL_MATCH))
                .ignored(countStatus(rows, DiscogsImportRowStatus.IGNORED))
                .failed(countStatus(rows, DiscogsImportRowStatus.FAILED))
                .rateLimited(count(rows, row ->
                        DiscogsImportRowStatus.RATE_LIMITED.name().equalsIgnoreCase(row.getStatus())
                                || (row.getErrorMessage() != null
                                && row.getErrorMessage().contains("importado parcialmente"))))
                .imported(countStatus(rows, DiscogsImportRowStatus.IMPORTED))
                .coversDownloaded(count(rows, row ->
                        row.getImageUrl() != null
                                && row.getImageUrl().contains("/discogs/covers/")))
                .mp3PreviewsFound(entityRows.stream()
                        .flatMap(row -> parseTracks(row.getTracksJson()).stream())
                        .mapToInt(track -> blank(track.mp3Url()) ? 0 : 1)
                        .sum())
                .youtubeLinksFound(entityRows.stream()
                        .flatMap(row -> parseTracks(row.getTracksJson()).stream())
                        .mapToInt(track -> blank(track.youtubeUrl()) ? 0 : 1)
                        .sum())
                .qrEntriesCreated(entityRows.stream()
                        .map(DiscogsImportRow::getImportedCatalogProduct)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toMap(
                                Disco::getIdDisco,
                                disco -> qrCopyService.listDtos(disco).size(),
                                Integer::max
                        ))
                        .values().stream().mapToInt(Integer::intValue).sum())
                .pending(count(rows, row -> Set.of(
                        "pending", "parsed", "fetching_discogs", "pending_retry"
                ).contains(row.getStatus()) && row.getResolvedReleaseId() == null))
                .rows(rows)
                .build();
    }

    private DiscogsImportRowDTO toRowDto(DiscogsImportRow row) {
        return DiscogsImportRowDTO.builder()
                .id(row.getIdDiscogsImportRow())
                .sourceExcelRowNumber(row.getSourceExcelRowNumber())
                .visibleCellValue(row.getVisibleCellValue())
                .hyperlinkUrl(row.getHyperlinkUrl())
                .normalizedDiscogsUrl(row.getNormalizedDiscogsUrl())
                .urlSource(row.getUrlSource())
                .discogsType(row.getDiscogsType())
                .discogsId(row.getDiscogsId())
                .masterId(row.getMasterId())
                .resolvedReleaseId(row.getResolvedReleaseId())
                .artist(row.getArtist())
                .title(row.getTitle())
                .year(row.getYear())
                .genre(row.getGenre())
                .label(row.getLabel())
                .catalogNumber(row.getCatalogNumber())
                .imageUrl(row.getImageUrl())
                .status(row.getStatus().name().toLowerCase(Locale.ROOT))
                .errorMessage(row.getErrorMessage())
                .retryCount(row.getRetryCount())
                .importedCatalogProductId(row.getImportedCatalogProduct() != null
                        ? row.getImportedCatalogProduct().getIdDisco()
                        : null)
                .build();
    }

    private int countStatus(List<DiscogsImportRowDTO> rows, DiscogsImportRowStatus status) {
        return count(rows, row -> status.name().equalsIgnoreCase(row.getStatus()));
    }

    private int count(List<DiscogsImportRowDTO> rows, java.util.function.Predicate<DiscogsImportRowDTO> test) {
        return (int) rows.stream().filter(test).count();
    }

    @PreDestroy
    void shutdown() {
        jobExecutor.shutdownNow();
    }
}
