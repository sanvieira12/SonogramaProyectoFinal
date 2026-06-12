package com.sonograma.service.importacion;

import com.sonograma.dto.DiscogsImportJobDTO;
import com.sonograma.dto.DiscogsImportRowDTO;
import com.sonograma.entity.Disco;
import com.sonograma.entity.DiscogsImportJob;
import com.sonograma.entity.DiscogsImportRow;
import com.sonograma.enums.*;
import com.sonograma.exception.NegocioException;
import com.sonograma.repository.DiscoRepository;
import com.sonograma.repository.DiscogsImportJobRepository;
import com.sonograma.repository.DiscogsImportRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscogsImportJobService {

    private static final int MAX_RETRIES = 3;
    private static final long MAX_RETRY_DELAY_MS = 30_000;

    private final DiscogsExcelParser excelParser;
    private final DiscogsApiClient apiClient;
    private final DiscogsImportJobRepository jobRepository;
    private final DiscogsImportRowRepository rowRepository;
    private final DiscoRepository discoRepository;
    private final PlatformTransactionManager transactionManager;
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
                            List.of(DiscogsImportRowStatus.PARSED)
                    );
            for (DiscogsImportRow row : rows) {
                if (row.getResolvedReleaseId() == null || blank(row.getArtist()) || blank(row.getTitle())) {
                    continue;
                }
                if (row.getImportedCatalogProduct() != null) {
                    row.setStatus(DiscogsImportRowStatus.IMPORTED);
                    continue;
                }
                Disco disco = discoRepository.findByDiscogsUrl(row.getNormalizedDiscogsUrl())
                        .orElseGet(() -> discoRepository.save(toDisco(row)));
                row.setImportedCatalogProduct(disco);
                row.setStatus(DiscogsImportRowStatus.IMPORTED);
                row.setErrorMessage(null);
                rowRepository.save(row);
            }
        });
        return getJob(jobId);
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
                for (Long rowId : rowIds) {
                    processRow(rowId);
                }
            }
            finalizeJob(jobId);
        } catch (Exception ex) {
            log.error("Falló importación Discogs {}: {}", jobId, ex.getMessage(), ex);
            updateJobStatus(jobId, DiscogsImportJobStatus.FAILED, ex.getMessage());
        }
    }

    private void processRowAndFinalize(Long jobId, Long rowId) {
        processRow(rowId);
        finalizeJob(jobId);
    }

    private void processRow(Long rowId) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            DiscogsImportRow source = updateRowStatus(rowId, DiscogsImportRowStatus.FETCHING_DISCOGS, null);
            DiscogsApiClient.FetchResult result = apiClient.fetch(source.getDiscogsType(), source.getDiscogsId());
            if (result.success()) {
                saveFetchResult(rowId, result);
                return;
            }
            if (!result.rateLimited()) {
                updateRowStatus(rowId, DiscogsImportRowStatus.FAILED, result.errorMessage());
                return;
            }

            int retryCount = incrementRetry(rowId, result.errorMessage());
            if (retryCount >= MAX_RETRIES) {
                updateRowStatus(rowId, DiscogsImportRowStatus.RATE_LIMITED, result.errorMessage());
                return;
            }
            updateRowStatus(rowId, DiscogsImportRowStatus.PENDING_RETRY,
                    result.errorMessage() + ". Reintento " + retryCount + "/" + MAX_RETRIES);
            sleep(retryDelay(result.retryAfterMs(), retryCount));
        }
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
        return count == null ? MAX_RETRIES : count;
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
            row.setCountry(result.country());
            row.setStyle(result.style());
            row.setFormat(result.format());
            row.setImageUrl(result.imageUrl());
            row.setPreviewUrl(result.previewUrl());
            row.setTracklist(result.tracklist());
            row.setStatus(DiscogsImportRowStatus.PARSED);
            row.setErrorMessage(null);
            rowRepository.save(row);
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
        return Disco.builder()
                .codigoInterno(generateCode(row))
                .codigoQr(UUID.randomUUID().toString())
                .artista(row.getArtist())
                .album(row.getTitle())
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
                .previewUrl(row.getPreviewUrl())
                .discogsUrl(row.getNormalizedDiscogsUrl())
                .procedencia("DISCOGS")
                .build();
    }

    private String generateCode(DiscogsImportRow row) {
        String initials = Arrays.stream(row.getArtist().split("\\s+"))
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

    private long retryDelay(long headerDelay, int retryCount) {
        long exponential = 1_000L * (1L << Math.max(0, retryCount - 1));
        return Math.min(MAX_RETRY_DELAY_MS, Math.max(headerDelay, exponential));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return blank(first) ? fallback : first;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private DiscogsImportJobDTO toDto(DiscogsImportJob job) {
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
                .rateLimited(countStatus(rows, DiscogsImportRowStatus.RATE_LIMITED))
                .imported(countStatus(rows, DiscogsImportRowStatus.IMPORTED))
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
